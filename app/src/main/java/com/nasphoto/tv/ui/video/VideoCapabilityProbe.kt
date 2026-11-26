/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.video

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.pixelSize

/**
 * ================= 设备解码能力预检 =================
 *
 * 背景：飞牛相册 API 只返回原片（实测给 `/p/api/v1/stream/v/{id}` 加任何
 * quality/resolution/transcode 参数都返回同一份原片），所以没有服务端转码或
 * 降级选流可用。既然改不了片源，就在**播放前**先问设备一句「你解得了这段吗」，
 * 解不了就别让用户对着一块黑屏猜原因。
 *
 * 判定用 Android 官方的 [MediaCodecList]：枚举所有视频解码器，看有没有支持
 * 该 MIME（H.265/H.264/AV1）且**分辨率够大**的。关键点是必须同时看：
 *
 *  - [MediaCodecInfo.CodecCapabilities.VIDEO_HEVC_10_BIT] 这类 profile：
 *    "设备支持 HEVC" 不等于 "支持 10bit HEVC"，4K 60 帧无人机原片几乎都是
 *    Main10 10bit，这正是最常见的失败原因；
 *  - 最大宽高：低配盒子只到 1080p，4K 原片直接超规格。
 *
 * 这类预检是**保守提示**，不会拦截播放：软解、厂商私有解码器、ExoPlayer 的
 * decoder fallback 都可能让「看起来解不了」的片子实际能播。所以真实结果仍以
 * ExoPlayer 的 [androidx.media3.common.PlaybackException] 为准；预检只用来在
 * 失败时给出**针对性的原因**，而不是武断地说「不能播」。
 */
object VideoCapabilityProbe {

    /** 一种编码在设备上的解码能力画像 */
    data class CodecProfile(
        val mime: String,
        /** 是否有任意解码器支持该 MIME */
        val supported: Boolean,
        /** 是否支持 10bit（HEVC Main10 / H264 High10 / AV1 10bit） */
        val supports10Bit: Boolean,
        /** 支持的最大面积（宽×高），0 表示未知 */
        val maxPixels: Long,
    )

    data class DeviceProfile(
        val hevc: CodecProfile,
        val avc: CodecProfile,
        val av1: CodecProfile,
        val vp9: CodecProfile,
    ) {
        fun forMime(mime: String?): CodecProfile? = when (mime) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> hevc
            MediaFormat.MIMETYPE_VIDEO_AVC -> avc
            MediaFormat.MIMETYPE_VIDEO_AV1 -> av1
            MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9
            else -> null
        }
    }

    /** 对单条视频的可播性结论 */
    data class Assessment(
        /** 是否可能解不了（仅提示，不拦截） */
        val risky: Boolean,
        /** 一段可直接展示给电视用户的话；可播时为 null */
        val reason: String?,
    )

    /**
     * 查询设备能力。必须在主线程之外的调用方使用（内部只是遍历静态列表，很快）。
     */
    fun query(context: Context): DeviceProfile {
        val codecs = try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isEncoder && it.supportedTypes.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }

        fun scan(mime: String): CodecProfile {
            var any = false
            var tenBit = false
            var maxPixels = 0L
            for (codec in codecs) {
                val caps = try {
                    codec.getCapabilitiesForType(mime) ?: continue
                } catch (_: IllegalArgumentException) {
                    continue
                } catch (_: Throwable) {
                    continue
                }
                any = true
                if (caps.supports10Bit(mime)) tenBit = true
                val vc = caps.videoCapabilities
                val area = try {
                    vc.supportedFrameRates?.let { range ->
                        val w = vc.supportedWidths.upper
                        val h = vc.supportedHeights.upper
                        w.toLong() * h.toLong()
                    } ?: 0L
                } catch (_: Throwable) {
                    0L
                }
                if (area > maxPixels) maxPixels = area
            }
            return CodecProfile(mime, any, tenBit, maxPixels)
        }

        return DeviceProfile(
            hevc = scan(MediaFormat.MIMETYPE_VIDEO_HEVC),
            avc = scan(MediaFormat.MIMETYPE_VIDEO_AVC),
            av1 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                scan(MediaFormat.MIMETYPE_VIDEO_AV1)
            } else {
                CodecProfile(MediaFormat.MIMETYPE_VIDEO_AV1, false, false, 0)
            },
            vp9 = scan(MediaFormat.MIMETYPE_VIDEO_VP9),
        )
    }

    private fun MediaCodecInfo.CodecCapabilities.supports10Bit(mime: String): Boolean =
        profileLevels.any { level ->
            when (level.profile) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.VP9Profile2,
                MediaCodecInfo.CodecProfileLevel.VP9Profile3,
                -> true
                // API 24 上部分常量不存在时，用数值兜底（HEVC Main10 = 2）
                else -> (level.profile == 2 && mime == MediaFormat.MIMETYPE_VIDEO_HEVC)
            }
        }

    /**
     * 判断一条视频在这台设备上是否"可能解不了"。
     *
     * 由于元数据里没有 codec/profile，这里做两级判断：
     *  1. 分辨率超过设备里**任何**解码器的最大面积 → 几乎必然失败；
     *  2. 分辨率达到 4K 且设备对 HEVC 的 10bit 支持缺失 → 高风险
     *     （手机/无人机 4K 60 帧原片绝大多数是 10bit HEVC）。
     *
     * 返回 [Assessment.risky] 仅供 UI 提示；[Assessment.reason] 是失败时展示的诊断。
     */
    fun assess(item: MediaItem, device: DeviceProfile): Assessment {
        val (w, h) = item.pixelSize() ?: return Assessment(false, null)
        val pixels = w.toLong() * h.toLong()

        val maxAny = maxOf(
            device.hevc.maxPixels,
            device.avc.maxPixels,
            device.av1.maxPixels,
            device.vp9.maxPixels,
        )

        // 1) 分辨率硬超：设备所有解码器的最大面积都小于这段视频
        if (maxAny > 0L && pixels > maxAny) {
            return Assessment(
                risky = true,
                reason = "这段视频是 ${w}×${h}，超过这台电视的硬件解码上限" +
                    "（最大约 ${formatMaxResolution(maxAny)}），播放会很卡顿或无法播放。",
            )
        }

        // 2) 4K 且设备 HEVC 不支持 10bit —— 高码率原片失败的最常见原因
        val is4K = pixels >= 3_400_000L // 3840×2160 ≈ 8.3M；1920×3840 竖屏 ≈ 7.4M
        if (is4K && !device.hevc.supports10Bit && device.hevc.supported) {
            return Assessment(
                risky = true,
                reason = "这段视频可能是 4K 10bit 高帧率原片，而这台电视的 HEVC 解码器" +
                    "不支持 10bit —— 这类素材通常会播放失败。",
            )
        }
        if (is4K && !device.hevc.supported && !device.avc.supported) {
            return Assessment(
                risky = true,
                reason = "这段视频是 4K 规格，但这台电视没有可用的高清视频解码器。",
            )
        }

        return Assessment(false, null)
    }

    /**
     * 把失败诊断写成一句电视上能看懂的话。
     * [errorCode] 用 ExoPlayer PlaybackException.errorCode 的数值，避免这里依赖 media3 类型。
     */
    fun describeFailure(errorCode: Int, fallbackMessage: String, assessment: Assessment): String {
        // 解码类错误：优先用预检给出的针对性诊断
        val isDecodeError = errorCode in DECODE_ERROR_CODES
        if (isDecodeError && assessment.reason != null) return assessment.reason
        return fallbackMessage
    }

    private val DECODE_ERROR_CODES = setOf(
        2001, // ERROR_CODE_DECODER_INIT_FAILED
        2002, // ERROR_CODE_DECODING_FAILED
        2004, // ERROR_CODE_DECODER_QUERY_FAILED
        2008, // ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        2010, // ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
    )

    /** 把最大面积近似成「宽×高」文案，用于给用户一个量级概念 */
    internal fun formatMaxResolution(maxPixels: Long): String {
        if (maxPixels <= 0L) return "未知"
        val ratio = 16f / 9f
        val h = kotlin.math.sqrt(maxPixels / ratio).toLong()
        val w = (h * ratio).toLong()
        return "${w}×${h}"
    }
}

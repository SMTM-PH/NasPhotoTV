package com.nasphoto.tv.ui.video

/**
 * ================= 视频缓冲预算（按素材码率自适应） =================
 *
 * 为什么不能再用一套写死的缓冲参数：
 *
 * 电视的 Java 堆很小（本机实测上限 192 MB），ExoPlayer 的缓冲是 `byte[]`，
 * 所以「前向缓冲多少秒」和「能拿多少内存」是同一件事的两面。旧实现把
 * 目标字节数写死成 48 MB、最短缓冲 8 秒：
 *
 *  - 20 Mbps 的 1080p：8 秒只要 20 MB，48 MB 够放 19 秒 —— 完全够用；
 *  - 130 Mbps 的 4K 60fps：8 秒要 130 MB，远超 48 MB 上限。
 *    `setPrioritizeTimeOverSizeThresholds(false)` 下字节数一到顶就停止加载，
 *    于是每次只缓冲到约 3 秒就开播，播完 3 秒立刻重新缓冲 ——
 *    表现就是「4K 60 帧每隔几秒卡一下」，正是竖拍 4K60 素材（码率通常
 *    120–150 Mbps，比横拍更高）反馈最集中的现象。
 *
 * 这里改成按码率算预算：
 *  1. 先按「8 秒 × 码率 × 1.25」估算想要多少字节，并夹在 [下限, 上限] 之间；
 *  2. 如果上限装不下 8 秒，就把时长目标一起缩短 —— 关键是让
 *     `maxBufferMs` 落在字节上限**之内**，缓冲永远不撞墙，也就不会出现
 *     「一直想加载但分配不出来」的抖动；
 *  3. 最短缓冲取可达时长的 60%，让稳定播放时的缓冲水位有来回的余量。
 *
 * 纯函数、无 Android 依赖，便于单元测试（见 `PlaybackBufferPlanTest`）。
 */
data class PlaybackBufferPlan(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
)

object PlaybackBufferPlanner {

    /** 字节下限：低码率素材也给足时长，避免网络抖动直接卡住。 */
    private const val MIN_TARGET_BYTES = 32 * 1024 * 1024

    /** 字节上限：留出解码器、位图与 Compose 的余量，别把 192 MB 堆打满。 */
    private const val MAX_TARGET_BYTES = 64 * 1024 * 1024

    /** 期望的最短 / 最长前向缓冲时长。 */
    private const val DESIRED_MIN_MS = 8_000
    private const val DESIRED_MAX_MS = 20_000

    /** 估算余量：码率是平均值，关键帧附近会短时飙高。 */
    private const val HEADROOM = 1.25

    /** 时长硬下限：再短就失去抗抖动意义。 */
    private const val FLOOR_MIN_MS = 1_500

    /** 码率未知时的兜底（与旧行为一致，保证回归面最小）。 */
    val FALLBACK = PlaybackBufferPlan(
        minBufferMs = 8_000,
        maxBufferMs = 20_000,
        bufferForPlaybackMs = 1_500,
        bufferForPlaybackAfterRebufferMs = 4_000,
        targetBufferBytes = 48 * 1024 * 1024,
    )

    /**
     * @param bitrateBps 素材平均码率（bit/s）。<=0 或异常值时返回 [FALLBACK]。
     */
    fun plan(bitrateBps: Long): PlaybackBufferPlan {
        if (bitrateBps <= 0L) return FALLBACK
        val bytesPerSecond = bitrateBps / 8.0
        if (bytesPerSecond <= 0.0) return FALLBACK

        // 1) 想要多少字节才能装下 8 秒，再夹到 [下限, 上限]
        val wanted = bytesPerSecond * (DESIRED_MIN_MS / 1000.0) * HEADROOM
        val targetBytes = wanted.toLong()
            .coerceIn(MIN_TARGET_BYTES.toLong(), MAX_TARGET_BYTES.toLong())
            .toInt()

        // 2) 这份预算实际能装多少秒
        val achievableMs = (targetBytes / bytesPerSecond * 1000.0).toLong()

        // 3) 时长目标收敛到可达范围内，保证 max 永远撞不到字节上限
        val minMs = minOf(DESIRED_MIN_MS.toLong(), (achievableMs * 0.6).toLong())
            .coerceAtLeast(FLOOR_MIN_MS.toLong())
        val maxMs = minOf(DESIRED_MAX_MS.toLong(), achievableMs)
            .coerceAtLeast(minMs + 1_000L)

        // 4) 起播阈值必须小于最短缓冲，否则会「攒满才播」，起播变慢
        val playbackMs = minOf(1_500L, minMs / 2).coerceAtLeast(300L)
        val afterRebufferMs = minOf(4_000L, minMs).coerceAtLeast(500L)

        return PlaybackBufferPlan(
            minBufferMs = minMs.toInt(),
            maxBufferMs = maxMs.toInt(),
            bufferForPlaybackMs = playbackMs.toInt(),
            bufferForPlaybackAfterRebufferMs = afterRebufferMs.toInt(),
            targetBufferBytes = targetBytes,
        )
    }

    /**
     * 从媒体条目估算平均码率（bit/s）。
     * 两者缺失或为 0 时返回 0，交给 [plan] 走兜底。
     */
    fun estimateBitrateBps(sizeBytes: Long, durationMs: Long): Long {
        if (sizeBytes <= 0L || durationMs <= 0L) return 0L
        return sizeBytes * 8_000L / durationMs
    }
}

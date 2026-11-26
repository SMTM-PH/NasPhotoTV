/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.RAW_EXTENSIONS
import org.json.JSONObject

/**
 * ================= 飞牛媒体地址解析（1.2.9） =================
 *
 * 飞牛官方 Web 前端（DMsxwg1v8l6DUDoj2.js）对相册/搜索/时间线条目用
 * `additional.thumbnail.*Url`；但**文件夹视图** `folder_view/getFileList` 的条目经常
 * 不带这组字段，前端会退回到按 `{id, photoUUID, size}` 拼 stream 地址：
 *
 * ```
 * thumbnailUrlPrefix + "p/t/${id}/${size}/${photoUUID}"   // 图片
 * thumbnailUrlPrefix + "v/${id}"                          // 视频
 * ```
 *
 * 1.2.8 的 Android 端只认前一种形态，于是目录里的照片卡片一片空白、点开大图也是空
 * （用户反馈「子文件夹都显示出来，但照片缩略图不能显示，大照片也不能显示」）。
 * 这里把地址解析收敛成纯函数，覆盖三种返回形态并在缺字段时按官方算法兜底。
 */

/** 官方前端的图片流地址；[size] 取 xxs/xs/s/m/o，o = 原图。 */
internal fun photoStreamUrl(id: Long, size: String, uuid: String): String =
    if (id <= 0L || uuid.isBlank()) "" else "/p/api/v1/stream/p/t/$id/$size/$uuid"

/** 官方前端的视频流地址（也用于实况照片的动态片段）。 */
internal fun videoStreamUrl(id: Long): String =
    if (id <= 0L) "" else "/p/api/v1/stream/v/$id"

/** 相对地址挂到 baseUrl 下（baseUrl 可能带反代子路径）。 */
internal fun absoluteUrl(relative: String, baseUrl: String): String {
    if (relative.isBlank()) return ""
    if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
    return baseUrl.trimEnd('/') + "/" + relative.trimStart('/')
}

/** 多字段命名兼容：返回第一个非空的字符串。 */
internal fun firstNonBlank(json: JSONObject, vararg names: String): String {
    for (name in names) {
        val value = json.optString(name)
        if (value.isNotBlank() && value != "null") return value
    }
    return ""
}

internal data class MediaUrls(
    val thumbnailUrl: String,
    val fullUrl: String,
    /** 实况照片的动态片段；普通照片/视频为空或视频流地址 */
    val liveUrl: String,
)

/**
 * 从一条飞牛媒体记录解析缩略图 / 大图 / 实况片段地址。
 *
 * 兼容形态：
 *  1. `additional.thumbnail.{mUrl,sUrl,xsUrl,xxsUrl,originalUrl,videoUrl}`（相册/时间线/搜索）
 *  2. 顶层 `thumbnail` / `thumb` 对象里同名字段（部分版本）
 *  3. 顶层直接给 `mUrl` / `sUrl` / `originalUrl` 等
 *  4. 以上都没有：用 `id` + `photoUUID`（或 uuid/session）拼 stream 地址（文件夹视图）
 */
internal fun resolveMediaUrls(json: JSONObject, id: Long, baseUrl: String): MediaUrls {
    val nested = json.optJSONObject("additional")?.optJSONObject("thumbnail")
    val direct = json.optJSONObject("thumbnail") ?: json.optJSONObject("thumb")
    val sources = listOfNotNull(nested, direct, json)

    fun field(vararg names: String): String {
        for (name in names) {
            for (source in sources) {
                val value = source.optString(name)
                if (value.isNotBlank() && value != "null") return value
            }
        }
        return ""
    }

    val uuid = firstNonBlank(json, "photoUUID", "uuid", "session")
    val mUrl = field("mUrl", "m")
    val sUrl = field("sUrl", "s")
    val xsUrl = field("xsUrl", "xs")
    val xxsUrl = field("xxsUrl", "xxs")
    val originalUrl = field("originalUrl", "o", "originUrl")
    val videoUrl = field("videoUrl", "video")

    val isVideo = json.optString("category").equals("video", ignoreCase = true) ||
        json.optString("fileType").equals("video", ignoreCase = true)
    val fileName = firstNonBlank(json, "fileName", "name")
    val extension = fileName.substringAfterLast('.', "").uppercase()
    val isRaw = extension in RAW_EXTENSIONS

    // 缩略图：优先服务端给的中尺寸；没有就用前端同款 stream/m
    val thumbRaw = mUrl
        .ifBlank { sUrl }
        .ifBlank { xsUrl }
        .ifBlank { xxsUrl }
        .ifBlank { photoStreamUrl(id, "s", uuid) }

    // 大图：视频走视频流；RAW 用 NAS 生成的高清 JPG 预览；普通照片用原图
    val fullRaw = when {
        isVideo -> videoUrl.ifBlank { videoStreamUrl(id) }
        isRaw -> mUrl.ifBlank { sUrl }
            .ifBlank { originalUrl }
            .ifBlank { photoStreamUrl(id, "m", uuid) }
        else -> originalUrl
            .ifBlank { mUrl }
            .ifBlank { photoStreamUrl(id, "o", uuid) }
    }

    val thumbnailUrl = absoluteUrl(thumbRaw.ifBlank { fullRaw }, baseUrl)
    val fullUrl = absoluteUrl(fullRaw.ifBlank { thumbRaw }, baseUrl)
    val isLive = json.optInt("isLive", 0) == 1
    val live = if (isLive) {
        absoluteUrl(videoUrl.ifBlank { videoStreamUrl(id) }, baseUrl)
    } else {
        ""
    }
    return MediaUrls(thumbnailUrl = thumbnailUrl, fullUrl = fullUrl, liveUrl = live)
}

/** 文件夹封面：从 folder_view/cover 的 {cover:[{id,uuid}]} 里取第一张拼 xs 图。 */
internal fun posterUrl(poster: JSONObject, baseUrl: String): String {
    val idRaw = firstNonBlank(poster, "id", "photoId")
    val id = idRaw.toLongOrNull() ?: 0L
    val uuid = firstNonBlank(poster, "uuid", "photoUUID", "session")
    return absoluteUrl(photoStreamUrl(id, "xs", uuid), baseUrl)
}

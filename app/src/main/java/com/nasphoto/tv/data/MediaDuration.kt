/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import org.json.JSONObject

/**
 * ================= 飞牛视频时长解析 =================
 *
 * 真实故障：`gallery` 响应里视频时长字段叫 **`mediaDuration`，单位是秒**
 * （实测 `IMG_0406.MOV` → `mediaDuration: 11`，文件确实是 11 秒）。
 * 早期实现读的是 `durationMs` / `duration`，两个字段都不存在，于是
 * **全库视频时长恒为 0**，连带三个下游一起坏掉：
 *
 *  1. 卡片上的时长角标不显示；
 *  2. 按码率规划缓冲时算不出码率（`size / duration`），只能走兜底预算；
 *  3. 信息面板/进度条拿不到可靠时长。
 *
 * 这里集中一处解析，并做单位归一：只有字段名明确带 `Ms` 的才当毫秒，
 * 其余按秒处理（用 24 小时阈值兜住极老的毫秒口径）。
 */
internal object MediaDuration {

    /** 24 小时：超过它只可能是毫秒口径，用来区分 seconds / millis 两种返回。 */
    private const val MAX_PLAUSIBLE_SECONDS = 86_400L

    /**
     * 解析成毫秒。字段缺失或非法时返回 0（不是猜测值），调用方按「未知」处理。
     */
    fun fromJson(json: JSONObject): Long {
        json.optLong("durationMs", 0L).takeIf { it > 0L }?.let { return it }
        val raw = json.optLong("mediaDuration", 0L).takeIf { it > 0L }
            ?: json.optLong("duration", 0L).takeIf { it > 0L }
            ?: return 0L
        return if (raw > MAX_PLAUSIBLE_SECONDS) raw else raw * 1000L
    }
}

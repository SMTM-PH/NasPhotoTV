/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import org.json.JSONObject

/** 只接受 > 0 的 total：0 / 负数 / 非数字都当成「服务端没给」。 */
internal fun positiveTotal(data: JSONObject): Int {
    val raw = data.opt("total") ?: return -1
    val value = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: -1
        else -> -1
    }
    return if (value > 0) value else -1
}

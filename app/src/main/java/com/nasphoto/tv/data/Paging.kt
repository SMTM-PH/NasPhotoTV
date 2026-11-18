package com.nasphoto.tv.data

import org.json.JSONObject

/**
 * ================= 飞牛分页的公共判定 =================
 *
 * 服务端有几个坑，单独看任何一个信号都会误判「已经到末尾」：
 *
 * 1. 单页实际条数可能因 NAS 版本/版本配置而小于我们请求的 pageSize（例如请求 500 只回 200），
 *    但后面还有数据 —— 只看 `page.size < pageSize` 会停在最近 9 年；
 * 2. `hasNext` 不一定是 JSON 布尔：实测会回 `true` / `"true"` / `1` / `"1"`，甚至缺失；
 * 3. 部分接口忽略 `offset` 反复回第一页 —— 只看 `hasNext` 会一直重复同一批数据。
 *
 * 因此「是否继续翻页」采用 OR 语义：三个信号（hasNext / 满页 / total 未取满）
 * 任意一个说「还有」，就继续请求；只有明确指出结束（空页 / 没有新增 / 达到 total 或硬上限）才停。
 * 重复页由调用方按 id 去重后以「本页没有新增」终止，不会死循环。
 */

/** 服务端布尔字段兼容 true / "true" / 1 / "1"（与 FnOsApi 内部的 flag 一致）。 */
internal fun JSONObject.truthy(name: String): Boolean {
    val raw = opt(name) ?: return false
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true) || raw.trim() == "1"
        else -> false
    }
}

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

/**
 * 是否继续下一页。
 *
 * @param parsedPageSize 本页成功解析出的媒体条数
 * @param rawPageSize 服务端原始数组长度（解析失败的条目也算，用于判断「满页」）
 * @param pageSize 请求的每页条数
 * @param collected 目前累计（去重后）条数
 * @param newlyAdded 本页新增的、之前没见过的条数（0 表示服务端在重复返回同一页）
 * @param hardLimit 兜底上限，防止接口异常时无限拉取
 */
internal fun shouldContinuePaging(
    data: JSONObject,
    parsedPageSize: Int,
    rawPageSize: Int,
    pageSize: Int,
    collected: Int,
    newlyAdded: Int,
    hardLimit: Int,
): Boolean {
    if (parsedPageSize == 0) return false
    if (newlyAdded <= 0) return false
    if (collected >= hardLimit) return false
    val total = positiveTotal(data)
    if (total > 0 && collected >= total) return false
    val hasNext = data.truthy("hasNext")
    return hasNext || rawPageSize >= pageSize || (total > 0 && collected < total)
}

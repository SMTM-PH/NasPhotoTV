package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.MediaItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/**
 * fnOS 图库：timeline 提供完整日期及 itemCount，getList 提供日期范围内的媒体。
 *
 * 全库按年分段读取，避免最新照片占满一次全库查询后旧年份被截断。某些版本不能
 * 对跨日范围正确翻页时，退回官方 Web 端使用的单日范围，并核对时间线的数量。
 * 不以短页、hasNext 或 getList.total 推断全库完整性，也不静默返回重复页前的半份库。
 */
internal class FnOsGallery(
    private val getJson: suspend (String, List<Pair<String, String>>) -> JSONObject,
    private val parseMedia: (JSONObject) -> MediaItem?,
) {
    suspend fun getRecent(limit: Int): List<MediaItem> {
        if (limit <= 0) return emptyList()
        return if (limit == Int.MAX_VALUE) loadLibrary() else {
            loadRange(
                start = LocalDate.of(1970, 1, 1),
                end = LocalDate.of(2099, 12, 31),
                requestedLimit = limit,
            )
        }
    }

    private suspend fun loadLibrary(): List<MediaItem> {
        val timeline = getJson(TIMELINE, emptyList())
        val list = timeline.optJSONArray("list")
            ?: throw IllegalStateException("飞牛图库时间线格式异常（缺少日期列表）")
        val days = sortedMapOf<LocalDate, Long>(compareByDescending { it })
        for (i in 0 until list.length()) {
            coroutineContext.ensureActive()
            val entry = list.optJSONObject(i)
                ?: throw IllegalStateException("飞牛图库时间线格式异常（日期无效）")
            val date = runCatching {
                LocalDate.of(entry.getInt("year"), entry.getInt("month"), entry.getInt("day"))
            }.getOrElse { throw IllegalStateException("飞牛图库时间线格式异常（日期无效）", it) }
            val count = entry.optLong("itemCount", -1)
            if (count < 0) throw IllegalStateException("飞牛图库时间线格式异常（缺少照片数量）")
            if (count > 0) days[date] = maxOf(days[date] ?: 0L, count)
        }

        val library = LinkedHashMap<String, MediaItem>()
        for ((year, dates) in days.entries.groupBy { it.key.year }) {
            coroutineContext.ensureActive()
            val expected = dates.sumOf { it.value }
            val media = try {
                loadRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), expectedCount = expected)
            } catch (_: IncompleteRange) {
                // 单日范围是 fnOS Web 图库实际使用的请求协议。跨日查询不完整时逐日补齐，
                // 每天独立翻页，不能因为某一年/某一页只有重复项就省略更早的年份。
                val byDay = LinkedHashMap<String, MediaItem>()
                for ((date, count) in dates) {
                    try {
                        for (item in loadRange(date, date, expectedCount = count)) {
                            byDay.putIfAbsent(item.id, item)
                        }
                    } catch (incomplete: IncompleteRange) {
                        throw IllegalStateException("图库未读取完整（$date），请刷新后重试", incomplete)
                    }
                }
                byDay.values.toList()
            }
            for (item in media) library.putIfAbsent(item.id, item)
        }
        val expected = days.values.sum()
        if (library.size.toLong() < expected) {
            throw IllegalStateException("图库未读取完整（时间线 $expected 项，已读取 ${library.size} 项），请刷新后重试")
        }
        return library.values.sortedByDescending { it.dateTaken }
    }

    private suspend fun loadRange(
        start: LocalDate,
        end: LocalDate,
        expectedCount: Long? = null,
        requestedLimit: Int = Int.MAX_VALUE,
    ): List<MediaItem> {
        val collected = LinkedHashMap<String, MediaItem>()
        var offset = 0L
        while (collected.size < requestedLimit) {
            coroutineContext.ensureActive()
            val data = getJson(
                LIST,
                listOf(
                    "start_time" to "${start.format(API_DATE)} 00:00:00",
                    "end_time" to "${end.format(API_DATE)} 23:59:59",
                    "limit" to minOf(PAGE_SIZE, requestedLimit).toString(),
                    "offset" to offset.toString(),
                    "mode" to "index",
                ),
            )
            val page = data.optJSONArray("list")
                ?: throw IllegalStateException("飞牛图库返回格式异常（缺少媒体列表）")
            if (page.length() == 0) {
                if (expectedCount != null && collected.size.toLong() < expectedCount) throw IncompleteRange()
                break
            }
            val before = collected.size
            for (i in 0 until page.length()) {
                coroutineContext.ensureActive()
                val json = page.optJSONObject(i) ?: continue
                val item = parseMedia(json) ?: continue
                if (expectedCount != null && (item.dateTaken < start || item.dateTaken > end)) {
                    throw IncompleteRange()
                }
                collected.putIfAbsent(item.id, item)
            }
            // 原始条目占用服务端位置，解析失败或边界重复都不能让 offset 回退。
            offset += page.length()
            if (collected.size >= requestedLimit || expectedCount != null && collected.size.toLong() >= expectedCount) break
            if (collected.size == before) {
                if (expectedCount != null) throw IncompleteRange()
                throw IllegalStateException("飞牛图库未正确翻页，请刷新后重试")
            }
            // 服务端可能把 limit=500 限成更小的页，而且不提供 hasNext/total。
            // 非空且有新增就继续，直到读满时间线数量/调用方数量，或收到空页。
        }
        return collected.values.take(requestedLimit)
    }

    private class IncompleteRange : Exception()

    private companion object {
        const val TIMELINE = "/p/api/v1/gallery/timeline"
        const val LIST = "/p/api/v1/gallery/getList"
        const val PAGE_SIZE = 500
        val API_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd")
    }
}

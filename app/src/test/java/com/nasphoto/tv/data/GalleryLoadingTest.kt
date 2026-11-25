package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLoadingTest {
    @Test
    fun startupPreviewReadsExactlyOnePageEvenWhenItContainsFewerThanRequested() = runBlocking<Unit> {
        val offsets = mutableListOf<String>()
        val gallery = gallery { path, params ->
            assertEquals(LIST, path)
            offsets += params.getValue("offset")
            page(listOf(item(1, "2026-01-01")))
        }

        assertEquals(listOf("1"), gallery.getRecentPreview(48).map { it.id })
        assertEquals(listOf("0"), offsets)
    }

    @Test
    fun loadsEveryYearBackTo2000DespiteShortPagesAndAbsentPagingMetadata() = runBlocking<Unit> {
        val requestedYears = mutableListOf<Int>()
        val offsets = mutableListOf<Int>()
        val gallery = gallery { path, params ->
            if (path == TIMELINE) {
                timeline((2000..2026).map { LocalDate.of(it, 6, 1) to 3L })
            } else {
                val year = params.getValue("start_time").take(4).toInt()
                val offset = params.getValue("offset").toInt()
                requestedYears += year
                offsets += offset
                // 实际页上限 2，响应没有 total/hasNext；请求的 limit 仍是 500。
                page((offset until minOf(offset + 2, 3)).map { item(year * 10L + it, "$year-06-01") })
            }
        }
        val media = gallery.getRecent(Int.MAX_VALUE)
        assertEquals(81, media.size)
        assertEquals((2026 downTo 2000).toList(), media.map { it.dateTaken.year }.distinct())
        assertEquals((2026 downTo 2000).flatMap { listOf(it, it) }, requestedYears)
        assertEquals(List(27) { listOf(0, 2) }.flatten(), offsets)
    }

    @Test
    fun fullLibraryDoesNotSilentlyStopAtTwentyOrFiftyThousandItems() = runBlocking<Unit> {
        val count = 50_001
        var calls = 0
        val gallery = gallery { path, params ->
            if (path == TIMELINE) timeline(listOf(LocalDate.of(2000, 1, 1) to count.toLong())) else {
                calls++
                val offset = params.getValue("offset").toInt()
                // total 不可信，完整性来自 timeline，不把单页 total=500 当作全库终点。
                page((offset until minOf(offset + 500, count)).map { item(it + 1L, "2000-01-01") })
                    .put("total", 500).put("hasNext", false)
            }
        }
        val media = gallery.getRecent(Int.MAX_VALUE)
        assertEquals(count, media.size)
        assertEquals("50001", media.last().id)
        assertEquals(101, calls)
    }

    @Test
    fun ignoredYearOffsetFallsBackToDailyRangesWithoutLosingOlderYears() = runBlocking<Unit> {
        val calls = mutableListOf<Pair<String, String>>()
        val dates = listOf(LocalDate.of(2026, 1, 1) to 1L, LocalDate.of(2026, 1, 2) to 1L, LocalDate.of(2000, 1, 1) to 1L)
        val gallery = gallery { path, params ->
            if (path == TIMELINE) timeline(dates) else {
                val start = params.getValue("start_time")
                val end = params.getValue("end_time")
                calls += start to params.getValue("offset")
                when {
                    start.startsWith("2000") -> page(listOf(item(3, "2000-01-01")))
                    start.take(10) != end.take(10) -> page(listOf(item(1, "2026-01-02")))
                    start.startsWith("2026:01:02") -> page(listOf(item(1, "2026-01-02")))
                    else -> page(listOf(item(2, "2026-01-01")))
                }
            }
        }
        assertEquals(listOf("1", "2", "3"), gallery.getRecent(Int.MAX_VALUE).map { it.id })
        assertEquals(
            listOf(
                "2026:01:01 00:00:00" to "0", "2026:01:01 00:00:00" to "1",
                "2026:01:02 00:00:00" to "0", "2026:01:01 00:00:00" to "0",
                "2000:01:01 00:00:00" to "0",
            ),
            calls,
        )
    }

    @Test
    fun ignoredYearDateRangeCannotPassCountValidationWithPhotosFromAnotherYear() = runBlocking<Unit> {
        val gallery = gallery { path, params ->
            if (path == TIMELINE) timeline(listOf(LocalDate.of(2000, 6, 1) to 1L)) else {
                if (params.getValue("start_time").startsWith("2000:06:01")) {
                    page(listOf(item(2, "2000-06-01")))
                } else page(listOf(item(1, "2026-01-01")))
            }
        }
        assertEquals(listOf("2"), gallery.getRecent(Int.MAX_VALUE).map { it.id })
    }

    @Test
    fun incompleteDayIsAnErrorInsteadOfAFalseCompleteLibrary() = runBlocking<Unit> {
        var calls = 0
        val gallery = gallery { path, _ ->
            if (path == TIMELINE) timeline(listOf(LocalDate.of(2017, 1, 1) to 2L)) else {
                calls++
                page(listOf(item(1, "2017-01-01")))
            }
        }
        val failure = expectFailure<IllegalStateException> { gallery.getRecent(Int.MAX_VALUE) }
        assertTrue(failure.message.orEmpty().contains("2017-01-01"))
        assertEquals(4, calls)
    }

    @Test
    fun earlyEmptyYearPageFallsBackToDaysAndStillChecksTheCount() = runBlocking<Unit> {
        var calls = 0
        val gallery = gallery { path, params ->
            if (path == TIMELINE) timeline(listOf(LocalDate.of(2017, 6, 1) to 1L)) else {
                calls++
                if (params.getValue("start_time").startsWith("2017:06:01")) {
                    page(listOf(item(1, "2017-06-01")))
                } else page(emptyList())
            }
        }
        assertEquals(listOf("1"), gallery.getRecent(Int.MAX_VALUE).map { it.id })
        assertEquals(2, calls)
    }

    @Test
    fun finiteRecentRequestContinuesPastShortPagesAndAdvancesByRawLength() = runBlocking<Unit> {
        val offsets = mutableListOf<String>()
        val gallery = gallery { path, params ->
            assertEquals(LIST, path)
            offsets += params.getValue("offset")
            when (params.getValue("offset")) {
                "0" -> page(listOf(JSONObject(), item(1, "2026-01-01")))
                "2" -> page(listOf(item(1, "2026-01-01"), item(2, "2000-01-01")))
                else -> page(emptyList())
            }
        }
        assertEquals(listOf("1", "2"), gallery.getRecent(3).map { it.id })
        assertEquals(listOf("0", "2", "4"), offsets)
    }

    @Test
    fun finiteRecentLimitDoesNotRequestTimelineOrAdditionalPages() = runBlocking<Unit> {
        var calls = 0
        val gallery = gallery { path, params ->
            calls++
            assertEquals(LIST, path)
            assertEquals("index", params["mode"])
            assertEquals("1", params["limit"])
            page(listOf(item(1, "2026-01-01"), item(2, "2025-01-01")))
        }
        assertEquals(listOf("1"), gallery.getRecent(1).map { it.id })
        assertEquals(1, calls)
    }

    @Test
    fun emptyTimelineIsAValidEmptyLibrary() = runBlocking<Unit> {
        var calls = 0
        val gallery = gallery { path, _ ->
            calls++
            assertEquals(TIMELINE, path)
            timeline(emptyList())
        }
        assertTrue(gallery.getRecent(Int.MAX_VALUE).isEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun missingListsOrInvalidTimelineEntriesAreErrors() = runBlocking<Unit> {
        for (response in listOf("{}", """{"list":[null]}""", """{"list":[{"year":2000,"month":1,"day":1}]}""")) {
            expectFailure<IllegalStateException> {
                gallery { _, _ -> JSONObject(response) }.getRecent(Int.MAX_VALUE)
            }
        }
        expectFailure<IllegalStateException> { gallery { _, _ -> JSONObject() }.getRecent(20) }
    }

    @Test
    fun requestErrorsAndCancellationPropagateWithoutReturningPartialData() = runBlocking<Unit> {
        for (failure in listOf(IOException("NAS unavailable"), CancellationException("Account changed"))) {
            var calls = 0
            val gallery = gallery { path, _ ->
                calls++
                if (path == TIMELINE) timeline(listOf(LocalDate.of(2000, 1, 1) to 2L)) else {
                    if (calls == 2) page(listOf(item(1, "2000-01-01"))) else throw failure
                }
            }
            assertSame(failure, expectFailure<Exception> { gallery.getRecent(Int.MAX_VALUE) })
            assertEquals(3, calls)
        }
    }

    private fun gallery(get: suspend (String, Map<String, String>) -> JSONObject) = FnOsGallery(
        getJson = { path, params -> get(path, params.toMap()) },
        parseMedia = { json ->
            json.optLong("id").takeIf { it > 0 }?.let { id ->
                MediaItem(id = id.toString(), title = "$id", type = MediaType.PHOTO, url = "", thumbnailUrl = "", dateTaken = LocalDate.parse(json.getString("dateTime")))
            }
        },
    )

    private fun timeline(days: List<Pair<LocalDate, Long>>) = JSONObject().put("list", JSONArray().also { list ->
        for ((date, count) in days) list.put(JSONObject().put("year", date.year).put("month", date.monthValue).put("day", date.dayOfMonth).put("itemCount", count))
    })

    private fun item(id: Long, date: String) = JSONObject().put("id", id).put("dateTime", date)
    private fun page(items: List<JSONObject>) = JSONObject().put("list", JSONArray(items))

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
        try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
            return error
        }
    }

    private companion object {
        const val TIMELINE = "/p/api/v1/gallery/timeline"
        const val LIST = "/p/api/v1/gallery/getList"
    }
}

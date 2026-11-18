package com.nasphoto.tv.data

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图库只显示最近 9 年的回归测试：服务端「还有下一页」的信号必须按 OR 语义判断。
 * 旧实现要求 hasNext 与「本页满 500」同时成立，任何一种服务端变体都会让翻页在第一页停下。
 */
class PagingTest {
    private fun continueFor(
        json: String,
        parsedPageSize: Int = 200,
        rawPageSize: Int = 200,
        pageSize: Int = 500,
        collected: Int = 200,
        newlyAdded: Int = 200,
        hardLimit: Int = 20_000,
    ): Boolean = shouldContinuePaging(
        data = JSONObject(json),
        parsedPageSize = parsedPageSize,
        rawPageSize = rawPageSize,
        pageSize = pageSize,
        collected = collected,
        newlyAdded = newlyAdded,
        hardLimit = hardLimit,
    )

    @Test
    fun shortPageWithHasNextTrueStillContinues() {
        // 用户 NAS 的实际形态：每页只回 200 条，但 hasNext=true。旧代码在这里误停。
        assertTrue(continueFor("""{"hasNext":true}"""))
    }

    @Test
    fun hasNextAsOneOrStringOneStillContinues() {
        assertTrue(continueFor("""{"hasNext":1}"""))
        assertTrue(continueFor("""{"hasNext":"1"}"""))
        assertTrue(continueFor("""{"hasNext":"true"}"""))
    }

    @Test
    fun fullPageWithoutHasNextStillContinues() {
        assertTrue(continueFor("""{}""", parsedPageSize = 500, rawPageSize = 500, collected = 500))
    }

    @Test
    fun totalNotReachedStillContinuesEvenWithShortPageAndFalseHasNext() {
        assertTrue(
            continueFor(
                """{"hasNext":false,"total":5294}""",
                parsedPageSize = 200,
                rawPageSize = 200,
                collected = 200,
            ),
        )
    }

    @Test
    fun reachedTotalStops() {
        assertFalse(
            continueFor(
                """{"hasNext":true,"total":5294}""",
                collected = 5294,
                newlyAdded = 200,
            ),
        )
    }

    @Test
    fun emptyPageStops() {
        assertFalse(continueFor("""{"hasNext":true}""", parsedPageSize = 0, rawPageSize = 0, newlyAdded = 0))
    }

    @Test
    fun repeatedPageStopsToAvoidInfiniteLoop() {
        assertFalse(continueFor("""{"hasNext":true}""", newlyAdded = 0))
    }

    @Test
    fun hardLimitStops() {
        assertFalse(continueFor("""{"hasNext":true}""", collected = 20_000))
    }
}

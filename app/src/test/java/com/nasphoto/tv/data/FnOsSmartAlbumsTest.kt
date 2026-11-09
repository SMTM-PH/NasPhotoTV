package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FnOsSmartAlbumsTest {
    @Test
    fun loadsOnlySceneCategoriesWithServerCountsAndPosterUrls() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val helper = helper(
            get = { path, params ->
                calls += path
                assertEquals("/p/api/v1/ai-smart-rec/categories", path)
                assertEquals(mapOf("limit" to "-1", "mainCategory" to "scene"), params.toMap())
                JSONObject(
                    """{"list":[
                        {"name":"天空","itemCount":"12","count":999,"poster":{"photoId":73,"uuid":"cover-session"}},
                        {"name":"猫","itemCount":0,"count":5,"cover":"/cat.jpg"},
                        {"name":"天空","itemCount":12},
                        {"name":"  "},{"id":99},null
                    ]}""",
                )
            },
        )
        val albums = helper.getAlbums()
        assertEquals(listOf("天空", "猫"), albums.map { it.name })
        assertEquals(listOf(12, 0), albums.map { it.itemCount })
        assertEquals("https://nas.test:5667/p/api/v1/stream/p/t/73/m/cover-session", albums[0].coverUrl)
        assertEquals("https://nas.test:5667/cat.jpg", albums[1].coverUrl)
        assertTrue(albums.all { it.type == AlbumType.SMART_CATEGORY && it.isSmart })
        assertEquals(listOf("/p/api/v1/ai-smart-rec/categories"), calls)
    }

    @Test
    fun categoryIdsSurviveReloadAndRecoverExactSearchKeywordWithoutAnAlbumRequest() = runBlocking<Unit> {
        val name = "海边 + 山/河 & 100%"
        val original = helper(get = { _, _ -> JSONObject().put("list", org.json.JSONArray().put(JSONObject().put("name", name))) })
        val album = original.getAlbums().single()
        val reloaded = helper(
            get = { _, _ -> error("Opening an AI category must not request a normal album") },
            post = { path, body ->
                assertEquals("/p/api/v1/magic-search/do", path)
                assertEquals(name, body.getString("keyword"))
                assertEquals(0, body.getJSONArray("antiFilters").length())
                assertEquals(2, body.length())
                JSONObject("""{"list":[]}""")
            },
        )
        assertTrue(reloaded.getMedia(album.id).isEmpty())
        assertEquals(album.id, original.getAlbums().single().id)
    }

    @Test
    fun emptyListsAreSuccessfulAndNeverInventOrdinaryAlbums() = runBlocking<Unit> {
        val helper = helper(
            get = { _, _ -> JSONObject("""{"list":[]}""") },
            post = { _, _ -> JSONObject("""{"list":[],"total":0}""") },
            parse = { error("Empty results must not invoke the media parser") },
        )
        assertTrue(helper.getAlbums().isEmpty())
        assertTrue(helper.getMedia("smart:scene:sky").isEmpty())
    }

    @Test
    fun acceptsKnownListWrapperAndCountVariantsWithoutInventingACover() = runBlocking<Unit> {
        val helper = helper(get = { _, _ ->
            JSONObject("""{"items":[{"name":"风景","itemCount":null,"count":-1,"total":"8"}]}""")
        })
        val album = helper.getAlbums().single()
        assertEquals(8, album.itemCount)
        assertEquals("", album.coverUrl)
    }

    @Test
    fun malformedPayloadDoesNotMasqueradeAsAnEmptyCategoryOrMediaList() = runBlocking<Unit> {
        val helper = helper(
            get = { _, _ -> JSONObject("""{"unexpected":[]}""") },
            post = { _, _ -> JSONObject("""{"list":"bad"}""") },
        )
        expectFailure<IllegalStateException> { helper.getAlbums() }
        expectFailure<IllegalStateException> { helper.getMedia("smart:scene:sky") }
    }

    @Test
    fun requestFailuresPropagateInsteadOfProducingEmptyResults() = runBlocking<Unit> {
        val failure = IOException("NAS unavailable")
        val helper = helper(
            get = { _, _ -> throw failure },
            post = { _, _ -> throw failure },
        )
        assertSame(failure, expectFailure<IOException> { helper.getAlbums() })
        assertSame(failure, expectFailure<IOException> { helper.getMedia("smart:scene:sky") })
    }

    @Test
    fun cancellationPropagatesFromBothRequests() = runBlocking<Unit> {
        val cancelled = CancellationException("Account changed")
        val helper = helper(
            get = { _, _ -> throw cancelled },
            post = { _, _ -> throw cancelled },
        )
        assertSame(cancelled, expectFailure<CancellationException> { helper.getAlbums() })
        assertSame(cancelled, expectFailure<CancellationException> { helper.getMedia("smart:scene:sky") })
    }

    @Test
    fun legacyMagicSearchResultsGainAllGalleryImageUrlsAndRemainUnmodified() = runBlocking<Unit> {
        val response = JSONObject("""{"list":[{"photoId":17,"session":"photo-session"}]}""")
        val helper = helper(
            get = { path, params ->
                assertEquals("/p/api/v1/gallery/getOne", path)
                assertEquals(listOf("id" to "17"), params)
                JSONObject("""{"id":17,"category":"photo","fileName":"old.jpg","additional":{"thumbnail":{"originalUrl":"/verified-original"}}}""")
            },
            post = { _, _ -> response },
            parse = { json ->
                assertEquals("17", json.getString("id"))
                val thumb = json.getJSONObject("additional").getJSONObject("thumbnail")
                assertEquals("/p/api/v1/stream/p/t/17/m/photo-session", thumb.getString("mUrl"))
                assertEquals("/p/api/v1/stream/p/t/17/s/photo-session", thumb.getString("sUrl"))
                assertEquals("/p/api/v1/stream/p/t/17/s/photo-session", thumb.getString("xsUrl"))
                parsedMedia(json)
            },
        )
        val item = helper.getMedia("smart:scene:sky").single()
        assertEquals("https://nas.test:5667/verified-original", item.url)
        assertEquals("https://nas.test:5667/p/api/v1/stream/p/t/17/m/photo-session", item.thumbnailUrl)
        assertEquals("smart:scene:sky", item.albumId)
        assertFalse(response.getJSONArray("list").getJSONObject(0).has("additional"))
    }

    @Test
    fun fullVideoAndLivePayloadsKeepTheirMetadataAndSignedUrls() = runBlocking<Unit> {
        val helper = helper(post = { _, _ ->
            JSONObject(
                """{"list":[
                    {"id":21,"category":"video","session":"s","fileName":"trip.mp4","additional":{"thumbnail":{
                        "mUrl":"/signed-preview?token=kept","originalUrl":"/original","videoUrl":"/video"}}},
                    {"id":22,"category":"photo","fileName":"live.jpg","isLive":1,"photoUUID":"live","additional":{"thumbnail":{
                        "mUrl":"/live-preview","originalUrl":"/live-photo","videoUrl":"/live-motion"}}}
                ]}""",
            )
        })
        val media = helper.getMedia("smart:scene:travel")
        assertEquals(2, media.size)
        assertEquals(MediaType.VIDEO, media[0].type)
        assertEquals("https://nas.test:5667/video", media[0].url)
        assertEquals("https://nas.test:5667/signed-preview?token=kept", media[0].thumbnailUrl)
        assertTrue(media[1].isLive)
        assertEquals("https://nas.test:5667/live-motion", media[1].liveUrl)
        assertEquals("https://nas.test:5667/live-photo", media[1].url)
    }

    @Test
    fun mediaResultsDeduplicateAndSkipMalformedItemsWithoutInventingPagination() = runBlocking<Unit> {
        var requests = 0
        val detailIds = mutableListOf<String>()
        val helper = helper(
            get = { path, params ->
                assertEquals("/p/api/v1/gallery/getOne", path)
                val id = params.toMap().getValue("id")
                detailIds += id
                JSONObject("""{"id":$id,"category":"photo","fileName":"$id.jpg","additional":{"thumbnail":{"originalUrl":"/original-$id"}}}""")
            },
            post = { _, body ->
                requests += 1
                assertFalse(body.has("offset"))
                assertFalse(body.has("limit"))
                JSONObject("""{"list":[{"id":1,"session":"a"},{"id":1,"session":"a"},null,{}, {"id":2,"session":"b"}],"total":99}""")
            },
        )
        assertEquals(listOf("1", "2"), helper.getMedia("smart:scene:sky").map { it.id })
        assertEquals(1, requests)
        assertEquals(listOf("1", "2"), detailIds)
    }

    @Test
    fun legacyVideoUsesGetOneMetadataInsteadOfBeingMisclassifiedAsAPhoto() = runBlocking<Unit> {
        val helper = helper(
            post = { _, _ -> JSONObject("""{"list":[{"id":42,"session":"old-session"}]}""") },
            get = { path, params ->
                assertEquals("/p/api/v1/gallery/getOne", path)
                assertEquals(listOf("id" to "42"), params)
                JSONObject("""{"id":42,"category":"video","fileName":"trip.mp4","durationMs":4200,"additional":{"thumbnail":{"videoUrl":"/verified-video"}}}""")
            },
            parse = { json ->
                assertEquals(4200L, json.getLong("durationMs"))
                assertFalse(json.getJSONObject("additional").getJSONObject("thumbnail").has("originalUrl"))
                parsedMedia(json)
            },
        )
        val item = helper.getMedia("smart:scene:travel").single()
        assertEquals(MediaType.VIDEO, item.type)
        assertEquals("https://nas.test:5667/verified-video", item.url)
    }

    @Test
    fun partialMetadataIsEnrichedWithoutOverwritingAlreadySignedUrls() = runBlocking<Unit> {
        val helper = helper(
            post = { _, _ -> JSONObject("""{"list":[{"id":7,"category":"video","fileName":"clip.mp4","additional":{"thumbnail":{"mUrl":"/signed?keep=yes"}}}]}""") },
            get = { _, _ -> JSONObject("""{"id":7,"category":"video","fileName":"clip.mp4","additional":{"thumbnail":{"mUrl":"/other-preview","videoUrl":"/verified-play"}}}""") },
        )
        val item = helper.getMedia("smart:scene:travel").single()
        assertEquals("https://nas.test:5667/signed?keep=yes", item.thumbnailUrl)
        assertEquals("https://nas.test:5667/verified-play", item.url)
    }

    @Test
    fun failedOrIncompleteDetailsNeverProduceFakePhotosOrEnterTheCache() = runBlocking<Unit> {
        val failure = IOException("Detail unavailable")
        var detailCalls = 0
        val helper = helper(
            post = { _, _ -> JSONObject("""{"list":[{"id":7,"session":"s"}]}""") },
            get = { _, _ ->
                detailCalls += 1
                if (detailCalls == 1) throw failure
                JSONObject("""{"id":7}""")
            },
            parse = { error("Unidentified media must not reach the photo parser") },
        )
        assertSame(failure, expectFailure<IOException> { helper.getMedia("smart:scene:sky") })
        expectFailure<IllegalStateException> { helper.getMedia("smart:scene:sky") }
        assertEquals(2, detailCalls)
    }

    @Test
    fun successfulMediaLoadsAreCachedUntilCategoriesRefresh() = runBlocking<Unit> {
        var searches = 0
        var details = 0
        val helper = helper(
            post = { _, _ ->
                searches += 1
                JSONObject("""{"list":[{"id":7,"session":"s"}]}""")
            },
            get = { path, _ ->
                if (path.endsWith("/categories")) {
                    JSONObject("""{"list":[{"name":"sky"}]}""")
                } else {
                    details += 1
                    JSONObject("""{"id":7,"category":"photo","fileName":"sky.jpg","additional":{"thumbnail":{"originalUrl":"/real-photo"}}}""")
                }
            },
        )
        val first = helper.getMedia("smart:scene:sky")
        assertSame(first, helper.getMedia("smart:scene:sky"))
        assertEquals(1, searches)
        assertEquals(1, details)
        helper.getAlbums()
        assertEquals(first, helper.getMedia("smart:scene:sky"))
        assertEquals(2, searches)
        assertEquals(2, details)
    }

    @Test
    fun detailCancellationIsPropagatedWithoutCachingAnEmptyList() = runBlocking<Unit> {
        val cancelled = CancellationException("Leaving category")
        var attempts = 0
        val helper = helper(
            post = { _, _ -> JSONObject("""{"list":[{"id":7,"session":"s"}]}""") },
            get = { _, _ -> attempts += 1; throw cancelled },
        )
        repeat(2) {
            assertSame(cancelled, expectFailure<CancellationException> { helper.getMedia("smart:scene:sky") })
        }
        assertEquals(2, attempts)
    }

    @Test
    fun rejectsNonSmartOrMalformedIdsBeforeMakingARequest() = runBlocking<Unit> {
        val helper = helper(post = { _, _ -> error("Invalid ids must not make requests") })
        for (id in listOf("123", "smart:scene:", "smart:scene:%broken")) {
            expectFailure<IllegalArgumentException> { helper.getMedia(id) }
        }
    }

    private fun helper(
        get: suspend (String, List<Pair<String, String>>) -> JSONObject = { _, _ -> error("Unexpected GET") },
        post: suspend (String, JSONObject) -> JSONObject = { _, _ -> error("Unexpected POST") },
        parse: (JSONObject) -> MediaItem? = ::parsedMedia,
    ) = FnOsSmartAlbums(get, post, parse, ::absolute)

    /** Mirrors the repository parser's input contract, without depending on Android networking. */
    private fun parsedMedia(json: JSONObject): MediaItem? {
        val id = json.optLong("id", 0L).takeIf { it > 0 } ?: return null
        val thumb = json.optJSONObject("additional")?.optJSONObject("thumbnail") ?: JSONObject()
        val video = json.optString("category") == "video"
        val motion = thumb.optString("videoUrl")
        return MediaItem(
            id = id.toString(),
            title = json.optString("fileName"),
            type = if (video) MediaType.VIDEO else MediaType.PHOTO,
            url = absolute(if (video) motion else thumb.optString("originalUrl")),
            thumbnailUrl = absolute(thumb.optString("mUrl")),
            isLive = json.optInt("isLive") == 1 && motion.isNotEmpty(),
            liveUrl = if (json.optInt("isLive") == 1) absolute(motion) else "",
        )
    }

    private fun absolute(path: String): String = when {
        path.isEmpty() -> ""
        path.startsWith("https://") || path.startsWith("http://") -> path
        else -> "https://nas.test:5667/${path.trimStart('/')}"
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.simpleName}, got $error", error)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}

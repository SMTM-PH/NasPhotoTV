package com.nasphoto.tv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.2.9：文件夹视图条目缺 additional.thumbnail 时，必须按官方前端算法拼出图片地址。 */
class MediaUrlsTest {

    private val base = "https://nas.example:5667"

    @Test
    fun folderViewItemWithoutThumbnailUsesPhotoStreamUrls() {
        // folder_view/getFileList 的真实条目形态：只有 id / photoUUID / fileName / category
        val json = JSONObject(
            """
            {"id":6491,"photoUUID":"15200454-e34b-42e4-ab1b-b5d028fb1ded",
             "fileName":"IMG_20261110_114839.jpg","category":"photo","dateTime":"2026:11:10 11:48:39"}
            """.trimIndent(),
        )
        val urls = resolveMediaUrls(json, 6491L, base)
        assertEquals(
            "$base/p/api/v1/stream/p/t/6491/s/15200454-e34b-42e4-ab1b-b5d028fb1ded",
            urls.thumbnailUrl,
        )
        assertEquals(
            "$base/p/api/v1/stream/p/t/6491/o/15200454-e34b-42e4-ab1b-b5d028fb1ded",
            urls.fullUrl,
        )
        assertEquals("", urls.liveUrl)
    }

    @Test
    fun serverProvidedThumbnailUrlsAreKeptVerbatim() {
        val json = JSONObject(
            """
            {"id":6491,"photoUUID":"uuid-1","fileName":"a.jpg","category":"photo",
             "additional":{"thumbnail":{
               "mUrl":"https://cdn.example/m.jpg","sUrl":"https://cdn.example/s.jpg",
               "originalUrl":"https://cdn.example/o.jpg","videoUrl":""}}}
            """.trimIndent(),
        )
        val urls = resolveMediaUrls(json, 6491L, base)
        assertEquals("https://cdn.example/m.jpg", urls.thumbnailUrl)
        assertEquals("https://cdn.example/o.jpg", urls.fullUrl)
    }

    @Test
    fun topLevelThumbnailObjectAndDirectFieldsAreSupported() {
        val nested = JSONObject(
            """{"id":7,"photoUUID":"u","fileName":"a.jpg","category":"photo",
                "thumbnail":{"mUrl":"/p/api/v1/stream/p/t/7/m/u","originalUrl":"/p/api/v1/stream/p/t/7/o/u"}}""",
        )
        assertEquals("$base/p/api/v1/stream/p/t/7/m/u", resolveMediaUrls(nested, 7, base).thumbnailUrl)
        val direct = JSONObject(
            """{"id":8,"photoUUID":"u2","fileName":"b.jpg","category":"photo",
                "mUrl":"/x/m.jpg","originalUrl":"/x/o.jpg"}""",
        )
        assertEquals("$base/x/m.jpg", resolveMediaUrls(direct, 8, base).thumbnailUrl)
        assertEquals("$base/x/o.jpg", resolveMediaUrls(direct, 8, base).fullUrl)
    }

    @Test
    fun videoWithoutVideoUrlFallsBackToVideoStream() {
        val json = JSONObject(
            """{"id":91,"photoUUID":"vid-uuid","fileName":"clip.mp4","category":"video"}""",
        )
        val urls = resolveMediaUrls(json, 91, base)
        assertEquals("$base/p/api/v1/stream/p/t/91/s/vid-uuid", urls.thumbnailUrl)
        assertEquals("$base/p/api/v1/stream/v/91", urls.fullUrl)
    }

    @Test
    fun livePhotoUsesVideoStreamWhenVideoUrlMissing() {
        val json = JSONObject(
            """{"id":5,"photoUUID":"live-uuid","fileName":"live.jpg","category":"photo","isLive":1}""",
        )
        assertEquals("$base/p/api/v1/stream/v/5", resolveMediaUrls(json, 5, base).liveUrl)
    }

    @Test
    fun rawPhotoUsesMediumPreviewInsteadOfOriginalRaw() {
        val json = JSONObject(
            """{"id":12,"photoUUID":"raw-uuid","fileName":"shot.CR3","category":"photo"}""",
        )
        assertEquals(
            "$base/p/api/v1/stream/p/t/12/m/raw-uuid",
            resolveMediaUrls(json, 12, base).fullUrl,
        )
    }

    @Test
    fun posterUrlBuildsXsStreamUrl() {
        val poster = JSONObject("""{"id":6491,"uuid":"15200454-e34b-42e4-ab1b-b5d028fb1ded"}""")
        assertEquals(
            "$base/p/api/v1/stream/p/t/6491/xs/15200454-e34b-42e4-ab1b-b5d028fb1ded",
            posterUrl(poster, base),
        )
        assertEquals("", posterUrl(JSONObject("""{"id":1}"""), base))
    }

    @Test
    fun absoluteUrlKeepsReverseProxySubPath() {
        val relay = "https://relay.example/fnos"
        assertEquals(
            "$relay/p/api/v1/stream/p/t/1/o/u",
            absoluteUrl("/p/api/v1/stream/p/t/1/o/u", relay),
        )
        assertEquals("https://cdn.example/x.jpg", absoluteUrl("https://cdn.example/x.jpg", relay))
        assertEquals("", absoluteUrl("", relay))
    }

    @Test
    fun missingUuidCannotProduceBrokenStreamUrl() {
        val json = JSONObject("""{"id":3,"fileName":"a.jpg","category":"photo"}""")
        val urls = resolveMediaUrls(json, 3, base)
        assertTrue(urls.thumbnailUrl.isBlank())
        assertTrue(urls.fullUrl.isBlank())
    }
}

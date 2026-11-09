package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.RAW_EXTENSIONS
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/**
 * fnOS AI「场景事物」相册。只消费智能分类与语义搜索接口，不混入普通相册。
 *
 * 分类列表：ai-smart-rec/categories(mainCategory=scene, limit=-1)。
 * 分类内容：magic-search/do({keyword, antiFilters:[]})；该接口没有已验证的分页参数。
 * 请求异常直接交给调用方处理，合法空列表才表示「暂无分类 / 媒体」。
 */
internal class FnOsSmartAlbums(
    private val getJson: suspend (String, List<Pair<String, String>>) -> JSONObject,
    private val postJson: suspend (String, JSONObject) -> JSONObject,
    private val parseMedia: (JSONObject) -> MediaItem?,
    private val absolute: (String) -> String,
) {
    private val mediaCache = mutableMapOf<String, List<MediaItem>>()

    suspend fun getAlbums(): List<Album> {
        val data = getJson(
            CATEGORIES,
            listOf("limit" to "-1", "mainCategory" to "scene"),
        )
        val list = responseList(data)
        val albums = LinkedHashMap<String, Album>()
        for (i in 0 until list.length()) {
            coroutineContext.ensureActive()
            val json = list.optJSONObject(i) ?: continue
            val name = firstString(json, "name")
            if (name.isEmpty()) continue
            // The name is the server's semantic-search keyword. Encoding it makes ids stable
            // across list ordering and lets a newly created repository reopen the same album.
            val id = ID_PREFIX + URLEncoder.encode(name, Charsets.UTF_8.name())
            albums.putIfAbsent(
                id,
                Album(
                    id = id,
                    name = name,
                    coverUrl = categoryCover(json),
                    itemCount = categoryCount(json),
                    type = AlbumType.SMART_CATEGORY,
                    subtitle = "场景事物",
                    isSmart = true,
                ),
            )
        }
        return albums.values.toList().also { mediaCache.clear() }
    }

    suspend fun getMedia(albumId: String): List<MediaItem> {
        mediaCache[albumId]?.let { return it }
        require(albumId.startsWith(ID_PREFIX)) { "不是场景事物分类" }
        val name = URLDecoder.decode(albumId.removePrefix(ID_PREFIX), Charsets.UTF_8.name())
        require(name.isNotBlank()) { "分类名称不能为空" }
        val data = postJson(
            MAGIC_SEARCH,
            JSONObject().put("keyword", name).put("antiFilters", JSONArray()),
        )
        val list = responseList(data)
        val media = LinkedHashMap<String, MediaItem>()
        for (i in 0 until list.length()) {
            coroutineContext.ensureActive()
            val json = list.optJSONObject(i) ?: continue
            val id = firstString(json, "id", "photoId")
            if (id.toLongOrNull()?.let { it > 0 } != true || media.containsKey(id)) continue
            val item = parseMedia(normalizeSearchItem(json)) ?: continue
            media.putIfAbsent(item.id, item.copy(albumId = albumId))
        }
        return media.values.toList().also { mediaCache[albumId] = it }
    }

    private fun categoryCover(json: JSONObject): String {
        val direct = firstString(json, "cover", "thumb", "posterUrl", "imageUrl")
        if (direct.isNotEmpty()) return absolute(direct)
        val poster = json.optJSONObject("poster") ?: return ""
        val id = firstString(poster, "photoId", "id")
        val uuid = firstString(poster, "uuid", "photoUUID", "session")
        return if (id.isNotEmpty() && uuid.isNotEmpty()) {
            absolute("/p/api/v1/stream/p/t/$id/s/$uuid")
        } else {
            ""
        }
    }

    private fun categoryCount(json: JSONObject): Int {
        for (key in listOf("itemCount", "item_count", "count", "total", "photoCount", "photo_count")) {
            val count = when (val value = json.opt(key)) {
                is Number -> value.toDouble().takeIf { it.isFinite() && it >= 0 }?.toLong()
                is String -> value.toLongOrNull()?.takeIf { it >= 0 }
                else -> null
            }
            if (count != null) return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return 0
    }

    /** Older magic-search responses contain only id + session; newer ones carry full metadata. */
    private suspend fun normalizeSearchItem(source: JSONObject): JSONObject {
        val json = JSONObject(source.toString())
        val id = firstString(json, "id", "photoId")
        if (id.isEmpty()) return json
        json.put("id", id)
        var additional = json.optJSONObject("additional") ?: JSONObject().also {
            json.put("additional", it)
        }
        var thumb = additional.optJSONObject("thumbnail")
            ?: json.optJSONObject("thumbnail")
            ?: JSONObject()
        additional.put("thumbnail", thumb)
        if (!hasPlaybackMetadata(json)) {
            // id + session does not identify photos versus videos, nor RAW / Live Photos.
            // Read the real metadata instead of silently treating every result as a photo.
            val detail = getJson(GALLERY_DETAIL, listOf("id" to id))
            mergeMissing(json, detail)
            if (!hasPlaybackMetadata(json)) {
                throw IllegalStateException("飞牛未返回完整媒体信息（id $id）")
            }
            additional = json.getJSONObject("additional")
            thumb = additional.getJSONObject("thumbnail")
        }
        val uuid = firstString(json, "uuid", "photoUUID", "session")
        if (uuid.isNotEmpty()) {
            // Fill only absent URLs: full payloads may carry signed URLs, videoUrl and Live
            // Photo data that must keep their original values.
            for ((key, size) in listOf("mUrl" to "m", "sUrl" to "s", "xsUrl" to "s")) {
                if (firstString(thumb, key).isEmpty()) {
                    thumb.put(key, "/p/api/v1/stream/p/t/$id/$size/$uuid")
                }
            }
        }
        return json
    }

    private fun hasPlaybackMetadata(json: JSONObject): Boolean {
        val name = firstString(json, "fileName")
        if (name.isEmpty()) return false
        val thumb = json.optJSONObject("additional")?.optJSONObject("thumbnail") ?: return false
        return when (firstString(json, "category").lowercase()) {
            "video" -> firstString(thumb, "videoUrl").isNotEmpty()
            "photo" -> {
                val rawPreview = name.substringAfterLast('.', "").uppercase() in RAW_EXTENSIONS &&
                    firstString(thumb, "mUrl").isNotEmpty()
                val hasImage = firstString(thumb, "originalUrl").isNotEmpty() || rawPreview
                hasImage && (json.optInt("isLive", 0) != 1 || firstString(thumb, "videoUrl").isNotEmpty())
            }
            else -> false
        }
    }

    /** Preserve already supplied signed URLs while adding missing detail metadata. */
    private fun mergeMissing(target: JSONObject, fallback: JSONObject) {
        for (key in fallback.keys()) {
            val current = target.opt(key)
            val incoming = fallback.opt(key)
            when {
                current is JSONObject && incoming is JSONObject -> mergeMissing(current, incoming)
                current == null || current == JSONObject.NULL || current is String && current.isBlank() -> {
                    target.put(key, incoming)
                }
            }
        }
    }

    private fun responseList(data: JSONObject): JSONArray {
        for (key in listOf("list", "items", "records", "rows", "data", "result")) {
            data.optJSONArray(key)?.let { return it }
        }
        throw IllegalStateException("飞牛智能分类返回格式异常（缺少媒体列表）")
    }

    private fun firstString(json: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = json.opt(key)
            val text = when (value) {
                is String -> value.trim()
                is Number -> value.toString()
                else -> ""
            }
            if (text.isNotEmpty() && text != "null") return text
        }
        return ""
    }

    companion object {
        const val ID_PREFIX = "smart:scene:"
        private const val CATEGORIES = "/p/api/v1/ai-smart-rec/categories"
        private const val MAGIC_SEARCH = "/p/api/v1/magic-search/do"
        private const val GALLERY_DETAIL = "/p/api/v1/gallery/getOne"
    }
}

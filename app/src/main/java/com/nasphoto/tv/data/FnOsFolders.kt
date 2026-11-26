/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.NasFolder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/**
 * fnOS 文件夹视图，使用 NAS Web 前端的同一组接口：
 * photo/folder/list 给扫描根目录；folder_view/getFolderList 给直接子目录；
 * folder_view/getFileList 只给当前目录的媒体，不包含子目录。
 *
 * getFolderList 的 {name,path} 本身就是目录，无需 isDir / folderId 标记。
 * 所有标识均取完整路径，避免扫描根目录 id 被复用到子目录，也保留 NAS 路径的大小写。
 */
internal class FnOsFolders(
    private val getJson: suspend (String, List<Pair<String, String>>) -> JSONObject,
    private val parseMedia: (JSONObject) -> MediaItem?,
    /** 目录封面的批量接口；null = 不取封面（测试 / 旧版本降级） */
    private val postJson: (suspend (String, JSONObject) -> JSONObject)? = null,
    private val baseUrl: String = "",
) {
    @Volatile
    private var folderCache: Map<String, NasFolder> = emptyMap()

    suspend fun getFolders(): List<NasFolder> {
        val roots = directoryList(getJson(ROOTS, sortParams))
        val folders = LinkedHashMap<String, NasFolder>()

        suspend fun visit(folder: NasFolder) {
            coroutineContext.ensureActive()
            if (folders.containsKey(folder.id)) return
            check(folders.size < MAX_FOLDER_NODES && folder.depth <= MAX_FOLDER_DEPTH) {
                "文件夹数量或层级超过安全上限，无法完整读取"
            }
            folders[folder.id] = folder
            val children = directoryList(
                getJson(CHILDREN, listOf("folderPath" to folder.path) + sortParams),
            )
            for (i in 0 until children.length()) {
                val json = children.optJSONObject(i) ?: error("子目录响应不是目录对象")
                visit(parseFolder(json, folder))
            }
        }

        for (i in 0 until roots.length()) {
            val json = roots.optJSONObject(i) ?: error("根目录响应不是目录对象")
            visit(parseFolder(json, parent = null))
        }
        // Only publish a complete traversal. Permission/network errors must remain visible to callers.
        val discovered = folders.values.toList()
        // 目录封面：官方前端用 folder_view/cover 按父目录批量取 poster，
        // 失败不能影响目录树本身（旧版本可能没有这个接口）。
        val withCovers = attachCovers(discovered)
        folderCache = withCovers.associateBy { it.id }
        return withCovers
    }

    /**
     * 按父目录批量取封面。官方前端（XPOBuNOhKVjDE-Zr.js）每次最多 50 个 dirName：
     * POST {currentPath, dirNames} → data.list[] {dirPath, cover:[{id,uuid}], defaultCover:[...]}。
     * 拼出的地址与前端 getPhotoUrl 一致：/p/api/v1/stream/p/t/{id}/xs/{uuid}。
     */
    private suspend fun attachCovers(folders: List<NasFolder>): List<NasFolder> {
        val poster = postJson ?: return folders
        if (folders.isEmpty()) return folders
        val covers = HashMap<String, String>()
        folders.groupBy { parentPathOf(it.path) }.forEach { (parent, children) ->
            for (batch in children.chunked(COVER_BATCH)) {
                runCatching {
                    val dirNames = JSONArray()
                    batch.forEach { dirNames.put(it.name) }
                    val body = JSONObject()
                        .put("currentPath", parent)
                        .put("dirNames", dirNames)
                    val data = poster(COVER, body)
                    val list = data.optJSONArray("list")
                        ?: data.optJSONArray("covers")
                        ?: return@runCatching
                    for (i in 0 until list.length()) {
                        val entry = list.optJSONObject(i) ?: continue
                        val url = firstPoster(entry)?.let { posterUrl(it, baseUrl) }.orEmpty()
                        if (url.isBlank()) continue
                        val dirPath = firstNonBlank(entry, "dirPath", "folderPath", "path", "dirName")
                        val key = when {
                            dirPath.startsWith("/") -> dirPath.trimEnd('/')
                            dirPath.isNotBlank() -> parent.trimEnd('/') + "/" + dirPath.trim('/')
                            else -> batch.getOrNull(i)?.path.orEmpty()
                        }
                        if (key.isNotBlank()) covers[key] = url
                    }
                }
            }
        }
        if (covers.isEmpty()) return folders
        return folders.map { folder ->
            covers[folder.path.trimEnd('/')]?.let { folder.copy(coverUrl = it) } ?: folder
        }
    }

    private fun firstPoster(entry: JSONObject): JSONObject? {
        for (key in listOf("cover", "defaultCover", "poster", "posters")) {
            val arr = entry.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if ((firstNonBlank(item, "id", "photoId").toLongOrNull() ?: 0L) > 0L &&
                    firstNonBlank(item, "uuid", "photoUUID", "session").isNotBlank()
                ) return item
            }
        }
        return null
    }

    private fun parentPathOf(path: String): String {
        val trimmed = path.trimEnd('/')
        val cut = trimmed.lastIndexOf('/')
        return if (cut <= 0) "/" else trimmed.substring(0, cut)
    }

    suspend fun getMedia(folderId: String, limit: Int): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val path = folderCache[folderId]?.path ?: folderId.removePrefix(ID_PREFIX).also {
            require(folderId.startsWith(ID_PREFIX) && it.startsWith('/')) { "文件夹路径无效" }
        }
        val collected = LinkedHashMap<String, MediaItem>()
        val rawIds = HashSet<String>()
        var offset = 0
        var knownTotal = -1
        while (collected.size < limit) {
            coroutineContext.ensureActive()
            check(offset < MAX_MEDIA_ITEMS) { "目录媒体数量超过安全上限，无法完整读取" }
            val data = getJson(
                FILES,
                listOf(
                    "folderPath" to path,
                    "desc" to "true",
                    "orderBy" to "0",
                    "offset" to offset.toString(),
                    "limit" to minOf(MEDIA_PAGE_SIZE, limit - collected.size).toString(),
                ),
            )
            knownTotal = maxOf(knownTotal, positiveTotal(data))
            val list = responseList(data)
            if (list.length() == 0) {
                check(knownTotal <= offset) { "目录接口提前返回空页，无法完整读取" }
                break
            }
            var newRawItems = 0
            for (i in 0 until list.length()) {
                val json = list.optJSONObject(i) ?: continue
                // Track raw ids too: an unparseable media entry still consumes an offset.
                val rawId = json.optString("id").ifBlank { json.toString() }
                if (rawIds.add(rawId)) newRawItems++
                parseMedia(json)?.let { collected.putIfAbsent(it.id, it) }
            }
            check(newRawItems > 0) { "目录接口重复返回同一页，无法完整读取" }
            offset += list.length()
            if (knownTotal > 0 && offset >= knownTotal) break
            // Some versions return fewer rows than requested, without hasNext. Advance until
            // total is reached or an empty page, rather than treating a short page as the end.
        }
        return collected.values.sortedByDescending { it.dateTaken }.take(limit)
    }

    private fun parseFolder(json: JSONObject, parent: NasFolder?): NasFolder {
        val rawPath = if (parent == null) json.optString("folderPath") else json.optString("path")
        val path = rawPath.trimEnd('/').ifEmpty { if (rawPath == "/") "/" else "" }
        check(path.startsWith('/')) { "文件夹响应缺少完整路径" }
        return NasFolder(
            id = ID_PREFIX + path,
            path = path,
            name = json.optString("name").ifBlank { path.substringAfterLast('/').ifBlank { path } },
            photoCount = json.optInt("photoCount").coerceAtLeast(0),
            videoCount = json.optInt("videoCount").coerceAtLeast(0),
            parentId = parent?.id.orEmpty(),
            depth = (parent?.depth ?: -1) + 1,
        )
    }

    private fun responseList(data: JSONObject): JSONArray =
        data.optJSONArray("list") ?: error("文件夹接口响应缺少 list")

    private fun directoryList(data: JSONObject): JSONArray {
        val list = responseList(data)
        // The official client requests the entire child-directory list without paging params.
        check(positiveTotal(data) <= list.length()) { "目录接口返回的子目录不完整" }
        return list
    }

    private companion object {
        const val ROOTS = "/p/api/v1/photo/folder/list"
        const val CHILDREN = "/p/api/v1/folder_view/getFolderList"
        const val FILES = "/p/api/v1/folder_view/getFileList"
        const val COVER = "/p/api/v1/folder_view/cover"
        const val COVER_BATCH = 50
        const val ID_PREFIX = "path:"
        const val MAX_FOLDER_NODES = 50_000
        const val MAX_FOLDER_DEPTH = 128
        const val MAX_MEDIA_ITEMS = 50_000
        const val MEDIA_PAGE_SIZE = 100
        val sortParams = listOf("desc" to "false", "orderBy" to "2")
    }
}

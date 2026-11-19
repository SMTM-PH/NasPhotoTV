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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the request chain; a fabricated nested folder/view tree hid the old bug. */
class FolderTreeTest {
    @Test
    fun readsUnmarkedChildrenFromDedicatedEndpointAndRecursesBeyondOneLevel() = runBlocking<Unit> {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val folders = FnOsFolders(
            getJson = { path, params ->
                val p = params.toMap()
                calls += path to p
                assertEquals("false", p["desc"])
                assertEquals("2", p["orderBy"])
                when (path) {
                    ROOTS -> JSONObject("""{"list":[{"folderId":1,"folderPath":"/vol1/1000/Photos","photoCount":4839,"videoCount":455}]}""")
                    CHILDREN -> when (p["folderPath"]) {
                        "/vol1/1000/Photos" -> JSONObject("""{"list":[{"name":"DJI_001","path":"/vol1/1000/Photos/DJI_001"}],"total":1}""")
                        "/vol1/1000/Photos/DJI_001" -> JSONObject("""{"list":[{"name":"夏天","path":"/vol1/1000/Photos/DJI_001/夏天"}],"total":1}""")
                        "/vol1/1000/Photos/DJI_001/夏天" -> emptyList()
                        else -> error("Unexpected folder path: $p")
                    }
                    else -> error("Folder discovery must not request folder/view or getFileList: $path")
                }
            },
            parseMedia = { error("Discovering folders must not parse photos") },
        ).getFolders()

        assertEquals(listOf("/vol1/1000/Photos", "/vol1/1000/Photos/DJI_001", "/vol1/1000/Photos/DJI_001/夏天"), folders.map { it.path })
        assertEquals(listOf(0, 1, 2), folders.map { it.depth })
        assertEquals(listOf("", "path:/vol1/1000/Photos", "path:/vol1/1000/Photos/DJI_001"), folders.map { it.parentId })
        assertEquals(4839, folders.first().photoCount)
        assertEquals(455, folders.first().videoCount)
        assertEquals(4, calls.size)
        assertTrue(calls.all { "folderId" !in it.second && "limit" !in it.second })
    }

    @Test
    fun coversEveryRootAndKeepsCaseSensitivePathsAndStableIds() = runBlocking<Unit> {
        val roots = JSONArray()
        repeat(13) { roots.put(JSONObject().put("folderId", it).put("folderPath", "/Root$it")) }
        val visited = mutableListOf<String>()
        val loader = FnOsFolders(
            getJson = { path, params ->
                if (path == ROOTS) JSONObject().put("list", roots) else {
                    val folder = params.toMap().getValue("folderPath")
                    visited += folder
                    if (folder == "/Root0") JSONObject("""{"list":[
                        {"folderId":0,"name":"a","path":"/Root0/a"},
                        {"folderId":0,"name":"A","path":"/Root0/A"},
                        {"name":"duplicate","path":"/Root0/"}
                    ]}""") else emptyList()
                }
            },
            parseMedia = { null },
        )
        val folders = loader.getFolders()
        assertEquals(15, folders.size)
        assertEquals(15, folders.map { it.id }.distinct().size)
        assertEquals(15, visited.size)
        assertTrue(visited.contains("/Root12"))
        assertEquals(listOf("path:/Root0/a", "path:/Root0/A"), folders.filter { it.depth == 1 }.map { it.id })
        assertEquals(folders, loader.getFolders())
    }

    @Test
    fun opensChildByExactPathWithoutReusingScanRootIdAndPaginatesShortPages() = runBlocking<Unit> {
        val offsets = mutableListOf<Int>()
        val loader = FnOsFolders(
            getJson = { path, params ->
                assertEquals(FILES, path)
                val p = params.toMap()
                assertEquals("/Photos/孩子 + Summer", p["folderPath"])
                assertEquals("true", p["desc"])
                assertEquals("0", p["orderBy"])
                assertFalse(p.containsKey("folderId"))
                val offset = p.getValue("offset").toInt().also { offsets += it }
                when (offset) {
                    0 -> JSONObject("""{"list":[{"id":3,"date":"2017-03-01"},{"id":0}],"total":4}""")
                    2 -> JSONObject("""{"list":[{"id":2,"date":"2000-01-01"}],"total":4}""")
                    3 -> JSONObject("""{"list":[{"id":1,"date":"2026-01-01"}],"total":4}""")
                    else -> error("Unexpected offset $offset")
                }
            },
            parseMedia = ::parseMedia,
        )
        val media = loader.getMedia("path:/Photos/孩子 + Summer", 1000)
        assertEquals(listOf("1", "3", "2"), media.map { it.id })
        assertEquals(listOf(0, 2, 3), offsets)
    }

    @Test
    fun shortPagesWithoutTotalContinueUntilEmptyAndRespectRequestedLimit() = runBlocking<Unit> {
        val offsets = mutableListOf<Int>()
        val loader = FnOsFolders(
            getJson = { _, params ->
                val offset = params.toMap().getValue("offset").toInt().also { offsets += it }
                if (offset < 2) JSONObject("""{"list":[{"id":${offset + 1},"date":"2000-01-01"}]}""")
                else emptyList()
            },
            parseMedia = ::parseMedia,
        )
        assertEquals(2, loader.getMedia("path:/Photos", 1000).size)
        assertEquals(listOf(0, 1, 2), offsets)
        offsets.clear()
        assertEquals(1, loader.getMedia("path:/Photos", 1).size)
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun errorsAndCancellationAreVisibleInsteadOfPretendingOnlyRootsExist() = runBlocking<Unit> {
        for (expected in listOf(IOException("permission denied"), CancellationException("account changed"))) {
            val loader = FnOsFolders(
                getJson = { path, _ ->
                    if (path == ROOTS) JSONObject("""{"list":[{"folderPath":"/Photos"}]}""")
                    else throw expected
                },
                parseMedia = ::parseMedia,
            )
            assertSame(expected, failure { loader.getFolders() })
            assertSame(expected, failure { loader.getMedia("path:/Photos", 10) })
        }
        assertTrue(FnOsFolders({ _, _ -> emptyList() }, ::parseMedia).getFolders().isEmpty())
    }

    @Test
    fun malformedFolderResponseAndRepeatedMediaPageFailExplicitly() = runBlocking<Unit> {
        val malformed = FnOsFolders({ _, _ -> JSONObject("""{"unexpected":[]}""") }, ::parseMedia)
        assertTrue(failure { malformed.getFolders() } is IllegalStateException)
        val repeated = FnOsFolders(
            { _, _ -> JSONObject("""{"list":[{"id":1,"date":"2017-01-01"}],"total":3}""") },
            ::parseMedia,
        )
        assertTrue(failure { repeated.getMedia("path:/Photos", 1000) } is IllegalStateException)
        val prematureEmpty = FnOsFolders(
            { _, _ -> JSONObject("""{"list":[],"total":3}""") },
            ::parseMedia,
        )
        assertTrue(failure { prematureEmpty.getMedia("path:/Photos", 1000) } is IllegalStateException)
        val totalMissingFromEmptyPage = FnOsFolders(
            { _, params ->
                if (params.toMap()["offset"] == "0")
                    JSONObject("""{"list":[{"id":1,"date":"2000-01-01"}],"total":3}""")
                else emptyList()
            },
            ::parseMedia,
        )
        assertTrue(failure { totalMissingFromEmptyPage.getMedia("path:/Photos", 1000) } is IllegalStateException)
    }

    @Test
    fun truncatedChildDirectoryListDoesNotPretendTraversalIsComplete() = runBlocking<Unit> {
        val loader = FnOsFolders(
            getJson = { path, _ ->
                if (path == ROOTS) JSONObject("""{"list":[{"folderPath":"/Photos"}]}""")
                else JSONObject("""{"list":[{"name":"first","path":"/Photos/first"}],"total":2}""")
            },
            parseMedia = ::parseMedia,
        )
        assertTrue(failure { loader.getFolders() } is IllegalStateException)
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Throwable) {
            return e
        }
        throw AssertionError("Expected a failure")
    }

    private fun parseMedia(json: JSONObject): MediaItem? {
        val id = json.optInt("id")
        if (id <= 0) return null
        return MediaItem(
            id = id.toString(), title = "$id.jpg", type = MediaType.PHOTO, url = "/$id.jpg",
            dateTaken = LocalDate.parse(json.getString("date")),
        )
    }

    private fun emptyList(): JSONObject = JSONObject("""{"list":[]}""")

    private companion object {
        const val ROOTS = "/p/api/v1/photo/folder/list"
        const val CHILDREN = "/p/api/v1/folder_view/getFolderList"
        const val FILES = "/p/api/v1/folder_view/getFileList"
    }
}

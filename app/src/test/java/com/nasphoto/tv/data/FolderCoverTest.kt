package com.nasphoto.tv.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.2.9：目录封面走官方 folder_view/cover 批量接口，失败不能影响目录树。 */
class FolderCoverTest {

    private val base = "https://nas.example:5667"

    @Test
    fun attachesCoversFromCoverApiWithoutExtraGetRequests() = runBlocking<Unit> {
        val posts = mutableListOf<Pair<String, JSONObject>>()
        val folders = FnOsFolders(
            getJson = { path, params ->
                when (path) {
                    "/p/api/v1/photo/folder/list" -> JSONObject(
                        """{"list":[{"folderId":1,"folderPath":"/vol1/1000/Photos","photoCount":10,"videoCount":1}]}""",
                    )
                    "/p/api/v1/folder_view/getFolderList" -> JSONObject(
                        """{"list":[
                            {"name":"DJI_001","path":"/vol1/1000/Photos/DJI_001"},
                            {"name":"MobileBackup","path":"/vol1/1000/Photos/MobileBackup"}],"total":2}""",
                    )
                    else -> error("unexpected GET $path $params")
                }
            },
            postJson = { path, body ->
                posts += path to body
                JSONObject(
                    """{"list":[
                        {"dirPath":"/vol1/1000/Photos/DJI_001","cover":[{"id":6491,"uuid":"uuid-a"}]},
                        {"dirPath":"/vol1/1000/Photos/MobileBackup","defaultCover":[{"id":6492,"uuid":"uuid-b"}]}
                    ]}""",
                )
            },
            parseMedia = { null },
            baseUrl = base,
        ).getFolders()

        assertEquals(3, folders.size)
        // 按父目录分组：扫描根目录一批（["Photos"]），它的直接子目录一批
        assertEquals(2, posts.size)
        assertTrue(posts.all { it.first == "/p/api/v1/folder_view/cover" })
        assertEquals("/vol1/1000", posts[0].second.getString("currentPath"))
        assertEquals(listOf("Photos"), posts[0].second.getJSONArray("dirNames").toStrings())
        assertEquals("/vol1/1000/Photos", posts[1].second.getString("currentPath"))
        assertEquals(
            listOf("DJI_001", "MobileBackup"),
            posts[1].second.getJSONArray("dirNames").toStrings(),
        )
        assertEquals(
            "$base/p/api/v1/stream/p/t/6491/xs/uuid-a",
            folders.first { it.name == "DJI_001" }.coverUrl,
        )
        assertEquals(
            "$base/p/api/v1/stream/p/t/6492/xs/uuid-b",
            folders.first { it.name == "MobileBackup" }.coverUrl,
        )
        assertEquals("", folders.first { it.depth == 0 }.coverUrl)
    }

    @Test
    fun coverApiFailureDoesNotBreakFolderTree() = runBlocking<Unit> {
        val folders = FnOsFolders(
            getJson = { path, _ ->
                if (path == "/p/api/v1/photo/folder/list") {
                    JSONObject("""{"list":[{"folderId":1,"folderPath":"/vol1/1000/Photos"}]}""")
                } else {
                    JSONObject("""{"list":[],"total":0}""")
                }
            },
            postJson = { _, _ -> error("cover endpoint unavailable") },
            parseMedia = { null },
            baseUrl = base,
        ).getFolders()
        assertEquals(1, folders.size)
        assertTrue(folders[0].coverUrl.isEmpty())
    }

    @Test
    fun coverBatchesAreSplitByFiftyDirNames() = runBlocking<Unit> {
        val names = JSONArray()
        repeat(120) { names.put(JSONObject().put("name", "d$it").put("path", "/vol1/1000/Photos/d$it")) }
        val batches = mutableListOf<Int>()
        FnOsFolders(
            getJson = { path, _ ->
                when (path) {
                    "/p/api/v1/photo/folder/list" -> JSONObject(
                        """{"list":[{"folderId":1,"folderPath":"/vol1/1000/Photos"}]}""",
                    )
                    "/p/api/v1/folder_view/getFolderList" -> JSONObject().put("list", names).put("total", 120)
                    else -> JSONObject("""{"list":[],"total":0}""")
                }
            },
            postJson = { _, body ->
                batches += body.getJSONArray("dirNames").length()
                JSONObject("""{"list":[]}""")
            },
            parseMedia = { null },
            baseUrl = base,
        ).getFolders()
        // 121 folders grouped by parent: root (1 name) + 120 children (3 batches of 50/50/20)
        assertEquals(listOf(1, 20, 50, 50), batches.sorted())
    }

    private fun JSONArray.toStrings(): List<String> =
        (0 until length()).map { getString(it) }
}

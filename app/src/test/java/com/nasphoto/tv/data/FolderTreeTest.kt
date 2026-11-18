package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.NasConnection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件夹浏览只显示主目录的回归测试：目录树必须被完整展开成可显示的文件夹列表。 */
class FolderTreeTest {
    private val repo = FnOsMediaRepository(NasConnection(host = "nas.test"))

    @Test
    fun watchedRootsKeepTheirRealCounts() {
        val arr = JSONArray(
            """[{"folderId":"1","folderPath":"/Photos","photoCount":4839,"videoCount":455}]""",
        )
        val folders = repo.parseFolderNodes(arr, parentId = "", depth = 0)
        assertEquals(1, folders.size)
        assertEquals("1", folders[0].id)
        assertEquals("/Photos", folders[0].path)
        assertEquals(4839, folders[0].photoCount)
        assertEquals(455, folders[0].videoCount)
    }

    @Test
    fun nestedChildrenAreFlattenedWithParentAndDepth() {
        val arr = JSONArray(
            """[{
                "folderId":"1","folderPath":"/Photos",
                "children":[
                    {"folderId":"2","folderPath":"/Photos/2017","children":[
                        {"folderId":"3","folderPath":"/Photos/2017/夏天"}
                    ]}
                ]
            }]""",
        )
        val out = ArrayList<com.nasphoto.tv.data.model.NasFolder>()
        repo.flattenFolderNodes(arr, "", 0, HashSet(), out)
        assertEquals(listOf("/Photos", "/Photos/2017", "/Photos/2017/夏天"), out.map { it.path })
        assertEquals(listOf(0, 1, 2), out.map { it.depth })
        assertEquals("", out[0].parentId)
        assertEquals("1", out[1].parentId)
        assertEquals("2", out[2].parentId)
    }

    @Test
    fun mediaEntriesInsideFolderListingAreIgnored() {
        val arr = JSONArray(
            """[
                {"folderId":"2","folderPath":"/Photos/孩子","isDir":1},
                {"id":99,"fileName":"IMG_0001.jpg","filePath":"/Photos/孩子/IMG_0001.jpg","category":"photo"}
            ]""",
        )
        val folders = repo.parseFolderNodes(arr, parentId = "1", depth = 1)
        assertEquals(1, folders.size)
        assertEquals("/Photos/孩子", folders[0].path)
    }

    @Test
    fun duplicatePathsAreVisitedOnlyOnce() {
        val arr = JSONArray(
            """[
                {"folderId":"1","folderPath":"/Photos","children":[
                    {"folderId":"1","folderPath":"/Photos/"}
                ]}
            ]""",
        )
        val out = ArrayList<com.nasphoto.tv.data.model.NasFolder>()
        repo.flattenFolderNodes(arr, "", 0, HashSet(), out)
        assertEquals(1, out.size)
    }

    @Test
    fun childWithoutFullPathGetsPathFromParent() {
        // getFileList 的目录项可能只有 name/isDir，没有 folderPath：
        // 必须用父目录路径拼出完整路径，否则点进去按 folderPath 查不到照片。
        val arr = JSONArray("""[{"isDir":1,"name":"DJI_001","id":37}]""")
        val folders = repo.parseFolderNodes(arr, parentId = "1", depth = 1, parentPath = "/vol1/1000/Photos")
        assertEquals("/vol1/1000/Photos/DJI_001", folders.single().path)
        assertEquals("DJI_001", folders.single().name)
    }

    @Test
    fun multipleChildArrayNamesAreRecognised() {
        val json = JSONObject(
            """{"subfolders":[{"folderPath":"/A","isFolder":true}],"items":[{"folderPath":"/B","type":"dir"}]}""",
        )
        assertTrue(json.optJSONArray("subfolders")!!.length() == 1)
        assertTrue(JSONObject("""{"isDir":1}""").looksLikeFolder())
        assertTrue(JSONObject("""{"folderId":"7"}""").looksLikeFolder())
    }
}

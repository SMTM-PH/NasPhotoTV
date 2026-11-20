package com.nasphoto.tv.ui.library

import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.NasFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件夹「逐层浏览」的纯逻辑回归：
 * 根 / 子 / 孙目录必须按 parentId 分层，不能再平铺成一个网格。
 */
class FolderHierarchyTest {

    /** /Photos 与 /Video 两个扫描根目录，Photos 下 3 个子目录，其中一个还有孙目录。 */
    private val tree = listOf(
        folder("path:/vol1/1000/Photos", "Photos", parent = "", depth = 0, 4408, 455),
        folder("path:/vol1/1000/Video", "Video", parent = "", depth = 0, 142, 313),
        folder("path:/vol1/1000/Photos/PhotoLibrary", "PhotoLibrary", parent = "path:/vol1/1000/Photos", depth = 1),
        folder("path:/vol1/1000/Photos/MobileBackup", "MobileBackup", parent = "path:/vol1/1000/Photos", depth = 1),
        folder("path:/vol1/1000/Photos/DJI_001", "DJI_001", parent = "path:/vol1/1000/Photos", depth = 1),
        folder("path:/vol1/1000/Photos/DJI_001/夏天", "夏天", parent = "path:/vol1/1000/Photos/DJI_001", depth = 2),
    )

    private val albums = tree.map(::folderAlbum)
    private val hierarchy = FolderHierarchy(albums)

    @Test
    fun mappingKeepsHierarchyIdsAndCounts() {
        val root = albums.first()
        assertEquals("folder:path:/vol1/1000/Photos", root.id)
        assertEquals("", root.parentId)
        assertEquals(0, root.depth)
        assertEquals(4863, root.itemCount)
        assertEquals(AlbumType.FOLDER, root.type)
        assertEquals("/vol1/1000/Photos", root.subtitle)

        val child = albums.first { it.name == "DJI_001" }
        assertEquals("folder:path:/vol1/1000/Photos", child.parentId)
        assertEquals(1, child.depth)
        val grandchild = albums.first { it.name == "夏天" }
        assertEquals(child.id, grandchild.parentId)
        assertEquals(2, grandchild.depth)
    }

    @Test
    fun rootLevelListsOnlyScanRoots() {
        assertEquals(listOf("Photos", "Video"), hierarchy.childrenOf(null).map { it.name })
        assertFalse(hierarchy.childrenOf(null).any { it.name == "夏天" })
    }

    @Test
    fun eachLevelListsOnlyDirectChildren() {
        val photosId = "folder:path:/vol1/1000/Photos"
        assertEquals(
            listOf("PhotoLibrary", "MobileBackup", "DJI_001"),
            hierarchy.childrenOf(photosId).map { it.name },
        )
        val djiId = "folder:path:/vol1/1000/Photos/DJI_001"
        assertEquals(listOf("夏天"), hierarchy.childrenOf(djiId).map { it.name })
        assertTrue(hierarchy.childrenOf("folder:path:/vol1/1000/Photos/DJI_001/夏天").isEmpty())
    }

    @Test
    fun hasChildrenDistinguishesBranchFromLeaf() {
        assertTrue(hierarchy.hasChildren("folder:path:/vol1/1000/Photos"))
        assertTrue(hierarchy.hasChildren("folder:path:/vol1/1000/Photos/DJI_001"))
        assertFalse(hierarchy.hasChildren("folder:path:/vol1/1000/Photos/PhotoLibrary"))
        assertFalse(hierarchy.hasChildren("folder:path:/vol1/1000/Video"))
        assertFalse(hierarchy.hasChildren("folder:missing"))
    }

    @Test
    fun parentOfWalksUpOneLevelAndStopsAtRoots() {
        assertEquals(
            "folder:path:/vol1/1000/Photos",
            hierarchy.parentOf("folder:path:/vol1/1000/Photos/DJI_001"),
        )
        assertNull(hierarchy.parentOf("folder:path:/vol1/1000/Photos"))
        assertNull(hierarchy.parentOf(null))
        assertNull(hierarchy.parentOf("folder:missing"))
    }

    @Test
    fun breadcrumbListsAncestorsFromRootToCurrent() {
        assertEquals(emptyList<Album>(), hierarchy.breadcrumb(null))
        assertEquals(listOf("Photos"), hierarchy.breadcrumb("folder:path:/vol1/1000/Photos").map { it.name })
        assertEquals(
            listOf("Photos", "DJI_001", "夏天"),
            hierarchy.breadcrumb("folder:path:/vol1/1000/Photos/DJI_001/夏天").map { it.name },
        )
    }

    @Test
    fun cyclicParentChainDoesNotHang() {
        val a = folder("path:/A", "A", parent = "path:/B", depth = 1)
        val b = folder("path:/B", "B", parent = "path:/A", depth = 1)
        val cyclic = FolderHierarchy(listOf(a, b).map(::folderAlbum))
        assertEquals(2, cyclic.breadcrumb("folder:path:/A").size)
    }

    @Test
    fun orphanFoldersStayReachableFromRootLevel() {
        // 父目录缺失（刷新中途 / 旧缓存）不能让目录永远进不去。
        val orphan = folder("path:/Photos/Orphan", "Orphan", parent = "path:/Missing", depth = 2)
        val h = FolderHierarchy((tree + orphan).map(::folderAlbum))
        assertTrue(h.childrenOf(null).any { it.name == "Orphan" })
        assertEquals(3, h.childrenOf(null).size) // 2 个扫描根目录 + 1 个孤儿目录
    }

    @Test
    fun emptyFolderListIsSafe() {
        val empty = FolderHierarchy(emptyList())
        assertTrue(empty.childrenOf(null).isEmpty())
        assertTrue(empty.childrenOf("folder:anything").isEmpty())
        assertTrue(empty.breadcrumb("folder:anything").isEmpty())
        assertNull(empty.folder("folder:anything"))
    }

    private fun folder(
        id: String,
        name: String,
        parent: String,
        depth: Int,
        photos: Int = 0,
        videos: Int = 0,
    ) = NasFolder(
        id = id,
        path = id.removePrefix("path:"),
        name = name,
        photoCount = photos,
        videoCount = videos,
        parentId = parent,
        depth = depth,
    )
}

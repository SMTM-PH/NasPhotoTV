package com.nasphoto.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.model.MediaItem

/**
 * ================= 本地收藏（星标） =================
 *
 * fnOS 协议里没有收藏增删接口，所以收藏先落在本机 SharedPreferences：
 * 跨启动保留，但多设备不同步。等 NAS 侧接口确认后，在 [toggle] 里补双写即可，
 * UI 不需要改。
 *
 * [ids] 是不可变集合 + Compose 快照状态：UI 读它就会在切换时重组，
 * 也可以直接当成 `remember` 的 key 用。
 */
object FavoritesStore {
    private const val PREFS = "nas_photo_tv_favorites"
    private const val KEY_IDS = "ids"

    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    fun restore() {
        val stored = prefs()?.getStringSet(KEY_IDS, emptySet()).orEmpty()
        ids = stored.toSet()
    }

    fun isFavorite(id: String): Boolean = id in ids

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
        persist()
    }

    private fun persist() {
        prefs()?.edit()?.putStringSet(KEY_IDS, ids)?.apply()
    }

    private fun prefs(): android.content.SharedPreferences? {
        val context = AppContextHolder.context ?: return null
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }
}

/** NAS 自己带 isCollect 时与本地星标取并集 */
fun List<MediaItem>.favoriteItems(): List<MediaItem> = filter { it.starred || it.id in FavoritesStore.ids }

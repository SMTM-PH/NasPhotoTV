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
 *
 * 多账号：星标按账号隔离（key 前缀 `ids_<accountId>`）。照片 id 在不同账号下会撞
 * （同一台 NAS 的自增 id 空间是共享的），不隔离的话 B 账号会看到 A 账号的星标。
 */
object FavoritesStore {
    private const val PREFS = "nas_photo_tv_favorites"
    private const val KEY_IDS = "ids"
    private fun keyFor(accountId: String) = if (accountId.isBlank()) KEY_IDS else "ids_$accountId"

    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    /** 当前星标属于哪个账号（toggle 时按它写回，避免写到别人的分区里） */
    private var scopeAccountId: String = ""

    /** 启动时恢复：优先读单账号时代的旧 key，账号加载完成后会被 [load] 覆盖 */
    fun restore() {
        ids = prefs()?.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()
        scopeAccountId = ""
    }

    /** 换账号（或首次进入）时切到该账号的星标分区 */
    fun load(accountId: String) {
        scopeAccountId = accountId
        val p = prefs()
        val scoped = p?.getStringSet(keyFor(accountId), null)
        if (scoped != null) {
            ids = scoped.toSet()
            return
        }
        // 第一次按账号分区：把单账号时代攒下的星标搬给当前账号，用户的收藏不会凭空消失。
        // 只在 scoped key 完全不存在时搬一次（值为空集也算「已经搬过」，不会重复覆盖）。
        val legacy = p?.getStringSet(KEY_IDS, emptySet()).orEmpty()
        ids = legacy.toSet()
        if (legacy.isNotEmpty()) p?.edit()?.putStringSet(keyFor(accountId), legacy)?.apply()
    }

    fun isFavorite(id: String): Boolean = id in ids

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
        persist()
    }

    private fun persist() {
        prefs()?.edit()?.putStringSet(keyFor(scopeAccountId), ids)?.apply()
    }

    private fun prefs(): android.content.SharedPreferences? {
        val context = AppContextHolder.context ?: return null
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }
}

/** NAS 自己带 isCollect 时与本地星标取并集 */
fun List<MediaItem>.favoriteItems(): List<MediaItem> = filter { it.starred || it.id in FavoritesStore.ids }

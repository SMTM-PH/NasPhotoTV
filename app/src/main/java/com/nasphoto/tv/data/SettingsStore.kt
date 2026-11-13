package com.nasphoto.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.NasAccount
import com.nasphoto.tv.data.model.NasConnection
import com.nasphoto.tv.data.model.PlaybackSettings
import com.nasphoto.tv.data.model.ScreensaverSettings

/**
 * ================= 全局设置状态（data-engineer 维护） =================
 *
 * 用 Compose 的 `mutableStateOf` 持久在 [MutableState] 里，所以：
 *   - 读取 `SettingsStore.screensaver` 会在 Composable 中自动触发重组（getter 直接返回 value）
 *   - 写入只能通过 `updateXxx { it.copy(...) }`，保证单线程一致
 *
 * 使用示例（设置页）：
 * ```kotlin
 * val store = SettingsStore
 * val ss = store.screensaver                    // 可观察
 * store.updateScreensaver { it.copy(kenBurns = !it.kenBurns) }
 * store.cycleScreensaverBackground()            // 照片轮播 → 极光动态 → 纯黑极简 → …
 * ```
 *
 * 【持久化】改完就能存住，不需要用户"保存"：
 *   - 外壳 `NasPhotoTvApp` 里有一条 `LaunchedEffect(screensaver, playback, nas) { persist() }`，
 *     任何一次 `updateXxx` 都会在下一帧写进 SharedPreferences；
 *   - `MainActivity.onCreate` 调 [restore]，冷启动把上次的值读回来。
 * 早期原型「改了不存、重启回默认」的注释已经过时，别再按那个改。
 */
object SettingsStore {

    // ---------- 默认值 ----------
    val defaultScreensaver = ScreensaverSettings()
    val defaultPlayback = PlaybackSettings()
    val defaultNas: NasConnection = NasConnection()

    // ---------- 状态 ----------
    var screensaver: ScreensaverSettings by mutableStateOf(defaultScreensaver)
        private set

    var playback: PlaybackSettings by mutableStateOf(defaultPlayback)
        private set

    var nas: NasConnection by mutableStateOf(defaultNas)
        private set

    /**
     * 「测试连接」的结果：null = 还没测过。
     * 设置页可以读它显示「已连接 · 12ms」/「连接失败」。
     */
    var nasLatencyMs: Int? by mutableStateOf<Int?>(null)
        private set

    // ---------- 写入口 ----------

    fun updateScreensaver(transform: (ScreensaverSettings) -> ScreensaverSettings) {
        screensaver = transform(screensaver)
    }

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) {
        playback = transform(playback)
    }

    fun updateNas(transform: (NasConnection) -> NasConnection) {
        val next = transform(nas)
        invalidateDisplayScopeForIdentityChange(nas, next)
        nas = next
    }

    /**
     * 当前账号明确允许在客厅自动展示的相册。
     * 未授权、无有效账号或切换账号都返回空；调用方还须与当前真实相册取交集。
     * 相册为空/已删除时绝不能回退到最近照片。
     */
    val displayAlbumIds: Set<String>
        get() {
            val accountId = nas.activeAccountId
            if (accountId.isBlank() || nas.account(accountId) == null) return emptySet()
            return if (screensaver.displayOwnerAccountId == accountId) screensaver.displayAlbumIds else emptySet()
        }

    /** 逐相册明确授权；新账号首次勾选从空白名单开始。 */
    fun setDisplayAlbumEnabled(albumId: String, enabled: Boolean) {
        val accountId = nas.activeAccountId
        if (accountId.isBlank() || nas.account(accountId) == null || albumId.isBlank()) return
        val selected = displayAlbumIds
        val next = if (enabled) selected + albumId else selected - albumId
        updateScreensaver { it.copy(displayOwnerAccountId = accountId, displayAlbumIds = next) }
    }

    /** 撤销当前账号的全部客厅展示授权，不影响正常图库浏览。 */
    fun clearDisplayAlbums() {
        val accountId = nas.activeAccountId
        if (accountId.isBlank() || nas.account(accountId) == null) return
        updateScreensaver { it.copy(displayOwnerAccountId = accountId, displayAlbumIds = emptySet()) }
    }

    /** 同一个本地账号 id 换用户名/服务器，也必须重新明确选择展示范围。 */
    private fun invalidateDisplayScopeForIdentityChange(previous: NasConnection, next: NasConnection) {
        val owner = screensaver.displayOwnerAccountId
        if (owner.isBlank()) return
        val previousAccount = previous.account(owner)
        val nextAccount = next.account(owner)
        val endpointChanged = previous.host.trim().lowercase() != next.host.trim().lowercase() || previous.port != next.port
        if (endpointChanged || nextAccount == null || previousAccount?.username != nextAccount.username) {
            updateScreensaver { it.copy(displayOwnerAccountId = "", displayAlbumIds = emptySet()) }
        }
    }

    // ---------- 多账号（同一台 NAS 的多个飞牛账号） ----------

    /**
     * 切换当前账号。切换后 UI 必须重建仓库并清空缓存 —— [SettingsStore] 不做这件事，
     * 由外壳（NasPhotoTvApp）监听 activeAccountId 变化来收尾。
     * 返回 true 表示真的换了账号。
     */
    fun switchAccount(accountId: String): Boolean {
        if (accountId == nas.activeAccountId || nas.account(accountId) == null) return false
        nas = nas.withActiveAccount(accountId).copy(connected = false, latencyMs = 0)
        nasLatencyMs = null
        return true
    }

    /** 新增一个空账号并切过去（设置页「添加账号」） */
    fun addAccount(): NasAccount {
        val account = NasAccount(id = NasAccount.newId(nas.accounts))
        nas = nas.withAccount(account).withActiveAccount(account.id).copy(connected = false)
        nasLatencyMs = null
        return account
    }

    /** 删除账号；至少保留一个（最后一个不允许删） */
    fun removeAccount(accountId: String): Boolean {
        if (nas.accounts.size <= 1) return false
        if (nas.account(accountId) == null) return false
        nas = nas.withoutAccount(accountId).copy(connected = false)
        nasLatencyMs = null
        // 账号 id 将来可能重用；被删除账号的授权不得转交给新账号。
        if (screensaver.displayOwnerAccountId == accountId) {
            updateScreensaver { it.copy(displayOwnerAccountId = "", displayAlbumIds = emptySet()) }
        }
        return true
    }

    /** 改某个账号的字段（不改变当前账号） */
    fun updateAccount(accountId: String, transform: (NasAccount) -> NasAccount) {
        val account = nas.account(accountId) ?: return
        val next = nas.withAccount(transform(account))
        invalidateDisplayScopeForIdentityChange(nas, next)
        nas = next
    }

    /** 改当前账号的字段 */
    fun updateActiveAccount(transform: (NasAccount) -> NasAccount) {
        val next = nas.updateActiveAccount(transform)
        invalidateDisplayScopeForIdentityChange(nas, next)
        nas = next
    }

    /** 记录一次连接测试结果；[latencyMs] 为 null 表示失败 */
    fun reportNasResult(latencyMs: Int?) {
        nasLatencyMs = latencyMs
        nas = nas.copy(connected = latencyMs != null, latencyMs = latencyMs ?: nas.latencyMs)
    }

    /** 全部回到默认值 */
    fun reset() {
        screensaver = defaultScreensaver
        playback = defaultPlayback
        nas = defaultNas
        nasLatencyMs = null
    }

    // ---------- 「下一档」便捷方法（设置页遥控器一键切换） ----------

    /** 背景模式下一档：照片轮播 → 极光动态 → 纯黑极简 → 照片轮播 */
    fun cycleScreensaverBackground() {
        val values = ScreensaverSettings.BackgroundStyle.entries
        updateScreensaver { s ->
            val next = values[(s.background.ordinal + 1) % values.size]
            s.copy(background = next)
        }
    }

    /** 时钟样式下一档：巨幕时钟 → 翻页数字 → 纤细极简 → 极简小钟 → 日期日历 → 巨幕时钟 */
    fun cycleClockStyle(reverse: Boolean = false) {
        val values = ScreensaverSettings.ClockStyle.entries
        updateScreensaver { s ->
            val delta = if (reverse) -1 else 1
            val next = values[(s.clockStyle.ordinal + delta + values.size) % values.size]
            s.copy(clockStyle = next)
        }
    }

    /** 画面模式下一档：时钟待机 → 纯照片 → 时钟待机 */
    fun cycleScreensaverMode() {
        val values = ScreensaverSettings.Mode.entries
        updateScreensaver { s ->
            s.copy(mode = values[(s.mode.ordinal + 1) % values.size])
        }
    }

    /** 轮播来源下一档：人物 → 最近 → 随机 → 相册 → 人物 */
    fun cycleScreensaverSource(albums: List<Album>, reverse: Boolean = false) {
        val values = ScreensaverSettings.PhotoSource.entries
        updateScreensaver { s ->
            val delta = if (reverse) -1 else 1
            val next = values[(s.photoSource.ordinal + delta + values.size) % values.size]
            setSourceInternal(s, next, albums)
        }
    }

    /** 直接选中某个轮播来源；选「相册」时自动补上当前/第一个相册 */
    fun setScreensaverSource(source: ScreensaverSettings.PhotoSource, albums: List<Album>) {
        updateScreensaver { setSourceInternal(it, source, albums) }
    }

    private fun setSourceInternal(
        s: ScreensaverSettings,
        source: ScreensaverSettings.PhotoSource,
        albums: List<Album>,
    ): ScreensaverSettings {
        if (source != ScreensaverSettings.PhotoSource.ALBUM) return s.copy(photoSource = source)
        val album = albums.firstOrNull { it.id == s.albumId } ?: albums.firstOrNull()
        return if (album == null) {
            s.copy(photoSource = source)
        } else {
            s.copy(photoSource = source, albumId = album.id, albumName = album.name)
        }
    }

    /** 相册来源下一档；没有相册时保持原样 */
    fun cycleScreensaverAlbum(albums: List<Album>) {
        if (albums.isEmpty()) return
        updateScreensaver { s ->
            val index = albums.indexOfFirst { it.id == s.albumId }
            val next = albums[(index + 1).mod(albums.size)]
            s.copy(albumId = next.id, albumName = next.name)
        }
    }

    /** 屏保停留时长下一档：5 → 10 → 15 → 30 → 60 秒循环 */
    fun cyclePhotoInterval() {
        val steps = listOf(5, 10, 15, 30, 60)
        updateScreensaver { s ->
            val next = steps.firstOrNull { it > s.photoIntervalSec } ?: steps.first()
            s.copy(photoIntervalSec = next)
        }
    }

    /** 待机进入时间下一档：30 秒 → 1/2/5/10/30 分钟循环 */
    fun cycleIdleTimeout() {
        val steps = listOf(30, 60, 120, 300, 600, 1800)
        updateScreensaver { s ->
            val next = steps.firstOrNull { it > s.idleTimeoutSec } ?: steps.first()
            s.copy(idleTimeoutSec = next)
        }
    }

    // ---------- 展示用格式化 ----------

    /** 「1 分 30 秒」/「10 秒」 */
    fun formatIdleTimeout(sec: Int = screensaver.idleTimeoutSec): String = when {
        sec < 60 -> "$sec 秒"
        sec % 60 == 0 -> "${sec / 60} 分钟"
        else -> "${sec / 60} 分 ${sec % 60} 秒"
    }

    /** 「10 秒」/「1 分钟」 */
    fun formatInterval(sec: Int = screensaver.photoIntervalSec): String =
        if (sec < 60) "$sec 秒" else if (sec % 60 == 0) "${sec / 60} 分钟" else "${sec / 60} 分 ${sec % 60} 秒"

    // ---------- 轻量持久化（SharedPreferences，可选） ----------

    private const val PREF_NAME = "nas_photo_tv_settings"

    /// 上下文未就绪时直接跳过，不抛异常
    private fun prefs(): android.content.SharedPreferences? {
        val ctx = com.nasphoto.tv.data.AppContextHolder.context ?: return null
        return ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
    }

    /** 把当前设置写入磁盘（在界面层改动后调用，或由外壳在切后台时调用） */
    fun persist() {
        val p = prefs() ?: return
        val e = p.edit()
        e.putString("nas_name", nas.name)
        e.putString("nas_host", nas.host)
        e.putInt("nas_port", nas.port)
        e.putString("nas_active", nas.activeAccountId)
        // 账号列表：按序展开成 count + 下标字段（SharedPreferences 不适合存 JSON/对象）
        e.putInt("nas_account_count", nas.accounts.size)
        nas.accounts.forEachIndexed { index, account ->
            e.putString("nas_acc${index}_id", account.id)
            e.putString("nas_acc${index}_user", account.username)
            e.putString("nas_acc${index}_pswd", account.password)
            e.putString("nas_acc${index}_label", account.label)
            e.putString("nas_acc${index}_access", account.accessCode)
        }
        // 兼容旧版本存档用的一份「当前账号」扁平字段
        e.putString("nas_user", nas.username)
        e.putString("nas_password", nas.password)
        e.putString("nas_access", nas.accessCode)
        e.putBoolean("ss_enabled", screensaver.enabled)
        e.putInt("ss_idle", screensaver.idleTimeoutSec)
        e.putInt("ss_interval", screensaver.photoIntervalSec)
        e.putString("ss_clock", screensaver.clockStyle.name)
        e.putString("ss_bg", screensaver.background.name)
        e.putString("ss_mode", screensaver.mode.name)
        e.putString("ss_source", screensaver.photoSource.name)
        e.putString("ss_album_id", screensaver.albumId)
        e.putString("ss_album_name", screensaver.albumName)
        e.putBoolean("ss_date", screensaver.showDate)
        e.putBoolean("ss_dim", screensaver.nightDim)
        e.putBoolean("ss_kenburns", screensaver.kenBurns)
        e.putBoolean("ss_music", screensaver.musicEnabled)
        e.putFloat("ss_music_vol", screensaver.musicVolume)
        e.putString("display_owner_account", screensaver.displayOwnerAccountId)
        e.putStringSet("display_album_ids", screensaver.displayAlbumIds.toSet())
        e.putBoolean("pb_next", playback.autoPlayNext)
        e.putBoolean("pb_loop", playback.loopMode)
        e.apply()
    }

    /** 从磁盘恢复；没有存档时保持默认值 */
    fun restore() {
        val p = prefs() ?: return
        if (!p.contains("ss_idle") && !p.contains("nas_host")) return
        val restoredAccounts = restoreAccounts(p)
        val storedActive = p.getString("nas_active", "") ?: ""
        nas = defaultNas.copy(
            name = p.getString("nas_name", defaultNas.name) ?: defaultNas.name,
            host = p.getString("nas_host", defaultNas.host) ?: defaultNas.host,
            port = p.getInt("nas_port", defaultNas.port),
            accounts = restoredAccounts,
            // 存档里的 active id 可能已被删：退回第一个账号
            activeAccountId = restoredAccounts.firstOrNull { it.id == storedActive }?.id
                ?: restoredAccounts.firstOrNull()?.id.orEmpty(),
        )
        screensaver = defaultScreensaver.copy(
            enabled = p.getBoolean("ss_enabled", defaultScreensaver.enabled),
            idleTimeoutSec = p.getInt("ss_idle", 60),
            photoIntervalSec = p.getInt("ss_interval", 10),
            clockStyle = enumOrDefault(p.getString("ss_clock", null), ScreensaverSettings.ClockStyle.CINEMA),
            background = enumOrDefault(p.getString("ss_bg", null), ScreensaverSettings.BackgroundStyle.PHOTO),
            mode = enumOrDefault(p.getString("ss_mode", null), ScreensaverSettings.Mode.CLOCK),
            photoSource = enumOrDefault(
                p.getString("ss_source", null),
                ScreensaverSettings.PhotoSource.RECENT,
            ),
            albumId = p.getString("ss_album_id", "") ?: "",
            albumName = p.getString("ss_album_name", "") ?: "",
            showDate = p.getBoolean("ss_date", true),
            nightDim = p.getBoolean("ss_dim", true),
            kenBurns = p.getBoolean("ss_kenburns", true),
            musicEnabled = p.getBoolean("ss_music", true),
            musicVolume = p.getFloat("ss_music_vol", 0.7f),
            // 老版本未保存展示白名单：升级后先不授权，等用户逐相册选择。
            displayOwnerAccountId = p.getString("display_owner_account", "").orEmpty(),
            displayAlbumIds = p.getStringSet("display_album_ids", emptySet())?.toSet().orEmpty(),
        )
        playback = defaultPlayback.copy(
            autoPlayNext = p.getBoolean("pb_next", true),
            loopMode = p.getBoolean("pb_loop", false),
        )
    }

    /** 存档里的枚举名可能已失效（改了枚举），安全回退到默认值 */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        if (name == null) fallback else runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    /**
     * 读账号列表。
     * 新存档读 `nas_account_count` + 下标字段；老存档（只有 nas_user/nas_password）
     * 走 [NasConnection.of] 的兼容路径，保证升级后老用户的账号还在、能直接登录。
     */
    private fun restoreAccounts(
        p: android.content.SharedPreferences,
    ): List<NasAccount> {
        val count = p.getInt("nas_account_count", 0)
        if (count > 0) {
            val list = buildList {
                for (index in 0 until count) {
                    val id = p.getString("nas_acc${index}_id", "") ?: ""
                    if (id.isBlank()) continue
                    add(
                        NasAccount(
                            id = id,
                            username = p.getString("nas_acc${index}_user", "") ?: "",
                            password = p.getString("nas_acc${index}_pswd", "") ?: "",
                            label = p.getString("nas_acc${index}_label", "") ?: "",
                            accessCode = p.getString("nas_acc${index}_access", "") ?: "",
                        ),
                    )
                }
            }
            if (list.isNotEmpty()) return list
        }
        // 老存档兼容
        val legacyUser = p.getString("nas_user", "") ?: ""
        val legacyPassword = p.getString("nas_password", "") ?: ""
        if (legacyUser.isBlank() && legacyPassword.isBlank()) return emptyList()
        return listOf(
            NasAccount(
                id = "acc-1",
                username = legacyUser,
                password = legacyPassword,
                accessCode = p.getString("nas_access", "") ?: "",
            ),
        )
    }
}

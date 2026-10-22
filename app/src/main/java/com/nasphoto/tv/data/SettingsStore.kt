package com.nasphoto.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.model.Album
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
 * 注意：本对象默认不做磁盘持久化（原型重启回到默认值）。
 * 若需要保留：界面层改动后调用 [persist]，App 启动时调用 [restore] 即可。
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
        nas = transform(nas)
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
        p.edit()
            .putString("nas_name", nas.name)
            .putString("nas_host", nas.host)
            .putInt("nas_port", nas.port)
            .putString("nas_user", nas.username)
            .putString("nas_password", nas.password)
            .putString("nas_access", nas.accessCode)
            .putBoolean("ss_enabled", screensaver.enabled)
            .putInt("ss_idle", screensaver.idleTimeoutSec)
            .putInt("ss_interval", screensaver.photoIntervalSec)
            .putString("ss_clock", screensaver.clockStyle.name)
            .putString("ss_bg", screensaver.background.name)
            .putString("ss_mode", screensaver.mode.name)
            .putString("ss_source", screensaver.photoSource.name)
            .putString("ss_album_id", screensaver.albumId)
            .putString("ss_album_name", screensaver.albumName)
            .putBoolean("ss_date", screensaver.showDate)
            .putBoolean("ss_dim", screensaver.nightDim)
            .putBoolean("ss_kenburns", screensaver.kenBurns)
            .putBoolean("pb_next", playback.autoPlayNext)
            .putBoolean("pb_loop", playback.loopMode)
            .apply()
    }

    /** 从磁盘恢复；没有存档时保持默认值 */
    fun restore() {
        val p = prefs() ?: return
        if (!p.contains("ss_idle") && !p.contains("nas_host")) return
        nas = defaultNas.copy(
            name = p.getString("nas_name", defaultNas.name) ?: defaultNas.name,
            host = p.getString("nas_host", defaultNas.host) ?: defaultNas.host,
            port = p.getInt("nas_port", defaultNas.port),
            username = p.getString("nas_user", defaultNas.username) ?: defaultNas.username,
            password = p.getString("nas_password", defaultNas.password) ?: defaultNas.password,
            accessCode = p.getString("nas_access", defaultNas.accessCode) ?: defaultNas.accessCode,
        )
        screensaver = defaultScreensaver.copy(
            enabled = p.getBoolean("ss_enabled", true),
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
        )
        playback = defaultPlayback.copy(
            autoPlayNext = p.getBoolean("pb_next", true),
            loopMode = p.getBoolean("pb_loop", false),
        )
    }

    /** 存档里的枚举名可能已失效（改了枚举），安全回退到默认值 */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        if (name == null) fallback else runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}

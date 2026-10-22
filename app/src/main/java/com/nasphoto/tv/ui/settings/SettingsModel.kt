package com.nasphoto.tv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.data.fn.FnOsApi
import com.nasphoto.tv.data.fn.toFnOsAccess

/**
 * ================= 设置页的地址与写操作 =================
 *
 * 1. [SettingsGroup] / [SettingsKey]：左栏的“地址”，右栏靠 [SettingsKey] 决定画哪块面板；
 * 2. [SettingsActions]：所有写操作集中于此，只走 `SettingsStore.updateXxx {}`；
 * 3. [SettingsFeedback]：测试连接 / 恢复默认二次确认 等纯 UI 瞬时状态，不进 Store。
 */

enum class SettingsGroup(val title: String) {
    NAS("NAS 连接"),
    SCREENSAVER("屏保与待机"),
    PLAYBACK("视频播放"),
    ABOUT("关于"),
}

/** 左栏每一行的唯一标识 */
enum class SettingsKey(
    val group: SettingsGroup,
    val title: String,
    val hint: String,
) {
    NAS_DEVICE(SettingsGroup.NAS, "名称", "显示在顶栏与本机的 NAS 名称"),
    NAS_HOST(SettingsGroup.NAS, "地址", "NAS 的局域网 IP 或 DDNS 域名"),
    NAS_PORT(SettingsGroup.NAS, "端口", "飞牛 Web HTTPS 端口，默认 5667"),
    NAS_ACCOUNT(SettingsGroup.NAS, "账号", "飞牛账号"),
    NAS_PASSWORD(SettingsGroup.NAS, "密码", "账号密码，仅保存在这台电视上"),
    NAS_ACCESS_CODE(SettingsGroup.NAS, "访问码", "飞牛「访问码」网关，未启用可留空"),
    NAS_TEST(SettingsGroup.NAS, "测试连接", "真实登录 + 签名请求，验证地址与凭据"),
    NAS_REFRESH(SettingsGroup.NAS, "刷新媒体库", "重新从 NAS 拉取时间线与相册"),

    SS_ENABLED(SettingsGroup.SCREENSAVER, "屏保", "关闭后空闲也不再进入待机显示"),
    SS_IDLE(SettingsGroup.SCREENSAVER, "静止后进入", "多久没有操作就进入屏保"),
    SS_MODE(SettingsGroup.SCREENSAVER, "屏保画面", "时钟待机 / 纯照片；屏保里 ← → 换样式"),
    SS_SOURCE(SettingsGroup.SCREENSAVER, "轮播内容", "人物 / 最近 / 随机 / 相册；人物需 NAS 已开启 AI 索引"),
    SS_ALBUM(SettingsGroup.SCREENSAVER, "相册", "「相册」来源时只轮播这个相册"),
    SS_INTERVAL(SettingsGroup.SCREENSAVER, "照片停留", "每张照片播放多久"),
    SS_CLOCK(SettingsGroup.SCREENSAVER, "时钟样式", "五种表盘，切换实时预览（纯照片模式下不显示）"),
    SS_BACKGROUND(SettingsGroup.SCREENSAVER, "背景", "时钟待机的背景：照片轮播 / 极光动态 / 纯黑极简"),
    SS_DATE(SettingsGroup.SCREENSAVER, "显示日期", "显示星期与日期"),
    SS_NIGHT_DIM(SettingsGroup.SCREENSAVER, "夜间自动调暗", "降低亮度，保护面板"),
    SS_KEN_BURNS(SettingsGroup.SCREENSAVER, "Ken Burns 动效", "照片缓慢推拉，防烧屏"),
    SS_PREVIEW(SettingsGroup.SCREENSAVER, "预览屏保", "全屏预览当前屏保"),

    PB_AUTOPLAY(SettingsGroup.PLAYBACK, "自动连播下一集", "播完自动播放列表里的下一个"),
    PB_LOOP(SettingsGroup.PLAYBACK, "循环播放", "单个视频播完从头再放"),

    ABOUT_VERSION(SettingsGroup.ABOUT, "版本", "当前版本"),
    ABOUT_ENGINE(SettingsGroup.ABOUT, "引擎", "渲染与状态框架"),
    ABOUT_DEVICE(SettingsGroup.ABOUT, "设备", "当前电视型号"),
    ABOUT_DESIGN(SettingsGroup.ABOUT, "设计说明", "这套 10-foot UI 的设计原则"),
    ABOUT_RESET(SettingsGroup.ABOUT, "恢复默认设置", "需要按两次确认"),
}

/** 左栏按组取行 */
val SettingsRowsByGroup: Map<SettingsGroup, List<SettingsKey>> =
    SettingsKey.entries.groupBy { it.group }

/**
 * 所有写操作的唯一入口。
 * SettingsStore 的 setter 是 private → 一律 `updateXxx { it.copy(...) }`。
 */
class SettingsActions(
    private val store: SettingsStore,
    private val feedback: SettingsFeedback,
    private val onPreviewScreensaver: () -> Unit,
) {

    // ---------------- NAS ----------------
    fun setName(value: String) = store.updateNas { it.copy(name = value.ifBlank { "Home-NAS" }) }

    fun setHost(value: String) {
        store.updateNas { it.copy(host = value.trim()) }
        feedback.invalidateNasTest()
    }

    fun setAccount(value: String) {
        store.updateNas { it.copy(username = value.trim()) }
        feedback.invalidateNasTest()
    }

    fun setPort(value: String) {
        val port = value.toIntOrNull()?.coerceIn(1, 65535) ?: return
        store.updateNas { it.copy(port = port) }
        feedback.invalidateNasTest()
    }

    fun setPassword(value: String) {
        store.updateNas { it.copy(password = value) }
        feedback.invalidateNasTest()
    }

    fun setAccessCode(value: String) {
        store.updateNas { it.copy(accessCode = value) }
        feedback.invalidateNasTest()
    }

    // ---------------- 屏保 ----------------
    fun toggleScreensaver() = store.updateScreensaver { it.copy(enabled = !it.enabled) }
    fun cycleIdleTimeout() = store.cycleIdleTimeout()
    fun cycleMode() = store.cycleScreensaverMode()
    fun cycleSource() = store.cycleScreensaverSource(MediaCache.albums)
    fun cycleAlbum() = store.cycleScreensaverAlbum(MediaCache.albums)
    fun setMode(mode: ScreensaverSettings.Mode) = store.updateScreensaver { it.copy(mode = mode) }
    fun setSource(source: ScreensaverSettings.PhotoSource) =
        store.setScreensaverSource(source, MediaCache.albums)
    fun cyclePhotoInterval() = store.cyclePhotoInterval()
    fun cycleClockStyle() = store.cycleClockStyle()
    fun cycleBackground() = store.cycleScreensaverBackground()
    fun toggleDate() = store.updateScreensaver { it.copy(showDate = !it.showDate) }
    fun toggleNightDim() = store.updateScreensaver { it.copy(nightDim = !it.nightDim) }
    fun toggleKenBurns() = store.updateScreensaver { it.copy(kenBurns = !it.kenBurns) }

    fun previewScreensaver() {
        // 屏保关着还点预览：顺手打开（用户意图明显是"我要看"）
        if (!store.screensaver.enabled) toggleScreensaver()
        onPreviewScreensaver()
    }

    // ---------------- 播放 ----------------
    fun toggleAutoPlayNext() = store.updatePlayback { it.copy(autoPlayNext = !it.autoPlayNext) }
    fun toggleLoop() = store.updatePlayback { it.copy(loopMode = !it.loopMode) }

    // ---------------- 关于 ----------------
    fun toggleDesign() {
        feedback.showDesignDetail = !feedback.showDesignDetail
    }

    /**
     * 恢复默认：ENTER 第 1 次 → 变成「再按一次确认」；3 秒内再按 → 真重置。
     * 期间焦点离开该行、或按了别的键，确认自动取消。
     */
    fun requestReset() {
        if (feedback.resetArmed) {
            store.reset()
            feedback.disarmReset()
            feedback.toast = "已恢复默认设置"
        } else {
            feedback.armReset()
        }
    }
}

/**
 * 设置页的瞬时状态（纯 UI，不进 SettingsStore）：
 *  - 测试连接：真实登录探测
 *  - 恢复默认：二次确认武装态（3 秒自动解除）
 */
class SettingsFeedback {
    var nasTesting by mutableStateOf(false)
        private set
    /** null = 还没测过 */
    var nasTestOk by mutableStateOf<Boolean?>(null)
        private set
    var nasTestLatency by mutableStateOf<Int?>(null)
        private set

    /** 成功时的实据（照片 / 视频数量） */
    var nasTestDetail by mutableStateOf<String?>(null)
        private set

    /** 失败原因（给右栏展示，左栏胶囊只写「连接失败」） */
    var nasTestError by mutableStateOf<String?>(null)
        private set

    var resetArmed by mutableStateOf(false)
        private set
    /** 底部提示（2 秒后由页面自动清空） */
    var toast by mutableStateOf<String?>(null)

    var showDesignDetail by mutableStateOf(false)

    /** 地址/账号/密码改动后旧的探测结果失效 */
    fun invalidateNasTest() {
        if (nasTesting) return
        nasTestOk = null
        nasTestLatency = null
        nasTestDetail = null
        nasTestError = null
    }

    /** 「测试连接」：真实链路（加密登录 + 签名 stat） */
    suspend fun runNasTest(store: SettingsStore) {
        if (nasTesting) return
        nasTesting = true
        nasTestOk = null
        nasTestLatency = null
        nasTestDetail = null
        nasTestError = null
        try {
            val nas = store.nas
            val api = FnOsApi(nas.toFnOsAccess())
            val result = try {
                api.testConnection()
            } finally {
                api.close()
            }
            nasTestOk = result.ok
            if (result.ok) {
                nasTestLatency = result.latencyMs
                nasTestDetail = buildString {
                    append("登录成功")
                    result.stats?.let { append(" · ${it.photoCount} 张照片 / ${it.videoCount} 个视频") }
                }
                store.reportNasResult(result.latencyMs)
            } else {
                nasTestError = result.error ?: "连接失败"
                store.reportNasResult(null)
            }
        } finally {
            nasTesting = false
        }
    }

    fun armReset() {
        resetArmed = true
    }

    fun disarmReset() {
        resetArmed = false
    }
}

/** 「测试连接」的结果文案（左栏胶囊） */
fun nasResultText(feedback: SettingsFeedback): String? = when {
    feedback.nasTesting -> "正在测试连接…"
    feedback.nasTestOk == true -> "连接正常 · ${feedback.nasTestLatency ?: 0}ms"
    feedback.nasTestOk == false -> "连接失败"
    else -> null
}

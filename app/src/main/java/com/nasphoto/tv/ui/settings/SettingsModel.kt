package com.nasphoto.tv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.NasAccount
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.data.fn.FnOsApi
import com.nasphoto.tv.data.fn.FnOsTwoFactorChallenge
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
    ACCOUNTS("账号切换"),
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
    NAS_ACCOUNT(SettingsGroup.NAS, "账号", "当前账号的飞牛用户名"),
    NAS_PASSWORD(SettingsGroup.NAS, "密码", "账号密码，仅保存在这台电视上"),
    NAS_ACCESS_CODE(SettingsGroup.NAS, "访问码", "飞牛「访问码」网关，未启用可留空"),
    NAS_TEST(SettingsGroup.NAS, "测试连接", "真实登录 + 签名请求，验证地址与凭据"),
    NAS_REFRESH(SettingsGroup.NAS, "刷新媒体库", "重新从 NAS 拉取时间线与相册"),

    ACC_CURRENT(SettingsGroup.ACCOUNTS, "当前账号", "这台 NAS 上保存了多个账号时，← → 直接切换"),
    ACC_LABEL(SettingsGroup.ACCOUNTS, "备注名", "只显示在本机，方便区分家人的账号"),
    ACC_ADD(SettingsGroup.ACCOUNTS, "添加账号", "同一台 NAS 再加一个飞牛账号，填完账号密码即可"),
    ACC_REMOVE(SettingsGroup.ACCOUNTS, "删除当前账号", "至少保留一个账号；删除后回到第一个账号"),

    SS_DISPLAY_ALBUMS(SettingsGroup.SCREENSAVER, "客厅展示相册", "用于首页回忆和屏保；按确认逐相册选择"),
    SS_ENABLED(SettingsGroup.SCREENSAVER, "屏保", "关闭后空闲也不再进入待机显示"),
    SS_IDLE(SettingsGroup.SCREENSAVER, "静止后进入", "多久没有操作就进入屏保"),
    SS_MODE(SettingsGroup.SCREENSAVER, "屏保画面", "时钟待机 / 纯照片；屏保里 ← → 换样式"),
    SS_SOURCE(SettingsGroup.SCREENSAVER, "轮播内容", "仅在客厅展示相册内筛选人物 / 最近 / 随机 / 相册"),
    SS_ALBUM(SettingsGroup.SCREENSAVER, "轮播相册", "只可选择已授权的客厅展示相册"),
    SS_INTERVAL(SettingsGroup.SCREENSAVER, "照片停留", "每张照片播放多久"),
    SS_CLOCK(SettingsGroup.SCREENSAVER, "时钟样式", "五种表盘，切换实时预览（纯照片模式下不显示）"),
    SS_BACKGROUND(SettingsGroup.SCREENSAVER, "背景", "时钟待机的背景：照片轮播 / 极光动态 / 纯黑极简"),
    SS_DATE(SettingsGroup.SCREENSAVER, "显示日期", "显示星期与日期"),
    SS_NIGHT_DIM(SettingsGroup.SCREENSAVER, "夜间自动调暗", "降低亮度，保护面板"),
    SS_KEN_BURNS(SettingsGroup.SCREENSAVER, "Ken Burns 动效", "照片缓慢推拉，防烧屏"),
    SS_MUSIC(SettingsGroup.SCREENSAVER, "背景音乐", "幻灯片/屏保时循环播放；可用手机上传音乐"),
    SS_PREVIEW(SettingsGroup.SCREENSAVER, "预览屏保", "仅全屏预览，不开启自动屏保"),

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

    /**
     * 账号/密码/访问码都是「当前账号」的字段，走 updateActiveAccount。
     * 没有任何账号时这里会自动建一个空账号 —— 用户第一次填就能落地，不用先点「添加账号」。
     */
    fun setAccount(value: String) {
        store.updateActiveAccount { it.copy(username = value.trim()) }
        feedback.invalidateNasTest()
    }

    fun setPort(value: String) {
        val port = value.toIntOrNull()?.coerceIn(1, 65535) ?: return
        store.updateNas { it.copy(port = port) }
        feedback.invalidateNasTest()
    }

    fun setPassword(value: String) {
        store.updateActiveAccount { it.copy(password = value) }
        feedback.invalidateNasTest()
    }

    fun setAccessCode(value: String) {
        store.updateActiveAccount { it.copy(accessCode = value) }
        feedback.invalidateNasTest()
    }

    /** 账号备注名（列表里显示的名字，不影响登录） */
    fun setAccountLabel(value: String) {
        store.updateActiveAccount { it.copy(label = value.trim()) }
    }

    // ---------------- 多账号 ----------------

    /**
     * 切换当前账号。
     * 外壳（NasPhotoTvApp）会监听 activeAccountId 变化 → 重建仓库 + 清空 MediaCache，
     * 所以这里只负责改状态和给一句提示。
     */
    fun switchAccount(accountId: String) {
        val target = store.nas.account(accountId) ?: return
        if (store.nas.activeAccountId == accountId) {
            feedback.toast = "已经在使用「${target.displayName}」"
            return
        }
        if (store.switchAccount(accountId)) {
            feedback.invalidateNasTest()
            feedback.toast = "已切换到「${target.displayName}」"
        }
    }

    /** 新增账号并切过去；新账号是空的，用户接着填账号密码 */
    fun addAccount() {
        store.addAccount()
        feedback.invalidateNasTest()
        feedback.toast = "已添加账号，请填写账号与密码"
    }

    /** 删除账号（最后一个不能删，UI 会禁用并给出说明） */
    fun removeAccount(accountId: String) {
        val target = store.nas.account(accountId) ?: return
        if (store.nas.accounts.size <= 1) {
            feedback.toast = "至少要保留一个账号"
            return
        }
        if (store.removeAccount(accountId)) {
            feedback.invalidateNasTest()
            feedback.toast = "已删除账号「${target.displayName}」"
        }
    }

    /**
     * 删除当前账号（设置页「删除当前账号」那行动作）。
     * 两步确认：凭据删了不可恢复，遥控器上又很容易手滑连按两下。
     */
    fun requestRemoveActiveAccount() {
        val current = store.nas.activeAccount
        if (current == null) {
            feedback.toast = "还没有保存账号"
            return
        }
        if (!feedback.removeAccountArmed) {
            feedback.armRemoveAccount()
            return
        }
        feedback.disarmRemoveAccount()
        removeAccount(current.id)
    }

    /**
     * 「当前账号」行上按 ← → ：在已保存的账号之间循环。
     * 只有一个账号时给一句提示而不是静默无反应 —— 遥控器上「按了没动静」最难判断。
     */
    fun cycleAccount(reverse: Boolean = false) {
        val accounts = store.nas.accounts
        if (accounts.isEmpty()) {
            feedback.toast = "还没有保存账号，先添加一个"
            return
        }
        if (accounts.size == 1) {
            feedback.toast = "只有「${accounts.first().displayName}」一个账号，可按「添加账号」新增"
            return
        }
        val currentIndex = accounts.indexOfFirst { it.id == store.nas.activeAccountId }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + (if (reverse) -1 else 1) + accounts.size) % accounts.size
        switchAccount(accounts[nextIndex].id)
    }

    // ---------------- 屏保 ----------------
    fun toggleScreensaver() = store.updateScreensaver { it.copy(enabled = !it.enabled) }
    fun cycleIdleTimeout() = store.cycleIdleTimeout()
    fun cycleMode() = store.cycleScreensaverMode()
    private fun displayAlbums() = currentDisplayAlbums(store).filter { it.id in store.displayAlbumIds }
    fun cycleSource() = store.cycleScreensaverSource(displayAlbums())
    fun cycleAlbum() = store.cycleScreensaverAlbum(displayAlbums())
    fun setMode(mode: ScreensaverSettings.Mode) = store.updateScreensaver { it.copy(mode = mode) }
    fun setSource(source: ScreensaverSettings.PhotoSource) =
        store.setScreensaverSource(source, displayAlbums())
    fun cyclePhotoInterval() = store.cyclePhotoInterval()
    fun cycleClockStyle() = store.cycleClockStyle()
    fun cycleBackground() = store.cycleScreensaverBackground()
    fun toggleDate() = store.updateScreensaver { it.copy(showDate = !it.showDate) }
    fun toggleNightDim() = store.updateScreensaver { it.copy(nightDim = !it.nightDim) }
    fun toggleKenBurns() = store.updateScreensaver { it.copy(kenBurns = !it.kenBurns) }

    fun previewScreensaver() {
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

    /** 非 null = 账号开启了两步验证，等待用户输入 6 位动态码 */
    /** 背景音乐面板是否打开（打开时启动手机上传服务） */
    var musicPanel by mutableStateOf(false)
    /** 上传/开关后强制刷新面板（文件名列表、状态） */
    var musicVersion by mutableIntStateOf(0)

    var twoFactor by mutableStateOf<FnOsTwoFactorChallenge?>(null)
        private set
    var twoFactorBusy by mutableStateOf(false)
        private set
    var twoFactorError by mutableStateOf<String?>(null)
        private set

    /** 两步验证期间必须保留同一个 FnOsApi：临时凭证只对那条 WebSocket 有效 */
    private var twoFactorApi: FnOsApi? = null
    private var testStartedNs = 0L

    var resetArmed by mutableStateOf(false)
        private set

    /**
     * 「删除当前账号」的二次确认。
     * 不能和 resetArmed 复用：两个动作都在左栏，复用会让"按了重置却在删账号"这种
     * 错位状态出现，这是数据丢失级别的坑。
     */
    var removeAccountArmed by mutableStateOf(false)
        private set
    /** 底部提示（2 秒后由页面自动清空） */
    var toast by mutableStateOf<String?>(null)

    var showDesignDetail by mutableStateOf(false)

    /** 地址/账号/密码改动后旧的探测结果失效 */
    fun invalidateNasTest() {
        if (nasTesting) return
        // 改了账号/密码，等着的动态码就对应旧凭据了，直接丢弃这次半登录会话
        if (twoFactor != null) {
            twoFactorApi?.close()
            twoFactorApi = null
            twoFactor = null
            twoFactorError = null
        }
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
            var keepForTwoFactor = false
            try {
                testStartedNs = System.nanoTime()
                val result = api.testConnection()
                val challenge = result.twoFactor
                if (challenge != null) {
                    // 不是失败：同一会话等用户输入动态码，验证通过后再 stat
                    keepForTwoFactor = true
                    twoFactorApi?.close()
                    twoFactorApi = api
                    twoFactor = challenge
                    twoFactorError = null
                } else {
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
                }
            } finally {
                if (!keepForTwoFactor) api.close()
            }
        } finally {
            nasTesting = false
        }
    }

    /** 提交两步验证动态码；成功后才算「测试通过」 */
    suspend fun submitTwoFactor(store: SettingsStore, code: String) {
        val api = twoFactorApi ?: return
        if (twoFactorBusy) return
        twoFactorBusy = true
        twoFactorError = null
        try {
            api.verifyTwoFactor(code)
            val stats = api.stat()
            val latency = ((System.nanoTime() - testStartedNs) / 1_000_000L)
                .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            nasTestOk = true
            nasTestLatency = latency
            nasTestDetail = buildString {
                append("两步验证通过 · 登录成功")
                append(" · ${stats.photoCount} 张照片 / ${stats.videoCount} 个视频")
            }
            twoFactor = null
            twoFactorApi = null
            api.close()
            store.reportNasResult(latency)
        } catch (e: Exception) {
            // 验证码错误时保留会话，用户可以直接改输一次；
            // 会话本身已经没了（超时/连接断开）就收起输入层，避免一直让用户白输
            if (api.hasPendingTwoFactor()) {
                twoFactorError = e.message ?: "验证失败"
            } else {
                twoFactorApi = null
                twoFactor = null
                twoFactorError = null
                nasTestError = e.message ?: "两步验证失败，请重新测试连接"
                runCatching { api.close() }
            }
        } finally {
            twoFactorBusy = false
        }
    }

    /** 设置页销毁时收尾：半登录会话不能留着，否则 WebSocket 会一直挂在 NAS 上 */
    fun release() {
        twoFactorApi?.close()
        twoFactorApi = null
        twoFactor = null
        twoFactorBusy = false
    }

    /** 取消两步验证：关掉半登录会话，避免 WebSocket 一直挂着 */
    fun cancelTwoFactor() {
        twoFactorApi?.close()
        twoFactorApi = null
        twoFactor = null
        twoFactorError = null
        nasTestOk = null
        nasTestLatency = null
        nasTestDetail = null
        nasTestError = null
        toast = "已取消两步验证"
    }

    fun armReset() {
        resetArmed = true
    }

    fun disarmReset() {
        resetArmed = false
    }

    fun armRemoveAccount() {
        removeAccountArmed = true
    }

    fun disarmRemoveAccount() {
        removeAccountArmed = false
    }
}

/** 「测试连接」的结果文案（左栏胶囊） */
fun nasResultText(feedback: SettingsFeedback): String? = when {
    feedback.nasTesting -> "正在测试连接…"
    feedback.twoFactor != null -> "需要两步验证"
    feedback.nasTestOk == true -> "连接正常 · ${feedback.nasTestLatency ?: 0}ms"
    feedback.nasTestOk == false -> "连接失败"
    else -> null
}

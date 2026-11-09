package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import kotlinx.coroutines.channels.Channel
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay

/**
 * ================= 设置页（settings-dev） =================
 *
 * 布局：左侧分组设置 + 右侧即时预览；窄画布使用更紧凑的内边距。
 *
 * 遥控器：
 *  - ↑↓     在左栏行间移动（列表自动滚动保证聚焦行可见）
 *  - ←→     调整当前行的值（开关 / 协议 / 停留时长 / 时钟样式 / 背景 / 端口微调），
 *           行内 `‹ 值 ›` 胶囊即时反馈；上下键在有"微调"的行上做精调（端口 ±1、账号序号）
 *  - ENTER  执行（动作行立即执行；「恢复默认」需在 3 秒内按两次）
 *  - BACK   关闭设置页（「设计说明」展开时先收回说明，再按才退出）
 *
 * 已配置 NAS 时初始焦点落在「客厅展示相册」，先明确允许自动展示的范围。
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onClose: () -> Unit,
    onPreviewScreensaver: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    screensaverPhotos: List<MediaItem> = emptyList(),
) {
    val feedback = remember { SettingsFeedback() }
    val actions = remember(store) {
        SettingsActions(
            store = store,
            feedback = feedback,
            onPreviewScreensaver = onPreviewScreensaver,
        )
    }
    val listState = rememberLazyListState()

    // 当前聚焦行：右侧面板据此切换；首次未配置 NAS 时直接落在「地址」行
    val initialKey = remember { if (store.nas.isConfigured) INITIAL_KEY else SettingsKey.NAS_HOST }
    var focusedKey by remember { mutableStateOf(initialKey) }
    val requesters = remember { mutableMapOf<SettingsKey, FocusRequester>() }
    fun requesterFor(key: SettingsKey): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    // 动作队列：按键只入队，由单个协程串行执行（测试连接会挂起，避免叠跑）。
    // 注意别用 List.size 当 LaunchedEffect 的 key：出队会改变 size，从而取消正在跑的探测。
    val actionQueue = remember { Channel<SettingsKey>(Channel.UNLIMITED) }

    // 密码 / 访问码的电视键盘输入层
    var textTarget by remember { mutableStateOf<SettingsKey?>(null) }
    var editorOpened by remember { mutableStateOf(false) }
    var displayAlbumsOpen by remember { mutableStateOf(false) }
    var displayAlbumsOpened by remember { mutableStateOf(false) }

    LaunchedEffect(displayAlbumsOpen) {
        if (displayAlbumsOpen) {
            displayAlbumsOpened = true
        } else if (displayAlbumsOpened) {
            focusedKey = SettingsKey.SS_DISPLAY_ALBUMS
            listState.scrollToItem(rowIndexInList(SettingsKey.SS_DISPLAY_ALBUMS))
            withFrameMillis { }
            runCatching { requesterFor(SettingsKey.SS_DISPLAY_ALBUMS).requestFocus() }
        }
    }

    // 关闭输入层后把焦点还给刚才那一行（输入层拿到焦点时原焦点已丢失）
    LaunchedEffect(textTarget) {
        if (textTarget != null) {
            editorOpened = true
        } else if (editorOpened) {
            delay(80)
            runCatching { requesterFor(focusedKey).requestFocus() }
        }
    }

    // 初始焦点：等首帧布局完成
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { requesterFor(initialKey).requestFocus() }
    }

    // 动作队列：串行执行（测试连接会挂起），不会叠跑
    LaunchedEffect(Unit) {
        for (key in actionQueue) {
            when (key) {
                SettingsKey.NAS_TEST -> feedback.runNasTest(store)
                SettingsKey.NAS_REFRESH -> {
                    // 回媒体库会触发外壳重新拉取时间线与相册
                    feedback.toast = "正在刷新媒体库…"
                    onClose()
                }
                else -> {}
            }
        }
    }

    // 「恢复默认」二次确认：焦点离开该行 / 3 秒超时都会解除
    LaunchedEffect(feedback.resetArmed, focusedKey) {
        if (!feedback.resetArmed) return@LaunchedEffect
        if (focusedKey != SettingsKey.ABOUT_RESET) {
            feedback.disarmReset()
            return@LaunchedEffect
        }
        delay(3000)
        feedback.disarmReset()
    }

    // 「删除当前账号」同样两步确认，超时或移开焦点都解除
    LaunchedEffect(feedback.removeAccountArmed, focusedKey) {
        if (!feedback.removeAccountArmed) return@LaunchedEffect
        if (focusedKey != SettingsKey.ACC_REMOVE) {
            feedback.disarmRemoveAccount()
            return@LaunchedEffect
        }
        delay(3000)
        feedback.disarmRemoveAccount()
    }

    // toast 2 秒自动消失
    LaunchedEffect(feedback.toast) {
        if (feedback.toast != null) {
            delay(2000)
            feedback.toast = null
        }
    }

    // 聚焦行始终可见
    LaunchedEffect(focusedKey) {
        val index = rowIndexInList(focusedKey)
        runCatching { listState.animateScrollToItem(index.coerceAtLeast(0)) }
    }

    val rightPad by animateDpAsState(
        targetValue = if (feedback.showDesignDetail) TvDimen.SpaceM else TvDimen.SpaceL,
        animationSpec = tween(200),
        label = "panelPad",
    )

    // BACK：挂在整页根节点（preview 阶段），任何焦点位置都能退出
    SettingsBackdrop(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (displayAlbumsOpen) {
                        displayAlbumsOpen = false
                    } else if (textTarget != null) {
                        textTarget = null
                    } else if (feedback.showDesignDetail) {
                        feedback.showDesignDetail = false
                    } else {
                        onClose()
                    }
                    true
                } else {
                    false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(nasName = store.nas.name, online = store.nas.connected)

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = TvDimen.ScreenPadding),
            ) {
                val compact = maxWidth < 1200.dp
                val leftWidth = if (compact) maxWidth * 0.48f else (maxWidth * 0.34f).coerceIn(480.dp, 620.dp)
                val panelGap = if (compact) TvDimen.SpaceM else TvDimen.SpaceL

                Row(Modifier.fillMaxSize()) {
                    SettingsLeftPanel(
                        store = store,
                        feedback = feedback,
                        actions = actions,
                        listState = listState,
                        focusedKey = focusedKey,
                        onFocusKey = { focusedKey = it },
                        requesterFor = { requesterFor(it) },
                        onQueue = { key -> actionQueue.trySend(key) },
                        onEditText = { key -> textTarget = key },
                        onOpenDisplayAlbums = { displayAlbumsOpen = true },
                        width = leftWidth,
                    )

                    Spacer(Modifier.width(panelGap))

                    // ---------------- 右栏 ----------------
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(TvDimen.RadiusL))
                            .background(TvColor.Stage)
                            .border(
                                width = 1.dp,
                                color = TvColor.Divider,
                                shape = RoundedCornerShape(TvDimen.RadiusL),
                            )
                            .padding(if (compact) TvDimen.SpaceM else rightPad),
                    ) {
                        if (displayAlbumsOpen) {
                            SettingsDisplayAlbumsPanel(store, onClose = { displayAlbumsOpen = false })
                        } else Column(Modifier.fillMaxSize()) {
                            val group = focusedKey.group
                            AnimatedContent(
                                targetState = group,
                                // 必须显式吃掉右栏剩余高度：AnimatedContent 默认只按内容高度测量，
                                // 结果 SettingsAccountPanel 的 fillMaxSize 拿不到真实高度，
                                // weight(1f) 的账号列表只分到一张卡片的高度 —— 第 2 个账号被裁在框外。
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                                label = "settingsRightPanel",
                            ) { g ->
                                when (g) {
                                    SettingsGroup.NAS -> SettingsNasPanel(store, feedback)

                                    SettingsGroup.ACCOUNTS -> SettingsAccountPanel(store)

                                    SettingsGroup.SCREENSAVER -> SettingsScreensaverPanel(
                                        store = store,
                                        focusedKey = focusedKey,
                                        onPickClock = { style ->
                                            store.updateScreensaver { it.copy(clockStyle = style) }
                                        },
                                        onPickBackground = { bg ->
                                            store.updateScreensaver { it.copy(background = bg) }
                                        },
                                        onPickMode = actions::setMode,
                                        onPickSource = actions::setSource,
                                        screensaverPhotos = screensaverPhotos,
                                    )

                                    SettingsGroup.PLAYBACK -> SettingsPlaybackPanel(store)
                                    SettingsGroup.ABOUT -> SettingsAboutPanel(store, feedback)
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- 底部状态条 ----------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TvDimen.ScreenPadding)
                    .padding(top = TvDimen.SpaceM, bottom = TvDimen.SpaceM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = focusedKey.hint,
                    fontSize = TvType.Caption,
                    color = TvColor.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(TvDimen.SpaceM))
                Text(
                    text = focusedKey.group.title,
                    fontSize = TvType.Caption,
                    color = TvColor.TextMuted,
                    maxLines = 1,
                )
            }
        }

        // ---------------- 连接参数：电视键盘输入层 ----------------
        val editing = textTarget
        if (editing != null) {
            val spec = textEditSpec(editing, store)
            TextEntryOverlay(
                title = spec.title,
                subtitle = spec.subtitle,
                masked = spec.masked,
                initial = spec.initial,
                onCancel = { textTarget = null },
                onDone = { value ->
                    when (editing) {
                        SettingsKey.NAS_DEVICE -> actions.setName(value)
                        SettingsKey.NAS_HOST -> actions.setHost(value)
                        SettingsKey.NAS_PORT -> actions.setPort(value)
                        SettingsKey.NAS_ACCOUNT -> actions.setAccount(value)
                        SettingsKey.NAS_PASSWORD -> actions.setPassword(value)
                        SettingsKey.NAS_ACCESS_CODE -> actions.setAccessCode(value)
                        SettingsKey.ACC_LABEL -> actions.setAccountLabel(value)
                        else -> {}
                    }
                    textTarget = null
                },
            )
        }

        // ---------------- 右下角 toast ----------------
        feedback.toast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TvDimen.ScreenPadding, bottom = 96.dp)
                    .clip(CircleShape)
                    .background(TvColor.Accent.copy(alpha = 0.16f))
                    .border(1.dp, TvColor.Accent.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceXs),
            ) {
                Text(msg, fontSize = TvType.Caption, color = TvColor.Accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 顶部标题栏 / 左栏 / 右栏
// ---------------------------------------------------------------------------

@Composable
private fun SettingsTopBar(nasName: String, online: Boolean) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimen.ScreenPadding)
            .padding(top = TvDimen.SpaceL, bottom = TvDimen.SpaceM),
    ) {
        val compact = maxWidth < 1200.dp
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "设置",
                        fontSize = if (compact) TvType.Section else TvType.Display,
                        fontWeight = FontWeight.Medium,
                        color = TvColor.TextPrimary,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text("让相册适合你的客厅", fontSize = TvType.Caption, color = TvColor.TextSecondary)
                }
                Spacer(Modifier.width(TvDimen.SpaceM))
                Row(
                    Modifier
                        .widthIn(max = if (compact) 320.dp else 480.dp)
                        .clip(RoundedCornerShape(TvDimen.RadiusM))
                        .background(TvColor.SurfaceSoft)
                        .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(TvDimen.SpaceXs).clip(CircleShape).background(if (online) TvColor.Accent else TvColor.Danger))
                    Spacer(Modifier.width(TvDimen.SpaceS))
                    Text(
                        text = "${nasName.ifBlank { "NAS" }} · ${if (online) "已连接" else "未连接"}",
                        fontSize = TvType.Caption,
                        color = TvColor.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(TvDimen.SpaceM))
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM)) { KeyHintRow() }
        }
    }
}

/** 右上角按键提示 */
@Composable
private fun KeyHintRow() {
    listOf("↑ ↓ 选择", "← → 调整", "确认 应用", "返回 退出").forEach { h ->
        Text(
            text = h,
            fontSize = TvType.Caption,
            color = TvColor.TextMuted,
        )
    }
}

@Composable
private fun SettingsLeftPanel(
    store: SettingsStore,
    feedback: SettingsFeedback,
    actions: SettingsActions,
    listState: LazyListState,
    focusedKey: SettingsKey,
    onFocusKey: (SettingsKey) -> Unit,
    requesterFor: (SettingsKey) -> FocusRequester,
    onQueue: (SettingsKey) -> Unit,
    onEditText: (SettingsKey) -> Unit,
    onOpenDisplayAlbums: () -> Unit,
    width: Dp,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        contentPadding = PaddingValues(start = TvDimen.SpaceXs, end = TvDimen.SpaceXs, bottom = TvDimen.SpaceL, top = TvDimen.SpaceXs),
        verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
    ) {
        SettingsGroup.entries.forEach { group ->
            item(key = "header_${group.name}") {
                SettingsGroupHeader(group = group, focusedKey = focusedKey)
            }
            val rows = SettingsRowsByGroup.getValue(group)
            items(count = rows.size, key = { rows[it].name }) { i ->
                val key = rows[i]
                val step = stepAction(key, actions)
                SettingsRow(
                    key = key,
                    store = store,
                    feedback = feedback,
                    focused = focusedKey == key,
                    focusRequester = requesterFor(key),
                    onFocus = { onFocusKey(key) },
                    onConfirm = {
                        if (key == SettingsKey.SS_DISPLAY_ALBUMS) onOpenDisplayAlbums()
                        else performConfirm(key, actions, onQueue, onEditText)
                    },
                    onStepLeft = step?.let { fn -> { fn(true) } },
                    onStepRight = step?.let { fn -> { fn(false) } },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 按键 → 行为映射（集中在一处，方便对照遥控器手感）
// ---------------------------------------------------------------------------

/** ENTER */
private fun performConfirm(
    key: SettingsKey,
    actions: SettingsActions,
    onQueue: (SettingsKey) -> Unit,
    onEditText: (SettingsKey) -> Unit,
) {
    when (key) {
        SettingsKey.NAS_TEST, SettingsKey.NAS_REFRESH -> onQueue(key)
        SettingsKey.NAS_DEVICE,
        SettingsKey.NAS_HOST,
        SettingsKey.NAS_PORT,
        SettingsKey.NAS_ACCOUNT,
        SettingsKey.NAS_PASSWORD,
        SettingsKey.NAS_ACCESS_CODE,
        SettingsKey.ACC_LABEL,
        -> onEditText(key)

        // 账号组里 ENTER 就是「马上做那件事」，不做步进
        SettingsKey.ACC_ADD -> actions.addAccount()
        SettingsKey.ACC_REMOVE -> actions.requestRemoveActiveAccount()

        SettingsKey.SS_PREVIEW -> actions.previewScreensaver()
        SettingsKey.ABOUT_DESIGN -> actions.toggleDesign()
        SettingsKey.ABOUT_RESET -> actions.requestReset()
        // 其它行：ENTER 等同于"往后调一格"
        else -> stepAction(key, actions)?.invoke(false)
    }
}

/** 电视键盘输入层的文案与初始值 */
private data class TextEditSpec(
    val title: String,
    val subtitle: String,
    val initial: String,
    val masked: Boolean,
)

private fun textEditSpec(key: SettingsKey, store: SettingsStore): TextEditSpec = when (key) {
    SettingsKey.NAS_DEVICE -> TextEditSpec("NAS 名称", "显示在顶栏与本机", store.nas.name, masked = false)
    SettingsKey.NAS_HOST -> TextEditSpec("NAS 地址", "局域网 IP 或 DDNS 域名", store.nas.host, masked = false)
    SettingsKey.NAS_PORT -> TextEditSpec("端口", "飞牛 Web HTTPS 端口，默认 5667", store.nas.port.toString(), masked = false)
    SettingsKey.NAS_ACCOUNT -> TextEditSpec("账号", "飞牛账号", store.nas.username, masked = false)
    SettingsKey.NAS_PASSWORD -> TextEditSpec("密码", "仅保存在这台电视上", store.nas.password, masked = true)
    SettingsKey.NAS_ACCESS_CODE -> TextEditSpec("访问码", "未启用访问码网关时留空", store.nas.accessCode, masked = false)
    SettingsKey.ACC_LABEL -> TextEditSpec(
        title = "备注名",
        subtitle = "只显示在这台电视上，用来区分家人的账号",
        initial = store.nas.activeAccount?.label.orEmpty(),
        masked = false,
    )
    else -> TextEditSpec("输入", "", "", masked = false)
}

/**
 * ←→（及上下微调）。返回 (isLeft: Boolean) -> Unit；null = 这一行不可调。
 * 不可调的"信息行"（设备名称之类）按下会循环下一个候选值，避免按了没反应。
 */
private fun stepAction(key: SettingsKey, actions: SettingsActions): ((Boolean) -> Unit)? = when (key) {
    SettingsKey.SS_ENABLED -> { _ -> actions.toggleScreensaver() }
    SettingsKey.SS_IDLE -> { _ -> actions.cycleIdleTimeout() }
    SettingsKey.SS_MODE -> { _ -> actions.cycleMode() }
    SettingsKey.SS_SOURCE -> { _ -> actions.cycleSource() }
    SettingsKey.SS_ALBUM -> { _ -> actions.cycleAlbum() }
    SettingsKey.SS_INTERVAL -> { _ -> actions.cyclePhotoInterval() }
    SettingsKey.SS_CLOCK -> { _ -> actions.cycleClockStyle() }
    SettingsKey.SS_BACKGROUND -> { _ -> actions.cycleBackground() }
    SettingsKey.SS_DATE -> { _ -> actions.toggleDate() }
    SettingsKey.SS_NIGHT_DIM -> { _ -> actions.toggleNightDim() }
    SettingsKey.SS_KEN_BURNS -> { _ -> actions.toggleKenBurns() }
    SettingsKey.ACC_CURRENT -> { isLeft -> actions.cycleAccount(reverse = isLeft) }
    SettingsKey.PB_AUTOPLAY -> { _ -> actions.toggleAutoPlayNext() }
    SettingsKey.PB_LOOP -> { _ -> actions.toggleLoop() }
    else -> null
}

// ---------------------------------------------------------------------------
// 背景 / 辅助
// ---------------------------------------------------------------------------

/** 静态的深墨蓝背景，让焦点和右侧预览承担视觉层级。 */
@Composable
fun SettingsBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(TvColor.Bg)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(TvColor.Stage, TvColor.Bg)))
            },
        content = content,
    )
}

/** 初始焦点行：屏保开关 */
private val INITIAL_KEY = SettingsKey.SS_DISPLAY_ALBUMS

/** key 在 LazyColumn 里的下标（组标题也占一行，必须对齐，否则滚动错位） */
fun rowIndexInList(key: SettingsKey): Int {
    var index = 0
    SettingsGroup.entries.forEach { g ->
        index += 1 // 组标题
        val rows = SettingsRowsByGroup.getValue(g)
        val inGroup = rows.indexOf(key)
        if (inGroup >= 0) return index + inGroup
        index += rows.size
    }
    return 0
}

// ---------------------------------------------------------------------------
// 预览
// ---------------------------------------------------------------------------

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PreviewSettingsScreen() {
    NasPhotoTvTheme {
        SettingsScreen(
            store = SettingsStore,
            onClose = {},
            onPreviewScreensaver = {},
            onOpenLibrary = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PreviewSettingsNasPanel() {
    NasPhotoTvTheme {
        SettingsBackdrop(Modifier.fillMaxSize()) {
            val feedback = remember { SettingsFeedback() }
            Column(Modifier.fillMaxSize().padding(TvDimen.ScreenPadding)) {
                SettingsNasPanel(SettingsStore, feedback)
            }
        }
    }
}

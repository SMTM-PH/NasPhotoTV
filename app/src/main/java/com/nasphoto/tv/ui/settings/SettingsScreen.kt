/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.settings

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import kotlinx.coroutines.channels.Channel
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 分类在顶部，当前分类的设置与效果预览分别位于两侧。 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onClose: () -> Unit,
    onPreviewScreensaver: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    screensaverPhotos: List<MediaItem> = emptyList(),
    onRefreshLibrary: () -> Unit = onClose,
    initialFocus: SettingsKey? = null,
) {
    val feedback = remember { SettingsFeedback() }
    val scope = rememberCoroutineScope()
    // 离开设置页时丢掉还没完成的两步验证会话（否则 WebSocket 会一直挂着）
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    val actions = remember(store) {
        SettingsActions(
            store = store,
            feedback = feedback,
            onPreviewScreensaver = onPreviewScreensaver,
        )
    }
    // 当前聚焦行：右侧面板据此切换；首次未配置 NAS 时直接落在「地址」行
    val initialKey = remember(initialFocus) {
        initialFocus ?: if (store.nas.isConfigured) INITIAL_KEY else SettingsKey.NAS_HOST
    }
    var focusedKey by remember(initialKey) { mutableStateOf(initialKey) }
    val activeGroup = focusedKey.group
    val listState = key(activeGroup) { rememberLazyListState(rowIndexInList(focusedKey)) }
    val requesters = remember { SettingsKey.entries.associateWith { FocusRequester() } }
    val categoryRequesters = remember { SettingsGroup.entries.associateWith { FocusRequester() } }
    var pendingRowFocus by remember(initialKey) { mutableStateOf<SettingsKey?>(initialKey) }

    fun selectGroup(group: SettingsGroup) {
        if (focusedKey.group != group) focusedKey = SettingsRowsByGroup.getValue(group).first()
    }

    LaunchedEffect(pendingRowFocus, activeGroup) {
        val target = pendingRowFocus?.takeIf { it.group == activeGroup } ?: return@LaunchedEffect
        listState.scrollToItem(rowIndexInList(target))
        repeat(15) {
            withFrameMillis { }
            if (runCatching { requesters.getValue(target).requestFocus() }.isSuccess) {
                pendingRowFocus = null
                return@LaunchedEffect
            }
        }
    }

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
            pendingRowFocus = SettingsKey.SS_DISPLAY_ALBUMS
        }
    }

    // 关闭输入层后把焦点还给刚才那一行（输入层拿到焦点时原焦点已丢失）
    LaunchedEffect(textTarget) {
        if (textTarget != null) {
            editorOpened = true
        } else if (editorOpened) {
            pendingRowFocus = focusedKey
        }
    }

    // 动作队列：串行执行（测试连接会挂起），不会叠跑
    LaunchedEffect(Unit) {
        for (key in actionQueue) {
            when (key) {
                SettingsKey.SS_MUSIC -> feedback.musicPanel = true
                SettingsKey.NAS_TEST -> feedback.runNasTest(store)
                SettingsKey.NAS_REFRESH -> {
                    // 回媒体库会触发外壳重新拉取时间线与相册
                    feedback.toast = "正在刷新媒体库…"
                    onRefreshLibrary()
                }
                SettingsKey.ABOUT_UPDATE -> {
                    val ctx = com.nasphoto.tv.data.AppContextHolder.context
                    if (ctx != null) com.nasphoto.tv.data.AppUpdateController.onConfirm(ctx)
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

    // BACK：挂在整页根节点（preview 阶段），任何焦点位置都能退出
    SettingsBackdrop(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (feedback.musicPanel) {
                        feedback.musicPanel = false
                    } else if (feedback.twoFactor != null) {
                        // 两步验证浮层在最上层：BACK 只取消验证，不能顺手退出整个设置页
                        feedback.cancelTwoFactor()
                    } else if (displayAlbumsOpen) {
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
            SettingsCategoryBar(
                activeGroup = activeGroup,
                requesters = categoryRequesters,
                onFocused = { group -> if (!displayAlbumsOpen) selectGroup(group) },
                onEnter = { group ->
                    if (!displayAlbumsOpen) {
                        selectGroup(group)
                        pendingRowFocus = focusedKey
                    }
                },
            )

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = TvDimen.ScreenPadding),
            ) {
                val leftWidth = (maxWidth * 0.43f).coerceAtMost(560.dp)
                val panelGap = TvDimen.SpaceM

                Row(Modifier.fillMaxSize()) {
                    SettingsLeftPanel(
                        store = store,
                        feedback = feedback,
                        actions = actions,
                        listState = listState,
                        focusedKey = focusedKey,
                        onFocusKey = { focusedKey = it },
                        requesterFor = requesters::getValue,
                        categoryFocus = categoryRequesters.getValue(focusedKey.group),
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
                            .padding(TvDimen.SpaceM),
                    ) {
                        if (displayAlbumsOpen) {
                            SettingsDisplayAlbumsPanel(store, onClose = { displayAlbumsOpen = false })
                        } else {
                            when (focusedKey.group) {
                                SettingsGroup.APPEARANCE -> SettingsAppearancePanel(store)
                                SettingsGroup.SCREENSAVER -> SettingsScreensaverPanel(
                                    store = store,
                                    focusedKey = focusedKey,
                                    onPickClock = { style ->
                                        store.updateScreensaver { it.copy(clockStyle = style) }
                                    },
                                    onPickBackground = { background ->
                                        store.updateScreensaver { it.copy(background = background) }
                                    },
                                    onPickMode = actions::setMode,
                                    onPickSource = actions::setSource,
                                    screensaverPhotos = screensaverPhotos,
                                )
                                SettingsGroup.SLIDESHOW -> SettingsSlideshowPanel(store, focusedKey, replay = 0)
                                SettingsGroup.PLAYBACK -> SettingsPlaybackPanel(store)
                                SettingsGroup.NAS -> SettingsNasPanel(store, feedback)
                                SettingsGroup.ACCOUNTS -> SettingsAccountPanel(store)
                                SettingsGroup.ABOUT -> SettingsAboutPanel(store, feedback)
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
                    .padding(vertical = TvDimen.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (focusedKey.isAvailableInMode(store.screensaver.mode)) focusedKey.hint else PHOTO_WALL_SETTING_HINT,
                    fontSize = TvType.Caption,
                    color = TvColor.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(TvDimen.SpaceM))
                Text(
                    text = "↑↓ 选择 · ←→ 调整 · 返回",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextSecondary,
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
                        SettingsKey.ABOUT_UPDATE_SOURCE -> actions.setUpdateApiUrl(value)
                        SettingsKey.ABOUT_UPDATE_MIRROR -> actions.setUpdateMirror(value)
                        else -> {}
                    }
                    textTarget = null
                },
            )
        }

        // ---------------- 背景音乐（手机上传） ----------------
        if (feedback.musicPanel) {
            MusicOverlay(store = store, feedback = feedback, onClose = { feedback.musicPanel = false })
        }

        // ---------------- 两步验证：6 位动态码（复用电视数字键盘） ----------------
        feedback.twoFactor?.let { challenge ->
            // key = 错误信息：验证码错误后重建输入层，用户不用先删掉 6 位旧数字
            key(feedback.twoFactorError) {
                TextEntryOverlay(
                    title = "两步验证",
                    subtitle = buildString {
                        if (challenge.totpBound) {
                            append("该账号已开启两步验证，请输入验证器 App 中的 6 位动态码；验证成功后本机会被信任")
                        } else {
                            append("该账号需要邮箱验证码，请先在飞牛 App 中改用验证器后再连接")
                        }
                        if (feedback.twoFactorBusy) append(" · 正在验证…")
                        feedback.twoFactorError?.let { append(" · $it") }
                    },
                    masked = false,
                    initial = "",
                    numericOnly = true,
                    maxLength = 6,
                    onCancel = { feedback.cancelTwoFactor() },
                    onDone = { code ->
                        if (!feedback.twoFactorBusy) {
                            scope.launch { feedback.submitTwoFactor(store, code) }
                        }
                    },
                )
            }
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimen.ScreenPadding)
            .padding(top = TvDimen.SpaceM, bottom = TvDimen.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "设置",
            modifier = Modifier.weight(1f),
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            color = TvColor.TextPrimary,
        )
        Spacer(Modifier.width(TvDimen.SpaceM))
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (online) TvColor.Accent else TvColor.TextMuted),
        )
        Spacer(Modifier.width(TvDimen.SpaceXs))
        Text(
            text = "${nasName.ifBlank { "NAS" }} · ${if (online) "已连接" else "未连接"}",
            modifier = Modifier.widthIn(max = 280.dp),
            fontSize = TvType.Caption,
            color = TvColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCategoryBar(
    activeGroup: SettingsGroup,
    requesters: Map<SettingsGroup, FocusRequester>,
    onFocused: (SettingsGroup) -> Unit,
    onEnter: (SettingsGroup) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding)
            .padding(bottom = TvDimen.SpaceM),
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
    ) {
        SettingsGroup.entries.forEach { group ->
            val active = group == activeGroup
            TvFocusBox(
                modifier = Modifier.weight(1f).height(TvDimen.ControlHeight)
                    .clip(RoundedCornerShape(TvDimen.RadiusS))
                    .background(if (active) TvColor.SurfaceSoft else TvColor.Stage)
                    .semantics {
                        role = Role.Tab
                        selected = active
                    },
                focusRequester = requesters.getValue(group),
                cornerRadiusDp = 10,
                focusedScale = 1f,
                onFocusChanged = { if (it) onFocused(group) },
                onConfirm = { onEnter(group) },
                onDown = { onEnter(group) },
            ) { focused ->
                Text(
                    group.title,
                    Modifier.align(Alignment.Center),
                    color = if (active || focused) TvColor.TextPrimary else TvColor.TextSecondary,
                    fontSize = TvType.Label,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                            .width(16.dp).height(2.dp).background(TvColor.Accent, CircleShape),
                    )
                }
            }
        }
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
    categoryFocus: FocusRequester,
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
        val rows = SettingsRowsByGroup.getValue(focusedKey.group)
        items(count = rows.size, key = { rows[it].name }) { index ->
            val key = rows[index]
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
                modifier = if (index == 0) Modifier.focusProperties { up = categoryFocus } else Modifier,
                simple = true,
            )
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
        SettingsKey.NAS_TEST, SettingsKey.NAS_REFRESH, SettingsKey.SS_MUSIC -> onQueue(key)
        SettingsKey.NAS_DEVICE,
        SettingsKey.NAS_HOST,
        SettingsKey.NAS_PORT,
        SettingsKey.NAS_ACCOUNT,
        SettingsKey.NAS_PASSWORD,
        SettingsKey.NAS_ACCESS_CODE,
        SettingsKey.ACC_LABEL,
        SettingsKey.ABOUT_UPDATE_SOURCE,
        SettingsKey.ABOUT_UPDATE_MIRROR,
        -> onEditText(key)

        // 账号组里 ENTER 就是「马上做那件事」，不做步进
        SettingsKey.ACC_ADD -> actions.addAccount()
        SettingsKey.ACC_REMOVE -> actions.requestRemoveActiveAccount()

        SettingsKey.SS_PREVIEW -> actions.previewScreensaver()
        SettingsKey.ABOUT_UPDATE -> onQueue(key)
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
    SettingsKey.NAS_HOST -> TextEditSpec(
        "NAS 地址",
        "IP / 域名，或完整地址 http(s)://域名:端口/子路径",
        store.nas.displayAddress,
        masked = false,
    )
    SettingsKey.NAS_PORT -> TextEditSpec("端口", "直连 5667；反代填网关端口（如 443 / 80）", store.nas.port.toString(), masked = false)
    SettingsKey.NAS_ACCOUNT -> TextEditSpec("账号", "飞牛账号", store.nas.username, masked = false)
    SettingsKey.NAS_PASSWORD -> TextEditSpec("密码", "仅保存在这台电视上", store.nas.password, masked = true)
    SettingsKey.NAS_ACCESS_CODE -> TextEditSpec("访问码", "未启用访问码网关时留空", store.nas.accessCode, masked = false)
    SettingsKey.ACC_LABEL -> TextEditSpec(
        title = "备注名",
        subtitle = "只显示在这台电视上，用来区分家人的账号",
        initial = store.nas.activeAccount?.label.orEmpty(),
        masked = false,
    )
    SettingsKey.ABOUT_UPDATE_SOURCE -> TextEditSpec(
        title = "更新源地址",
        subtitle = "留空 = GitHub 官方 latest API；可填镜像或反代的完整 URL",
        initial = store.update.apiUrl,
        masked = false,
    )
    SettingsKey.ABOUT_UPDATE_MIRROR -> TextEditSpec(
        title = "下载镜像前缀",
        subtitle = "可选；例如 https://mirror.example/ 会拼在 APK 下载地址前，留空直连",
        initial = store.update.mirror,
        masked = false,
    )
    else -> TextEditSpec("输入", "", "", masked = false)
}

/**
 * ←→（及上下微调）。返回 (isLeft: Boolean) -> Unit；null = 这一行不可调。
 * 不可调的"信息行"（设备名称之类）按下会循环下一个候选值，避免按了没反应。
 */
private fun stepAction(key: SettingsKey, actions: SettingsActions): ((Boolean) -> Unit)? = when (key) {
    SettingsKey.APPEARANCE_MODE -> { isLeft -> actions.cycleAppearance(reverse = isLeft) }
    SettingsKey.SS_ENABLED -> { _ -> actions.toggleScreensaver() }
    SettingsKey.SS_IDLE -> { _ -> actions.cycleIdleTimeout() }
    SettingsKey.SS_MODE -> { isLeft -> actions.cycleMode(reverse = isLeft) }
    SettingsKey.SS_SOURCE -> { _ -> actions.cycleSource() }
    SettingsKey.SS_ALBUM -> { _ -> actions.cycleAlbum() }
    SettingsKey.SS_INTERVAL -> { _ -> actions.cyclePhotoInterval() }
    SettingsKey.SS_SLIDESHOW_INTERVAL -> { left -> actions.cycleSlideshowInterval(left) }
    SettingsKey.SS_SLIDESHOW_TRANSITION -> { left -> actions.cycleSlideshowTransition(left) }
    SettingsKey.SS_CLOCK -> { _ -> actions.cycleClockStyle() }
    SettingsKey.SS_BACKGROUND -> { _ -> actions.cycleBackground() }
    SettingsKey.SS_DATE -> { _ -> actions.toggleDate() }
    SettingsKey.SS_NIGHT_DIM -> { _ -> actions.toggleNightDim() }
    SettingsKey.SS_KEN_BURNS -> { _ -> actions.toggleKenBurns() }
    SettingsKey.ACC_CURRENT -> { isLeft -> actions.cycleAccount(reverse = isLeft) }
    SettingsKey.PB_AUTOPLAY -> { _ -> actions.toggleAutoPlayNext() }
    SettingsKey.PB_LOOP -> { _ -> actions.toggleLoop() }
    SettingsKey.PB_PORTRAIT_FIT -> { isLeft -> actions.cyclePortraitFit(reverse = isLeft) }
    SettingsKey.PB_ROTATION -> { isLeft -> actions.cycleVideoRotation(reverse = isLeft) }
    SettingsKey.ABOUT_UPDATE_AUTO -> { _ -> actions.toggleAutoCheck() }
    else -> null
}

// ---------------------------------------------------------------------------
// 背景 / 辅助
// ---------------------------------------------------------------------------

/** 静态的深墨蓝背景，让焦点和右侧预览承担视觉层级。 */
@Composable
fun SettingsBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // 先在组合期取色，再捕获进 drawBehind 的 DrawScope（非 Composable 上下文）。
    val bg = TvColor.Bg
    val stage = TvColor.Stage
    Box(
        modifier
            .background(bg)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(stage, bg)))
            },
        content = content,
    )
}

/** 常规入口从客厅展示相册开始。 */
private val INITIAL_KEY = SettingsKey.SS_DISPLAY_ALBUMS

private fun rowIndexInList(key: SettingsKey): Int = SettingsRowsByGroup.getValue(key.group).indexOf(key)

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

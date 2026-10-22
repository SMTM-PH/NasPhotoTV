package com.nasphoto.tv.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 左栏：一行设置项（settings-dev 维护） =================
 *
 * 行结构统一：`[状态点] [标题 / 副标题] ………… [值胶囊 | 开关 | 动作图标]`
 *  - 值行：`‹ 值 ›`
 *  - 开关行：开关（开 = 强调色）
 *  - 动作行：`›` 或状态图标（测试连接的 spinner / 成功勾 / 失败叉）
 *
 * 按键约定：
 *  - ENTER / DPAD_CENTER → [onConfirm]
 *  - LEFT / RIGHT → [onStepLeft] / [onStepRight]；两者都为 null 时 LEFT 放行给页面（切到右栏）
 *  - UP / DOWN → 交给焦点系统换行（行内不消费，否则长列表走不出去）
 */

@Composable
fun SettingsRow(
    key: SettingsKey,
    store: SettingsStore,
    feedback: SettingsFeedback,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onConfirm: () -> Unit,
    onStepLeft: (() -> Unit)? = null,
    onStepRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val value = rowValueText(key, store, feedback)
    val toggle = toggleState(key, store)
    val action = isActionRow(key)

    SettingsFocusRow(
        modifier = modifier,
        focusRequester = focusRequester,
        onConfirm = onConfirm,
        onFocused = { isFocused -> if (isFocused) onFocus() },
        onAdjust = { isLeft ->
            when {
                // 开关：左右直接切换；值行：执行该方向的步进；没有步进的动作行：吃掉但不做事
                toggle != null -> {
                    onConfirm()
                    true
                }

                else -> {
                    val step = if (isLeft) onStepLeft else onStepRight
                    step?.invoke()
                    step != null
                }
            }
        },
    ) { rowFocused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TvDimen.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (rowFocused) TvColor.Accent else TvColor.TextTertiary.copy(alpha = 0.30f),
                    ),
            )
            Spacer(Modifier.width(TvDimen.SpaceS))

            Column(Modifier.weight(1f)) {
                Text(
                    text = key.title,
                    fontSize = TvType.Body,
                    fontWeight = if (rowFocused) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (rowFocused) TvColor.TextPrimary else TvColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rowFocused) {
                    // 副标题只在聚焦行出现：非聚焦行保持安静
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(100)),
                    ) {
                        Text(
                            text = key.hint,
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(TvDimen.SpaceS))

            when {
                toggle != null -> TvSwitch(checked = toggle, focused = rowFocused)
                action -> ActionTail(key, feedback, rowFocused)
                else -> ValueChip(
                    text = value,
                    focused = rowFocused,
                    accent = if (key == SettingsKey.NAS_TEST && feedback.nasTestOk == false) {
                        TvColor.Danger
                    } else {
                        TvColor.Accent
                    },
                    minWidth = if (key == SettingsKey.NAS_HOST) 200 else 132,
                )
            }
        }
    }
}

/** 组标题：分组名 + 当前聚焦组时右侧一条短强调线 */
@Composable
fun SettingsGroupHeader(group: SettingsGroup, focusedKey: SettingsKey?, modifier: Modifier = Modifier) {
    val inGroup = focusedKey?.group == group
    val color by animateColorAsState(
        targetValue = if (inGroup) TvColor.Accent else TvColor.TextTertiary,
        animationSpec = tween(180),
        label = "groupHeader",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = TvDimen.SpaceS, top = TvDimen.SpaceM, bottom = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.title, fontSize = TvType.Caption, fontWeight = FontWeight.Medium, color = color)
        if (inGroup) {
            Spacer(Modifier.width(TvDimen.SpaceS))
            Box(
                Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(TvColor.Accent.copy(alpha = 0.45f)),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 行内容取值（左栏与右栏共用同一套判断，保证"左栏显示的就是右栏看到的"）
// ---------------------------------------------------------------------------

/** 值行显示的文字 */
fun rowValueText(key: SettingsKey, store: SettingsStore, feedback: SettingsFeedback): String {
    if (key == SettingsKey.NAS_TEST) return nasResultText(feedback) ?: "开始测试"
    val s = store.screensaver
    val p = store.playback
    return when (key) {
        SettingsKey.NAS_TEST -> "开始测试"
        SettingsKey.NAS_DEVICE -> store.nas.name
        SettingsKey.NAS_HOST -> store.nas.host.ifBlank { "未设置" }
        SettingsKey.NAS_PORT -> store.nas.port.toString()
        SettingsKey.NAS_ACCOUNT -> store.nas.username.ifBlank { "未设置" }
        SettingsKey.NAS_PASSWORD -> if (store.nas.password.isBlank()) "未设置" else "已设置"
        SettingsKey.NAS_ACCESS_CODE -> if (store.nas.accessCode.isBlank()) "未启用" else "已设置"
        SettingsKey.SS_IDLE -> SettingsStore.formatIdleTimeout(s.idleTimeoutSec)
        SettingsKey.SS_MODE -> s.mode.label
        SettingsKey.SS_SOURCE -> s.photoSource.label
        SettingsKey.SS_ALBUM -> s.albumName.ifBlank {
            if (MediaCache.albums.isEmpty()) "暂无相册" else "未选择"
        }
        SettingsKey.SS_INTERVAL -> SettingsStore.formatInterval(s.photoIntervalSec)
        SettingsKey.SS_CLOCK -> s.clockStyle.label
        SettingsKey.SS_BACKGROUND -> s.background.label
        SettingsKey.SS_DATE,
        SettingsKey.SS_NIGHT_DIM,
        SettingsKey.SS_KEN_BURNS,
        SettingsKey.SS_ENABLED,
        SettingsKey.PB_AUTOPLAY,
        SettingsKey.PB_LOOP,
        -> if (toggleState(key, store) == true) "开" else "关"

        SettingsKey.ABOUT_VERSION -> "1.0.0-prototype"
        SettingsKey.ABOUT_ENGINE -> "Compose"
        SettingsKey.ABOUT_DEVICE -> "Android TV"
        SettingsKey.NAS_REFRESH,
        SettingsKey.SS_PREVIEW,
        SettingsKey.ABOUT_DESIGN,
        SettingsKey.ABOUT_RESET,
        -> ""
    }
}

/** null = 非开关行；true/false = 当前开关状态 */
fun toggleState(key: SettingsKey, store: SettingsStore): Boolean? {
    val s = store.screensaver
    val p = store.playback
    return when (key) {
        SettingsKey.SS_ENABLED -> s.enabled
        SettingsKey.SS_DATE -> s.showDate
        SettingsKey.SS_NIGHT_DIM -> s.nightDim
        SettingsKey.SS_KEN_BURNS -> s.kenBurns
        SettingsKey.PB_AUTOPLAY -> p.autoPlayNext
        SettingsKey.PB_LOOP -> p.loopMode
        else -> null
    }
}

/** 动作行：ENTER 立即执行，行尾画图标而非值胶囊 */
fun isActionRow(key: SettingsKey): Boolean = key in ACTION_KEYS

private val ACTION_KEYS = setOf(
    SettingsKey.NAS_TEST,
    SettingsKey.NAS_REFRESH,
    SettingsKey.SS_PREVIEW,
    SettingsKey.ABOUT_DESIGN,
    SettingsKey.ABOUT_RESET,
)

@Composable
private fun ActionTail(key: SettingsKey, feedback: SettingsFeedback, focused: Boolean) {
    val tint = if (focused) TvColor.TextPrimary else TvColor.TextSecondary
    when (key) {
        SettingsKey.NAS_TEST -> when {
            feedback.nasTesting -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = TvColor.Accent,
                strokeWidth = 2.dp,
            )

            feedback.nasTestOk == true -> Icon(
                Icons.Outlined.CheckCircle, null, Modifier.size(22.dp), tint = TvColor.Accent,
            )

            feedback.nasTestOk == false -> Icon(
                Icons.Outlined.ErrorOutline, null, Modifier.size(22.dp), tint = TvColor.Danger,
            )

            else -> Chevron(tint)
        }

        SettingsKey.SS_PREVIEW -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(18.dp), tint = TvColor.TextTertiary)
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Text("全屏", fontSize = TvType.Tiny, color = TvColor.TextTertiary)
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Chevron(tint)
        }

        SettingsKey.ABOUT_RESET -> if (feedback.resetArmed) {
            Text(
                text = "再按一次确认",
                fontSize = TvType.Tiny,
                fontWeight = FontWeight.SemiBold,
                color = TvColor.Danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(TvDimen.RadiusS))
                    .background(TvColor.Danger.copy(alpha = 0.14f))
                    .padding(horizontal = TvDimen.SpaceS, vertical = 4.dp),
            )
        } else {
            Chevron(tint)
        }

        else -> Chevron(tint)
    }
}

@Composable
private fun Chevron(tint: Color) {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = tint.copy(alpha = 0.75f),
    )
}

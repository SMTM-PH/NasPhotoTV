package com.nasphoto.tv.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * 右栏 · 连接状态：电视 ↔ NAS 的真实拓扑与指标。
 * 没有 mock 的路由器 / 带宽 / 假延迟：这里只展示来自 NAS 的真实结果。
 */
@Composable
fun SettingsNasPanel(store: SettingsStore, feedback: SettingsFeedback, modifier: Modifier = Modifier) {
    val nas = store.nas
    val testing = feedback.nasTesting
    val failed = feedback.nasTestOk == false

    val flow by rememberInfiniteTransition(label = "nasFlow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (testing) 900 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nasFlowPhase",
    )

    val lineColor = when {
        failed -> TvColor.Danger
        testing -> TvColor.Warn
        feedback.nasTestOk == true -> TvColor.Accent
        else -> TvColor.TextTertiary
    }

    Column(modifier.fillMaxWidth()) {
        PanelTitle(
            "连接状态",
            if (nas.isConfigured) {
                "飞牛相册 · ${nas.host}:${nas.port}"
            } else {
                "尚未配置：在左侧填写地址、账号与密码"
            },
        )

        feedback.nasTestDetail?.let { detail ->
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(
                text = "测试通过：$detail",
                fontSize = TvType.Tiny,
                color = TvColor.Accent.copy(alpha = 0.92f),
                maxLines = 2,
            )
        }
        feedback.nasTestError?.let { error ->
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(
                text = "测试失败：$error",
                fontSize = TvType.Tiny,
                color = TvColor.Danger.copy(alpha = 0.92f),
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(TvDimen.SpaceM))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
            ) {
                NodeCapsule(
                    icon = { Icon(Icons.Outlined.Tv, null, Modifier.size(22.dp), tint = TvColor.AccentAlt) },
                    title = "这台电视",
                    subtitle = "Android TV · 遥控器",
                    accent = TvColor.AccentAlt,
                    active = true,
                )

                Connector(
                    phase = flow,
                    color = lineColor,
                    animated = testing || feedback.nasTestOk == true,
                    leftLabel = "局域网",
                    rightLabel = when {
                        testing -> "探测中…"
                        failed -> "未连接"
                        feedback.nasTestOk == true -> "已连接"
                        else -> "未测试"
                    },
                )

                NodeCapsule(
                    icon = {
                        Icon(
                            Icons.Outlined.Dns,
                            null,
                            Modifier.size(22.dp),
                            tint = if (failed) TvColor.Danger else TvColor.Accent,
                        )
                    },
                    title = nas.name.ifBlank { "Home-NAS" },
                    subtitle = "${nas.host.ifBlank { "未设置" }}:${nas.port} · 飞牛相册",
                    accent = if (failed) TvColor.Danger else TvColor.Accent,
                    active = nas.isConfigured && !failed,
                )
            }

            Spacer(Modifier.width(TvDimen.SpaceM))

            Column(
                Modifier.width(210.dp),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
            ) {
                MetricRow(
                    "状态",
                    when {
                        testing -> "探测中…"
                        failed -> "连接失败"
                        feedback.nasTestOk == true -> "已连接"
                        else -> if (nas.isConfigured) "待测试" else "未配置"
                    },
                    if (failed) TvColor.Danger else TvColor.Accent,
                )
                MetricRow(
                    "延迟",
                    if (failed || feedback.nasTestOk != true) "—" else "${nas.latencyMs} ms",
                    if (failed) TvColor.TextTertiary else TvColor.TextPrimary,
                )
                MetricRow("账号", nas.username.ifBlank { "—" }, TvColor.TextSecondary)
                MetricRow(
                    "密码",
                    if (nas.password.isBlank()) "未设置" else "已设置",
                    if (nas.password.isBlank()) TvColor.Warn else TvColor.TextSecondary,
                )
                MetricRow(
                    "访问码",
                    if (nas.accessCode.isBlank()) "未启用" else "已设置",
                    TvColor.TextSecondary,
                )
                MetricRow("照片", "${MediaCache.photoCount} 张", TvColor.TextSecondary)
                MetricRow("相册", "${MediaCache.albums.size} 个", TvColor.TextSecondary)
            }
        }
    }
}

/** 拓扑上的一个“节点胶囊” */
@Composable
private fun NodeCapsule(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    accent: Color,
    active: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(TvDimen.RadiusL))
            .background(TvColor.BgElevated)
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = if (active) 0.38f else 0.16f),
                shape = RoundedCornerShape(TvDimen.RadiusL),
            )
            .padding(horizontal = TvDimen.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (active) 0.14f else 0.07f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(TvDimen.SpaceM))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = TvType.CardTitle,
                fontWeight = FontWeight.Medium,
                color = if (active) TvColor.TextPrimary else TvColor.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 节点之间的连线：一条带流动光点的虚线 */
@Composable
private fun Connector(
    phase: Float,
    color: Color,
    animated: Boolean,
    leftLabel: String,
    rightLabel: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = TvDimen.SpaceM + 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .height(26.dp),
        ) {
            val y = size.height / 2f
            val dash = 10.dp.toPx()
            val gap = 8.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = color.copy(alpha = 0.28f),
                    start = Offset(x, y),
                    end = Offset((x + dash).coerceAtMost(size.width), y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                x += dash + gap
            }
            if (animated) {
                repeat(2) { i ->
                    val p = ((phase + i * 0.5f) % 1f)
                    val px = size.width * p
                    drawCircle(color = color.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = Offset(px, y))
                    drawCircle(color = color, radius = 3.2.dp.toPx(), center = Offset(px, y))
                }
            }
        }
        Spacer(Modifier.width(TvDimen.SpaceS))
        Text(leftLabel, fontSize = TvType.Tiny, color = TvColor.TextTertiary)
        Text(" · ", fontSize = TvType.Tiny, color = TvColor.TextTertiary.copy(alpha = 0.6f))
        Text(rightLabel, fontSize = TvType.Tiny, color = color.copy(alpha = 0.85f))
    }
}

/** 指标：左标签右值 */
@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TvType.Tiny, color = TvColor.TextTertiary)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = TvType.Caption,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

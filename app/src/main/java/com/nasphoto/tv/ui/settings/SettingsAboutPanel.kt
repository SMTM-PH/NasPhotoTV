package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 右栏 · 关于（settings-dev 维护） =================
 *
 * Logo + 版本 + 一行"这套 UI 的设计原则"；按「设计说明」行会展开 5 条原则。
 */
@Composable
fun SettingsAboutPanel(store: SettingsStore, feedback: SettingsFeedback, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(TvDimen.RadiusL))
                    .background(
                        Brush.linearGradient(listOf(TvColor.Accent.copy(alpha = 0.85f), TvColor.AccentAlt.copy(alpha = 0.85f))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = TvColor.Bg,
                )
            }
            Spacer(Modifier.width(TvDimen.SpaceM))
            Column {
                Text(
                    text = "NAS Photo TV",
                    fontSize = TvType.Section,
                    fontWeight = FontWeight.SemiBold,
                    color = TvColor.TextPrimary,
                )
                Text(
                    text = "版本 1.0.0 · Kotlin + Jetpack Compose",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(TvDimen.SpaceL))

        InfoLine("媒体源", "${store.nas.name} · ${store.nas.displayPath}")
        InfoLine("照片数", "${MediaCache.photoCount} 项")
        InfoLine("视频数", "${MediaCache.videoCount} 项")
        InfoLine("相册数", "${MediaCache.albums.size} 个")
        InfoLine("分辨率", "1920 x 1080 · 10-foot UI")
        InfoLine("交互", "遥控器 D-Pad · 无触屏")

        Spacer(Modifier.height(TvDimen.SpaceL))

        if (feedback.showDesignDetail) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TvDimen.RadiusM))
                    .background(TvColor.BgElevated)
                    .padding(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
            ) {
                Text(
                    text = "设计原则",
                    fontSize = TvType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = TvColor.Accent,
                )
                listOf(
                    "10 英尺可读：正文 20sp 起，辅助信息不小于 13sp，一条信息不要挤两行。",
                    "焦点永不丢失：任何可聚焦项都有白描边 + 底色变化，不靠颜色单独表达。",
                    "安静的颜色：底色近黑，强调色只用于「当前项」和「可操作」，一屏不超过两处高饱和。",
                    "防烧屏：屏保整体像素位移 + 定时调暗，文字不用纯白（90%）。",
                    "遥控器优先：左右调值、上下移行、ENTER 执行、BACK 退出，所有行行为一致。",
                ).forEach { line ->
                    Row {
                        Text("· ", fontSize = TvType.Tiny, color = TvColor.TextTertiary)
                        Text(line, fontSize = TvType.Tiny, color = TvColor.TextSecondary)
                    }
                }
            }
        } else {
            Text(
                text = "聚焦「设计说明」后按 ENTER 展开这套 10-foot UI 的设计原则。",
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
            )
        }

        Spacer(Modifier.height(TvDimen.SpaceL))

        Row(
            Modifier
                .clip(CircleShape)
                .background(TvColor.SurfaceHigh.copy(alpha = 0.45f))
                .padding(horizontal = TvDimen.SpaceM, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(TvColor.Accent),
            )
            Spacer(Modifier.width(TvDimen.SpaceXs))
            Text(
                text = "数据来自飞牛 NAS · 加密连接与签名请求",
                fontSize = TvType.Tiny,
                color = TvColor.TextSecondary,
            )
        }
    }
}

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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.common.Placeholder
import com.nasphoto.tv.ui.common.colorForUrl
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * 右栏 · 视频播放：用 NAS 上真实的第一条视频做封面预览，右侧是实际生效的两条播放参数。
 */
@Composable
fun SettingsPlaybackPanel(store: SettingsStore, modifier: Modifier = Modifier) {
    val p = store.playback
    val sample = MediaCache.recent.firstOrNull { it.type == MediaType.VIDEO }

    Column(modifier.fillMaxWidth()) {
        PanelTitle("播放摘要", "播放器启动时使用的默认参数")
        Spacer(Modifier.height(TvDimen.SpaceL))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .width(400.dp)
                    .height(225.dp)
                    .clip(RoundedCornerShape(TvDimen.RadiusL))
                    .background(TvColor.BgElevated),
            ) {
                if (sample != null) {
                    MediaImage(
                        url = sample.thumbnailUrl,
                        contentDescription = sample.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(225.dp),
                        targetWidth = 720,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = Color.White.copy(alpha = 0.92f),
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(TvDimen.SpaceM),
                    ) {
                        Text(
                            text = sample.title,
                            fontSize = TvType.Caption,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.94f),
                        )
                        Text(
                            text = listOfNotNull(
                                sample.resolution.takeIf { it.isNotBlank() },
                                sample.extension.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            fontSize = TvType.Tiny,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                } else {
                    Placeholder(colorForUrl("no-video"), Modifier.fillMaxWidth().height(225.dp))
                    Text(
                        text = "NAS 上还没有视频",
                        fontSize = TvType.Caption,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(Modifier.width(TvDimen.SpaceL))

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
            ) {
                ParamRow("自动连播", p.autoPlayNext)
                ParamRow("循环播放", p.loopMode)
            }
        }

        Spacer(Modifier.height(TvDimen.SpaceL))

        val summary = buildList {
            if (p.autoPlayNext) add("播完自动播放下一个") else add("播完停在当前画面")
            if (p.loopMode) add("单条循环") else add("不循环")
        }.joinToString(" · ")

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TvDimen.RadiusM))
                .background(TvColor.BgElevated)
                .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = TvColor.AccentAlt.copy(alpha = 0.85f),
            )
            Spacer(Modifier.width(TvDimen.SpaceS))
            Text(summary, fontSize = TvType.Tiny, color = TvColor.TextSecondary)
        }
    }
}

/** 一条参数：开/关 + 圆点 */
@Composable
private fun ParamRow(label: String, on: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) TvColor.Accent else TvColor.TextTertiary.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(TvDimen.SpaceS))
        Text(label, fontSize = TvType.Caption, color = TvColor.TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (on) "开启" else "关闭",
            fontSize = TvType.Caption,
            fontWeight = FontWeight.Medium,
            color = if (on) TvColor.Accent else TvColor.TextTertiary,
        )
    }
}

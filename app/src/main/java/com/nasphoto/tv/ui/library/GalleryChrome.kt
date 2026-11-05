package com.nasphoto.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/** 客厅影廊：身份与操作在上，内容导航在下。两个密度下保持相同焦点顺序。 */
@Composable
internal fun GalleryTopBar(
    tabs: List<LibraryTab>, selectedTab: LibraryTab,
    connectionName: String, isOnline: Boolean,
    onTabClick: (LibraryTab) -> Unit, onTabFocus: (Int) -> Unit,
    onActionFocus: (Int) -> Unit,
    tabFocusRequesters: List<FocusRequester>, actionFocusRequesters: List<FocusRequester>,
    onHome: () -> Unit, onScreensaver: () -> Unit, onSettings: () -> Unit,
    accountName: String, accountCount: Int, onAccountClick: () -> Unit,
    onAccountDown: () -> Unit, accountFocusRequester: FocusRequester?,
    onTabDown: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 1200.dp
        Row(
            Modifier.fillMaxWidth()
                .padding(start = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding,
                    top = TvDimen.HeaderInset, bottom = TvDimen.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) TvDimen.SpaceS else TvDimen.SpaceL),
        ) {
            TvFocusBox(focusRequester = actionFocusRequesters.getOrNull(0),
                cornerRadiusDp = 10, focusedScale = 1.025f,
                onConfirm = onHome, onFocusChanged = { if (it) onActionFocus(0) },
                onDown = { tabFocusRequesters.getOrNull(selectedTab.ordinal)?.requestFocus() },
                modifier = Modifier.height(TvDimen.CinemaHeaderHeight)
                    .semantics { contentDescription = "首页 · $connectionName · ${if (isOnline) "在线" else "离线"}" }
                    .padding(horizontal = TvDimen.SpaceXs)) {
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = TvColor.Accent,
                        modifier = Modifier.size(TvDimen.IconSize))
                    Text("NAS 相册", color = TvColor.TextPrimary,
                        fontSize = if (compact) TvType.Body else TvType.CardTitle,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(if (isOnline) TvColor.Accent else TvColor.Danger))
                }
            }
            Row(Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TvDimen.SpaceXs)) {
                tabs.forEachIndexed { index, tab ->
                    TvFocusBox(focusRequester = tabFocusRequesters.getOrNull(index),
                        cornerRadiusDp = 10, focusedScale = 1.025f,
                        onFocusChanged = { if (it) onTabFocus(index) },
                        onConfirm = { onTabClick(tab) }, onDown = { onTabDown(index) },
                        modifier = Modifier.weight(1f).height(TvDimen.CinemaHeaderHeight)) { focused ->
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(tab.label, color = if (focused || tab == selectedTab) TvColor.TextPrimary else TvColor.TextSecondary,
                                fontSize = if (compact) TvType.Caption else TvType.Body,
                                fontWeight = if (tab == selectedTab) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tab == selectedTab) Box(Modifier.padding(top = 4.dp).width(24.dp).height(3.dp)
                                .background(TvColor.Accent))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
                if (accountName.isNotBlank()) CinemaHeaderAction(
                    Icons.Outlined.AccountCircle,
                    if (accountCount > 1) "$accountName · 切换账号" else accountName,
                    accountFocusRequester, onAccountClick, {}, onAccountDown)
                CinemaHeaderAction(Icons.Outlined.Slideshow, "屏保", actionFocusRequesters.getOrNull(1),
                    onScreensaver, { onActionFocus(1) },
                    { tabFocusRequesters.getOrNull(selectedTab.ordinal)?.requestFocus() })
                CinemaHeaderAction(Icons.Outlined.Settings, "设置", actionFocusRequesters.getOrNull(2),
                    onSettings, { onActionFocus(2) },
                    { tabFocusRequesters.getOrNull(selectedTab.ordinal)?.requestFocus() })
            }
        }
    }
}

@Composable
private fun CinemaHeaderAction(icon: ImageVector, label: String, requester: FocusRequester?,
    onClick: () -> Unit, onFocus: () -> Unit, onDown: () -> Unit) {
    TvFocusBox(focusRequester = requester, cornerRadiusDp = 10,
        onConfirm = onClick, onDown = onDown, focusedScale = 1.025f,
        onFocusChanged = { if (it) onFocus() },
        modifier = Modifier.size(TvDimen.CinemaHeaderHeight)) { focused ->
        Icon(icon, label, tint = if (focused) TvColor.Accent else TvColor.TextPrimary,
            modifier = Modifier.align(Alignment.Center).size(TvDimen.IconSize))
    }
}

/** 信息与照片分区，文字不压住照片主体。Hero 只持有一个焦点。 */
@Composable
internal fun GalleryHero(hero: HeroInfo, focusRequester: FocusRequester?, onOpen: () -> Unit,
    onFocusChanged: (Boolean) -> Unit, modifier: Modifier, onDown: () -> Unit,
    onUp: (() -> Unit)? = null) {
    TvFocusBox(modifier = modifier.clip(RoundedCornerShape(TvDimen.RadiusL)).background(TvColor.Stage),
        focusRequester = focusRequester, cornerRadiusDp = 24,
        focusedScale = TvDimen.FocusScaleHero, onConfirm = onOpen,
        onFocusChanged = onFocusChanged, onDown = onDown, onUp = onUp) { focused ->
        BoxWithConstraints(Modifier.fillMaxSize().blockPointer()) {
            val compact = maxWidth < 1100.dp
            val inset = if (compact) TvDimen.SpaceM else TvDimen.SpaceXl
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxHeight().weight(0.36f).padding(inset),
                    verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
                        Box(Modifier.width(3.dp).height(TvDimen.SpaceM).background(TvColor.Accent))
                        Text("最近的时光", color = TvColor.Warm, fontSize = TvType.Caption)
                    }
                    Spacer(Modifier.height(if (compact) TvDimen.SpaceS else TvDimen.SpaceL))
                    Text(hero.title, color = TvColor.TextPrimary,
                        fontSize = if (compact) TvType.Section else TvType.Display,
                        fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(TvDimen.SpaceS))
                    Text(hero.dateText, color = TvColor.TextSecondary, fontSize = TvType.Caption,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(if (compact) TvDimen.SpaceM else TvDimen.SpaceL))
                    Row(Modifier.clip(RoundedCornerShape(TvDimen.RadiusS))
                        .background(if (focused) TvColor.Accent else TvColor.SurfaceHigh)
                        .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
                        Icon(Icons.Outlined.PlayArrow, null,
                            tint = if (focused) TvColor.InkOnAccent else TvColor.TextPrimary,
                            modifier = Modifier.size(TvDimen.IconSize))
                        Text(if (hero.item.type == MediaType.VIDEO) "播放视频" else "打开照片",
                            color = if (focused) TvColor.InkOnAccent else TvColor.TextPrimary,
                            fontSize = TvType.Caption, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.weight(0.64f).fillMaxHeight()) {
                    MediaImage(hero.item.thumbnailUrl.ifBlank { hero.item.url }, hero.title,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop, targetWidth = 1440)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                        0f to TvColor.Stage, 0.12f to TvColor.Stage.copy(alpha = 0.1f),
                        0.35f to Color.Transparent)))
                    Box(Modifier.align(Alignment.BottomEnd).padding(inset)
                        .clip(RoundedCornerShape(TvDimen.RadiusS))
                        .background(TvColor.Bg.copy(alpha = 0.72f))
                        .padding(horizontal = TvDimen.SpaceS, vertical = TvDimen.SpaceXs)) {
                        Text("${hero.index + 1} / ${hero.items.size}", color = TvColor.TextPrimary,
                            fontSize = TvType.Caption, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GalleryNavigationHint(label: String, hint: String = "方向键 移动    ·    确认 打开    ·    返回 上一级") {
    Row(Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding,
        vertical = TvDimen.SpaceS), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TvColor.TextMuted, fontSize = TvType.Caption, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.weight(1f))
        Text(hint,
            color = TvColor.TextSecondary, fontSize = TvType.Caption, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

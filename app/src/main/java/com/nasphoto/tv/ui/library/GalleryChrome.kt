/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.R
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/** 主导航的下划线独立于遥控器焦点。 */
@Composable
internal fun GalleryTopBar(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    connectionName: String,
    isOnline: Boolean,
    isConnecting: Boolean,
    tabFocusRequesters: List<FocusRequester>,
    homeRequester: FocusRequester,
    artAlbumRequester: FocusRequester,
    screensaverRequester: FocusRequester,
    phonePhotosRequester: FocusRequester,
    settingsRequester: FocusRequester,
    onTabClick: (LibraryTab) -> Unit,
    onTabDown: () -> Unit,
    onHome: () -> Unit,
    onArtAlbum: () -> Unit,
    onScreensaver: () -> Unit,
    onPhonePhotos: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        isConnecting -> "正在连接 NAS"
        isOnline -> "在线"
        else -> "离线"
    }
    val statusColor = when {
        isConnecting -> TvColor.TextTertiary
        isOnline -> TvColor.Accent
        else -> TvColor.Danger
    }
    val activeIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val focusActiveTab: () -> Unit = { tabFocusRequesters.getOrNull(activeIndex)?.requestFocus() }
    val largeText = LocalDensity.current.fontScale > 1.35f
    val controlHeight = if (largeText) 54.dp else 44.dp
    Row(
        modifier.fillMaxWidth().background(TvColor.Bg.copy(alpha = 0.94f))
            .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
    ) {
        TvFocusBox(
            focusRequester = homeRequester,
            cornerRadiusDp = 10,
            focusedScale = 1.025f,
            onConfirm = onHome,
            onDown = focusActiveTab,
            modifier = Modifier.height(controlHeight).semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "首页 · $connectionName · $status"
            }.padding(horizontal = TvDimen.SpaceXs),
        ) {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(TvDimen.RadiusS)),
                )
                Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor))
            }
        }
        GalleryTabs(
            tabs = tabs,
            activeIndex = activeIndex,
            controlHeight = controlHeight,
            focusRequesters = tabFocusRequesters,
            artAlbumRequester = artAlbumRequester,
            onClick = onTabClick,
            onArtAlbum = onArtAlbum,
            onDown = onTabDown,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaHeaderAction(
                R.drawable.ic_gallery_screensaver, "屏保", !largeText, controlHeight,
                screensaverRequester, onScreensaver, focusActiveTab,
            )
            CinemaHeaderAction(
                R.drawable.ic_gallery_phone_photos, "传图", !largeText, controlHeight,
                phonePhotosRequester, onPhonePhotos, focusActiveTab, accessibilityLabel = "手机传图",
            )
            CinemaHeaderAction(
                R.drawable.ic_gallery_settings, "设置", !largeText, controlHeight,
                settingsRequester, onSettings, focusActiveTab,
            )
        }
    }
}

@Composable
private fun GalleryTabs(
    tabs: List<LibraryTab>,
    activeIndex: Int,
    controlHeight: Dp,
    focusRequesters: List<FocusRequester>,
    artAlbumRequester: FocusRequester,
    onClick: (LibraryTab) -> Unit,
    onArtAlbum: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(TvDimen.RadiusM)
    val tabGap = 4.dp
    val indicatorWidth = 22.dp
    val artAlbumWeight = 1.35f
    BoxWithConstraints(
        modifier.background(TvColor.Stage.copy(alpha = 0.82f), shape)
            .border(1.dp, TvColor.Divider, shape)
            .padding(4.dp),
    ) {
        val tabWidth = (maxWidth - tabGap * tabs.size) / (tabs.size + artAlbumWeight)
        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth + tabGap) * activeIndex + (tabWidth - indicatorWidth) / 2,
            animationSpec = tween(210),
            label = "galleryTabIndicator",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tabGap)) {
            tabs.forEachIndexed { index, tab ->
                GalleryTabControl(
                    label = tab.label,
                    selectedTab = index == activeIndex,
                    requester = focusRequesters[index],
                    onClick = { onClick(tab) },
                    onDown = onDown,
                    modifier = Modifier.weight(1f).height(controlHeight),
                )
            }
            GalleryTabControl(
                label = "艺术画册",
                selectedTab = null,
                requester = artAlbumRequester,
                onClick = onArtAlbum,
                onDown = onDown,
                modifier = Modifier.weight(artAlbumWeight).height(controlHeight),
            )
        }
        Box(
            Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(indicatorOffset.roundToPx(), -4.dp.roundToPx()) }
                .size(width = indicatorWidth, height = 3.dp)
                .background(TvColor.Accent, CircleShape),
        )
    }
}

@Composable
private fun GalleryTabControl(
    label: String,
    selectedTab: Boolean?,
    requester: FocusRequester,
    onClick: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier,
) {
    val active = selectedTab == true
    TvFocusBox(
        focusRequester = requester,
        cornerRadiusDp = 10,
        focusedScale = 1.02f,
        onConfirm = onClick,
        onDown = onDown,
        modifier = modifier.semantics(mergeDescendants = true) {
            if (selectedTab != null) {
                role = Role.Tab
                selected = selectedTab
            } else {
                role = Role.Button
            }
        },
    ) { focused ->
        val surface by animateColorAsState(
            targetValue = when {
                active -> TvColor.AccentDim
                focused -> TvColor.SurfaceSoft
                else -> Color.Transparent
            },
            animationSpec = tween(130),
            label = "galleryTabSurface",
        )
        Box(
            Modifier.fillMaxSize().background(surface, RoundedCornerShape(TvDimen.RadiusS)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = when {
                    active -> TvColor.Accent
                    focused -> TvColor.TextPrimary
                    else -> TvColor.TextSecondary
                },
                modifier = Modifier.padding(bottom = 3.dp),
                fontSize = TvType.Label,
                fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CinemaHeaderAction(
    @DrawableRes icon: Int,
    label: String,
    showLabel: Boolean,
    controlHeight: Dp,
    requester: FocusRequester,
    onClick: () -> Unit,
    onDown: () -> Unit,
    accessibilityLabel: String = label,
) {
    TvFocusBox(
        focusRequester = requester,
        cornerRadiusDp = 10,
        onConfirm = onClick,
        onDown = onDown,
        focusedScale = 1.025f,
        modifier = Modifier.size(width = 46.dp, height = controlHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibilityLabel
            },
    ) { focused ->
        Column(
            Modifier.fillMaxSize().background(
                if (focused) TvColor.SurfaceHigh else Color.Transparent,
                RoundedCornerShape(TvDimen.RadiusS),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            Icon(painterResource(icon), null, tint = if (focused) TvColor.Accent else TvColor.TextPrimary,
                modifier = Modifier.size(if (showLabel) 19.dp else TvDimen.IconSize))
            if (showLabel) {
                Text(label, color = TvColor.TextPrimary, fontSize = 12.sp,
                    lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
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

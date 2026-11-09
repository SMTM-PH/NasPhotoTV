package com.nasphoto.tv.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.pixelSize
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.launch

/** 全屏照片始终在导航及内容的下方，不参与焦点树。 */
@Composable
internal fun CinemaBackdrop(item: MediaItem, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(TvColor.Bg)) {
        Crossfade(
            targetState = item.thumbnailUrl.ifBlank { item.url },
            animationSpec = tween(280),
            label = "cinemaPhoto",
        ) { url ->
            val size = item.pixelSize()
            val wide = size != null && size.first.toFloat() / size.second >= 1.6f
            Box(Modifier.fillMaxSize()) {
                if (!wide) MediaImage(url, null, Modifier.fillMaxSize().alpha(0.18f),
                    contentScale = ContentScale.Crop, targetWidth = 1280)
                MediaImage(url, null,
                    if (wide) Modifier.fillMaxSize() else Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight().fillMaxWidth(0.7f),
                    contentScale = if (wide) ContentScale.Crop else ContentScale.Fit, targetWidth = 1920)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to TvColor.Bg.copy(alpha = 0.96f),
            0.25f to TvColor.Bg.copy(alpha = 0.86f),
            0.5f to TvColor.Bg.copy(alpha = 0.46f),
            0.8f to Color.Transparent,
            1f to TvColor.Bg.copy(alpha = 0.08f),
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to TvColor.Bg.copy(alpha = 0.68f),
            0.2f to Color.Transparent,
            0.5f to Color.Transparent,
            0.78f to TvColor.Bg.copy(alpha = 0.45f),
            1f to TvColor.Bg.copy(alpha = 0.93f),
        )))
    }
}

/**
 * 一张背景、两项操作、一排缩略图。选中照片由 LibraryScreen 按 ID 保存，
 * 聚焦缩略图只更新展示数据；显式导航请求才移动焦点。
 */
@Composable
internal fun ImmersiveHome(
    collections: List<HomeCollection>,
    selectedIndex: Int,
    onSelect: (HomeCollection) -> Unit,
    lastZone: HomeFocusZone,
    onZoneChange: (HomeFocusZone) -> Unit,
    onOpenCollection: (HomeCollection) -> Unit,
    onPlayCollection: (HomeCollection) -> Unit,
    onConfigureDisplay: () -> Unit,
    onOpenAlbumsTab: () -> Unit,
    contentFocus: PendingFocus,
    onUpToTopBar: () -> Unit,
) {
    if (collections.isEmpty()) {
        EmptyImmersiveHome(onConfigureDisplay, contentFocus, onUpToTopBar)
        return
    }
    val safeIndex = selectedIndex.coerceIn(0, collections.lastIndex)
    val collection = collections[safeIndex]
    val item = collection.cover
    val primaryRequester = remember { FocusRequester() }
    val albumsRequester = remember { FocusRequester() }
    val thumbnailRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun thumbnailRequester(id: String) = thumbnailRequesters.getOrPut(id) { FocusRequester() }
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (safeIndex - 1).coerceAtLeast(0))
    val scope = rememberCoroutineScope()

    fun focusStrip() {
        scope.launch {
            stripState.scrollToItem((safeIndex - 1).coerceAtLeast(0))
            withFrameMillis { }
            runCatching { thumbnailRequester(collection.id).requestFocus() }
        }
    }

    // 此页是 contentFocus 的唯一消费者。恢复屏幕外项目时先滚动，再请求焦点。
    val token = contentFocus.token
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        val direct = contentFocus.direct
        val block = contentFocus.block
        val target = when {
            direct != null -> direct
            block?.first == BLOCK_CONTENT -> when (lastZone) {
                HomeFocusZone.OPEN -> primaryRequester
                HomeFocusZone.ALBUMS -> albumsRequester
                else -> {
                    stripState.scrollToItem((safeIndex - 1).coerceAtLeast(0))
                    withFrameMillis { }
                    thumbnailRequester(collection.id)
                }
            }
            else -> null
        }
        if (target != null) {
            repeat(3) {
                if (runCatching { target.requestFocus() }.isSuccess) {
                    contentFocus.consume(token)
                    return@LaunchedEffect
                }
                withFrameMillis { }
            }
        }
        contentFocus.consume(token)
    }
    // 选中改变可滚动胶片条，但不会触发 requestFocus 或打断上方操作。
    LaunchedEffect(collection.id) { scrollRowTo(stripState, safeIndex) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1500.dp || maxHeight < 720.dp
        val titleSize = when {
            maxWidth < 1200.dp -> TvType.CinemaCompactDisplay
            compact -> TvType.Display
            else -> TvType.CinemaDisplay
        }
        val thumbnailWidth = ((maxWidth - TvDimen.ScreenPadding * 2 -
            TvDimen.CinemaRailInset * 2 - TvDimen.SpaceS * 4) / 5).coerceAtLeast(144.dp)
        val thumbnailHeight = thumbnailWidth * 0.56f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth(if (compact) 0.62f else 0.55f)
                        .padding(start = TvDimen.ScreenPadding, end = TvDimen.SpaceM)
                        .padding(top = if (compact) TvDimen.SpaceXs else TvDimen.SpaceXl),
                ) {
                    Text("客厅相册  /  飞牛 AI 智能分类",
                        color = TvColor.Accent, fontSize = TvType.Caption,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(collection.title, color = TvColor.TextPrimary,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(collection.subtitle, color = TvColor.TextPrimary,
                        fontSize = TvType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Spacer(Modifier.height(TvDimen.SpaceS))
                        Text("AI 识别的事物与场景", color = TvColor.TextSecondary,
                            fontSize = TvType.Body)
                    }
                    Spacer(Modifier.height(if (compact) TvDimen.SpaceM else TvDimen.SpaceL))
                    Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                        verticalAlignment = Alignment.CenterVertically) {
                        CinemaAction(
                            label = "播放这个分类",
                            icon = Icons.Outlined.PlayArrow,
                            primary = true, focusRequester = primaryRequester,
                            onClick = { onPlayCollection(collection) },
                            onFocus = { onZoneChange(HomeFocusZone.OPEN) }, onUp = onUpToTopBar,
                            onDown = ::focusStrip,
                        )
                        CinemaAction("浏览相册", Icons.Outlined.Collections, false, albumsRequester,
                            onOpenAlbumsTab, { onZoneChange(HomeFocusZone.ALBUMS) }, onUpToTopBar, ::focusStrip)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically) {
                Text("智能分类", color = TvColor.TextPrimary,
                    fontSize = if (compact) TvType.CardTitle else TvType.Section,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(TvDimen.SpaceS))
                Text("共 ${collections.size} 个分类", color = TvColor.TextSecondary, fontSize = TvType.Caption)
                Spacer(Modifier.weight(1f))
                Text("${safeIndex + 1} / ${collections.size}", color = TvColor.TextPrimary,
                    fontSize = TvType.Caption, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(if (compact) TvDimen.SpaceXs else TvDimen.SpaceM))
            Box(Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding)
                .clip(RoundedCornerShape(TvDimen.RadiusM)).background(TvColor.CinemaPanel)) {
                LazyRow(
                    state = stripState,
                    contentPadding = PaddingValues(horizontal = TvDimen.CinemaRailInset,
                        vertical = TvDimen.SpaceS),
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(collections, key = { _, media -> media.id }) { index, media ->
                        val selected = index == safeIndex
                        val shape = RoundedCornerShape(TvDimen.CinemaThumbnailRadius)
                        TvFocusBox(
                            focusRequester = thumbnailRequester(media.id), cornerRadiusDp = 8,
                            focusedScale = 1.04f, focusRingColor = TvColor.Accent,
                            onFocusChanged = { focused -> if (focused) {
                                onZoneChange(HomeFocusZone.STRIP)
                                onSelect(media)
                            } },
                            onConfirm = { onOpenCollection(media) },
                            onUp = { primaryRequester.requestFocus() },
                            modifier = Modifier.width(thumbnailWidth).height(thumbnailHeight)
                                .semantics { this.selected = selected }
                                .clip(shape)
                                .border(if (selected) 1.dp else 0.dp,
                                    if (selected) TvColor.Accent else Color.Transparent, shape),
                        ) {
                            Box(Modifier.fillMaxSize().blockPointer()) {
                                MediaImage(media.cover.thumbnailUrl.ifBlank { media.cover.url }, media.title,
                                    Modifier.fillMaxSize(), targetWidth = 640)
                                Text(media.title, color = TvColor.TextPrimary, fontSize = TvType.Caption,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(TvColor.Bg.copy(alpha = 0.8f)).padding(TvDimen.SpaceXs))
                                if (media.cover.type == MediaType.VIDEO) {
                                    VideoBadge(formatDuration(media.cover.durationMs), Modifier.align(Alignment.BottomEnd)
                                        .padding(TvDimen.SpaceXs))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(TvDimen.SpaceXs))
        }
    }
}

@Composable
private fun CinemaAction(
    label: String, icon: ImageVector, primary: Boolean, focusRequester: FocusRequester,
    onClick: () -> Unit, onFocus: () -> Unit, onUp: () -> Unit, onDown: () -> Unit,
) {
    val shape = RoundedCornerShape(TvDimen.RadiusS)
    TvFocusBox(focusRequester = focusRequester, cornerRadiusDp = 10,
        focusedScale = 1.025f, onConfirm = onClick, onUp = onUp, onDown = onDown,
        onFocusChanged = { if (it) onFocus() },
        modifier = Modifier.height(TvDimen.ControlHeight).clip(shape)
            .background(TvColor.CinemaPanel)
            .padding(horizontal = TvDimen.SpaceM)) { focused ->
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs)) {
            Icon(icon, null, tint = if (focused) TvColor.Accent else TvColor.TextPrimary,
                modifier = Modifier.size(TvDimen.IconSize))
            Text(label, color = if (focused) TvColor.Accent else TvColor.TextPrimary,
                fontSize = TvType.Caption, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyImmersiveHome(onOpenAlbums: () -> Unit, contentFocus: PendingFocus, onUp: () -> Unit) {
    val requester = remember { FocusRequester() }
    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT) { requester }
    Column(Modifier.fillMaxSize().padding(TvDimen.ScreenPadding),
        verticalArrangement = Arrangement.Center) {
        Text("还没有可展示的智能分类", color = TvColor.TextPrimary, fontSize = TvType.Display)
        Spacer(Modifier.height(TvDimen.SpaceS))
        Text("请在飞牛相册中开启 AI 识别并等待分析完成，识别出的事物与场景会自动出现在这里。",
            color = TvColor.TextSecondary, fontSize = TvType.Body)
        Spacer(Modifier.height(TvDimen.SpaceM))
        CinemaAction("浏览相册", Icons.Outlined.Collections, true, requester,
            onOpenAlbums, {}, onUp, {})
    }
}

internal enum class HomeFocusZone { OPEN, ALBUMS, STRIP }

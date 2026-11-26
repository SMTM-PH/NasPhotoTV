package com.nasphoto.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.DarkPalette
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvImage
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 媒体库页的视觉组件（library-dev 维护） =================
 * 全部组件只做展示 + 焦点回调，"点哪去"由 LibraryScreen 决定。
 */

// ============================== 区块标题 ==============================

@Composable
fun SectionHeader(title: String, trailing: String = "", modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = TvDimen.SpaceXs),
    ) {
        Text(
            text = title,
            color = TvColor.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.alignByBaseline(),
        )
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(TvDimen.SpaceS))
            Text(
                text = trailing,
                color = TvColor.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// ============================== Hero ==============================

/**
 * Hero：左侧信息与操作，右侧照片舞台；缩放与描边只在整体焦点上出现。
 * Hero 整体可聚焦（ENTER = 打开），且焦点时主按钮高亮。
 * 按钮侧的浮层全部忽略指针事件：点击只由 TvFocusBox 自己的 focusable/clickable 处理，保证一次 ENTER 只触发一次。
 */
@Composable
fun LibraryHero(
    hero: HeroInfo,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    onUp: (() -> Unit)? = null,
) {
    GalleryHero(hero, focusRequester, onOpen, onFocusChanged, modifier, onDown, onUp)
}

// ============================== 媒体卡片 ==============================

/** 视频时长角标。黑底角标和照片遮罩都使用固定暗场的文字配色。 */
@Composable
fun VideoBadge(duration: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(TvDimen.RadiusS))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = DarkPalette.textPrimary,
            modifier = Modifier.size(14.dp),
        )
        if (duration.isNotBlank()) {
            Spacer(Modifier.width(2.dp))
            Text(duration, color = DarkPalette.textPrimary, fontSize = TvType.Tiny)
        }
    }
}

/** 卡片/行/网格传入的上下导航：转成 TvFocusBox 的 onUp/onDown（null = 放行给 Compose 空间查找） */
@Composable
fun MediaCard(
    item: MediaItem,
    width: Int,
    height: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showMetaOnFocus: Boolean = true,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 16,
        onFocusChanged = onFocusChanged,
        onConfirm = onClick,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(TvDimen.RadiusM)),
    ) { focused ->
        Box(Modifier.fillMaxSize().blockPointer()) {
            MediaImage(
                url = item.thumbnailUrl.ifBlank { item.url },
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                targetWidth = TvImage.MediaThumbPx,
                cornerRadiusDp = 16,
            )
            if (showMetaOnFocus) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color(0xE6000000),
                            ),
                        ),
                )
            }
            if (item.starred || FavoritesStore.isFavorite(item.id)) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = TvColor.Warn,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
            if (showMetaOnFocus || item.type == MediaType.VIDEO) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(horizontal = TvDimen.SpaceS, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs, Alignment.End),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (showMetaOnFocus) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifBlank { "未命名" },
                                color = DarkPalette.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatShortDate(item.dateTaken),
                                color = DarkPalette.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                    }
                    if (item.type == MediaType.VIDEO) {
                        VideoBadge(duration = formatDuration(item.durationMs))
                    }
                }
            }
        }
    }
}

/**
 * 相册卡片 380x220：封面 + 底部渐变 + 相册名 + 数量；聚焦时显示 subtitle + 青色描边。
 */
@Composable
fun AlbumCard(
    album: Album,
    coverUrl: String,
    width: Int,
    height: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    /** 覆盖卡片第二行文字（文件夹视图用「N 个子文件夹」）；null = 沿用媒体计数逻辑 */
    secondaryText: String? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester, cornerRadiusDp = 16,
        onFocusChanged = onFocusChanged, onConfirm = onClick, onUp = onUp, onDown = onDown,
        modifier = modifier.width(width.dp).height(height.dp)
            .clip(RoundedCornerShape(TvDimen.RadiusM)).background(TvColor.Stage),
    ) { focused ->
        Column(Modifier.fillMaxSize().blockPointer()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MediaImage(coverUrl, album.name, Modifier.fillMaxSize(), targetWidth = TvImage.AlbumCoverPx)
                if (album.isSmart && album.type != AlbumType.SMART_PLACE) Text(smartGroupTitle(album.type), color = TvColor.TextPrimary,
                    fontSize = TvType.Caption,
                    modifier = Modifier.padding(TvDimen.SpaceS).clip(RoundedCornerShape(TvDimen.RadiusS))
                        .background(TvColor.Bg.copy(alpha = 0.8f)).padding(TvDimen.SpaceXs))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(album.name, color = TvColor.TextPrimary, style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 子目录接口不返回媒体计数，不能把未知数量显示成「0 项」。
                    // 文件夹层级视图会传 secondaryText（如「3 个子文件夹」）覆盖默认计数。
                    Text(secondaryText ?: if (album.id.startsWith("folder:path:") && album.itemCount == 0) {
                        if (album.subtitle.startsWith("子文件夹")) "子文件夹" else "文件夹"
                    } else "${album.itemCount} 项", color = TvColor.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (focused) Icon(Icons.Filled.PlayArrow, null, tint = TvColor.Accent,
                    modifier = Modifier.size(TvDimen.IconSize))
            }
        }
    }
}

/**
 * 文件夹层级浏览里的导航卡片（「返回上一级」/「查看本目录照片」）。
 *
 * 与 [AlbumCard] 同尺寸、同焦点行为，只是用图标代替封面图：导航项没有封面，
 * 伪造一张图反而会让人误以为是目录。放在网格首两项，遥控器一进来就能按到。
 */
@Composable
fun FolderNavCard(
    title: String,
    caption: String,
    icon: ImageVector,
    width: Int,
    height: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester, cornerRadiusDp = 16,
        onFocusChanged = onFocusChanged, onConfirm = onClick, onUp = onUp, onDown = onDown,
        modifier = modifier.width(width.dp).height(height.dp)
            .clip(RoundedCornerShape(TvDimen.RadiusM)).background(TvColor.Stage),
    ) {
        Column(Modifier.fillMaxSize().blockPointer()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = TvColor.Accent, modifier = Modifier.size(44.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = TvColor.TextPrimary, fontSize = TvType.Body,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(caption, color = TvColor.TextSecondary, fontSize = TvType.Caption, maxLines = 1)
                }
            }
        }
    }
}

// ============================== 时间线分桶卡片 ==============================

/**
 * 年 / 月 / 日 三级共用的分桶卡片：一张封面打底，左下角是「日期数字 + 数量」。
 * 与相册卡同一套视觉语言，但标题加重字重 —— 时间线的锚点是数字本身，
 * 扫一眼就能定位到「哪一年 / 哪个月 / 哪一天」。
 */
@Composable
fun ChronoCard(
    bucket: ChronoBucket,
    width: Int,
    height: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 20,
        onFocusChanged = onFocusChanged,
        onConfirm = onClick,
        onUp = onUp,
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) { focused ->
        Box(Modifier.fillMaxSize()) {
            MediaImage(
                url = bucket.coverUrl,
                contentDescription = bucket.title,
                modifier = Modifier.fillMaxSize(),
                targetWidth = TvImage.MediaThumbPx,
                cornerRadiusDp = 20,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.30f to Color.Transparent,
                            1f to Color(0xF2000000),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = bucket.title,
                    color = DarkPalette.textPrimary,
                    fontSize = TvType.Section,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bucket.subtitle,
                    color = if (focused) DarkPalette.accent else DarkPalette.textSecondary,
                    fontSize = TvType.Tiny,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================== 空状态 ==============================

/** 空列表占位：图标 + 标题 + 副文案（不可聚焦，避免误触） */
@Composable
fun EmptyState(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Image,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TvColor.TextTertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        Text(title, color = TvColor.TextSecondary, fontSize = TvType.Section, fontWeight = FontWeight.Medium)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(subtitle, color = TvColor.TextTertiary, fontSize = TvType.Caption)
        }
    }
}

/** 行内小空状态（放在 LazyRow 位置，高度较矮） */
@Composable
fun RowEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = TvDimen.ScreenPadding)
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(TvDimen.RadiusM))
            .background(TvColor.BgElevated)
            .border(1.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusM)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TvColor.TextTertiary, fontSize = TvType.Caption)
    }
}

/** 页面级空数据空状态：CTA 可聚焦并跳设置 */
@Composable
fun LibraryEmptyState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val ctaRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { ctaRequester.requestFocus() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = TvColor.TextTertiary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(TvDimen.SpaceM))
            Text("媒体库里还没有内容", color = TvColor.TextPrimary, fontSize = TvType.Section, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text("请检查 NAS 连接，或在设置里重新扫描媒体库", color = TvColor.TextTertiary, fontSize = TvType.Caption)
            Spacer(Modifier.height(TvDimen.SpaceL))
            val shape = RoundedCornerShape(TvDimen.RadiusXL)
            TvFocusBox(
                focusRequester = ctaRequester,
                cornerRadiusDp = 32,
                onConfirm = onOpenSettings,
                modifier = Modifier
                    .clip(shape)
                    .background(TvColor.Accent)
                    .padding(horizontal = TvDimen.SpaceL, vertical = 14.dp),
            ) { focused ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = TvColor.InkOnAccent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(TvDimen.SpaceXs))
                    Text(
                        text = "去设置",
                        color = TvColor.InkOnAccent,
                        fontSize = TvType.Body,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ============================== 相册详情页头 ==============================

/** 详情页顶栏：返回按钮（可聚焦）+ 相册名 + 数量 */
@Composable
fun AlbumDetailHeader(
    albumName: String,
    count: String,
    backFocusRequester: FocusRequester?,
    onBack: () -> Unit,
    onBackFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    playAction: (() -> Unit)? = null,
    playRequester: FocusRequester? = null,
    slideshowSettingsAction: (() -> Unit)? = null,
    slideshowSettingsRequester: FocusRequester? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvFocusBox(
            focusRequester = backFocusRequester,
            cornerRadiusDp = 24,
            focusedScale = 1.06f,
            onFocusChanged = onBackFocusChanged,
            onConfirm = onBack,
            modifier = Modifier
                .focusProperties { if (playRequester != null) right = playRequester }
                .clip(CircleShape)
                .background(TvColor.Surface)
                .verticalNav(onDown = onDown)
                .padding(horizontal = TvDimen.SpaceM, vertical = 8.dp),
        ) { focused ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = if (focused) TvColor.Accent else TvColor.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("返回", color = if (focused) TvColor.TextPrimary else TvColor.TextSecondary, fontSize = TvType.Caption)
            }
        }
        Spacer(Modifier.width(TvDimen.SpaceL))
        // 标题与数量同一行：详情页顶部越矮，网格能完整展示的行数越多。
        Text(
            albumName,
            color = TvColor.TextPrimary,
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(TvDimen.SpaceS))
        Text(
            count,
            color = TvColor.TextTertiary,
            fontSize = TvType.Caption,
            maxLines = 1,
        )
        if (playAction != null) {
            Spacer(Modifier.weight(1f, fill = true))
            DetailAction(
                "播放此相册",
                focusRequester = playRequester,
                modifier = Modifier
                    .focusProperties {
                        if (backFocusRequester != null) left = backFocusRequester
                        if (slideshowSettingsRequester != null) right = slideshowSettingsRequester
                    }
                    .verticalNav(onDown = onDown),
                onClick = playAction,
            )
            if (slideshowSettingsAction != null) {
                Spacer(Modifier.width(TvDimen.SpaceS))
                DetailAction(
                    "放映设置",
                    focusRequester = slideshowSettingsRequester,
                    modifier = Modifier
                        .focusProperties { if (playRequester != null) left = playRequester }
                        .verticalNav(onDown = onDown),
                    onClick = slideshowSettingsAction,
                )
            }
        }
    }
}

/** 一格「打开文件夹」卡片（相机卷风格），用于首页相册行的末尾 */
@Composable
internal fun DetailAction(
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    TvFocusBox(
        modifier = modifier
            .heightIn(min = TvDimen.CinemaHeaderHeight)
            .background(TvColor.Surface, RoundedCornerShape(12.dp)),
        focusRequester = focusRequester,
        cornerRadiusDp = 12,
        onConfirm = onClick,
    ) { focused ->
        Text(
            label,
            color = if (focused) TvColor.Accent else TvColor.TextPrimary,
            fontSize = TvType.Caption,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun OpenFolderCard(
    width: Int,
    height: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 20,
        onFocusChanged = onFocusChanged,
        onConfirm = onClick,
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clip(shape)
            .background(TvColor.BgElevated)
            .border(1.dp, TvColor.Divider, shape)
            .verticalNav(onUp = onUp),
    ) { focused ->
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Album,
                contentDescription = null,
                tint = if (focused) TvColor.Accent else TvColor.TextTertiary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(TvDimen.SpaceS))
            Text("查看全部相册", color = TvColor.TextSecondary, fontSize = TvType.Caption)
        }
    }
}

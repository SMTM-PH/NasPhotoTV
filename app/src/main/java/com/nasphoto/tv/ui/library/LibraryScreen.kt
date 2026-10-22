package com.nasphoto.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.AlbumType
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.pixelSize
import com.nasphoto.tv.ui.AppRoute
import com.nasphoto.tv.ui.LibraryState
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= 媒体库页（首页 / 相册 / 文件夹浏览） =================
 *
 * 【焦点模型】完全不依赖 Compose 自动焦点顺序：
 *  - 顶栏：Tab 行 / 图标按钮行 各自共享一个 PendingFocus，按索引聚焦；
 *  - 主内容：每个卡片聚焦时记下自己的 index，配合 [PendingFocus] 做「跨行同列」落点；
 *  - 行内左右 = Lazy 默认顺序；上下跨行 = 卡片上的 [verticalNav] 显式处理；
 *  - 焦点放大溢出：所有网格/行都以 TvDimen.ScreenPadding 作为 contentPadding，
 *    卡片缩放 1.08 的溢出（每边 4%）不会贴边被裁。
 *
 * 【空数据】空列表一律走空状态分支（NAS 尚未加载/为空时显示引导页，不崩）。
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onOpenPhoto: (items: List<MediaItem>, index: Int, contextTitle: String) -> Unit,
    onOpenVideo: (items: List<MediaItem>, index: Int, contextTitle: String) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable(state.selectedTabIndex) {
        mutableStateOf(initialTabOf(state.selectedTabIndex))
    }
    var openedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }

    // 派生数据（全部空安全）
    val hero = remember(state) { buildHero(state) }
    val allItems = remember(state) { allPhotos(state) }
    val favorites = remember(state, FavoritesStore.ids) { favoriteItems(state) }
    val openedAlbum = openedAlbumId?.let { id -> state.albums.firstOrNull { it.id == id } }

    // ---- 顶栏焦点 ----
    val tabRequesters = remember { LibraryTab.entries.map { FocusRequester() } }
    val actionRequesters = remember { List(TOP_ACTION_COUNT) { FocusRequester() } }
    val topFocus = remember { PendingFocus() }   // 顶栏精确落点
    val contentFocus = remember { PendingFocus() } // 主内容精确落点
    var lastTab by remember { mutableIntStateOf(0) }
    var lastAction by remember { mutableIntStateOf(DEFAULT_ACTION) }
    var hasInitialFocus by remember { mutableStateOf(false) }

    // 首次进入：焦点落到 Hero（无 Hero 时落到顶栏当前 Tab）
    if (!hasInitialFocus) {
        LaunchedEffect(Unit) {
            hasInitialFocus = true
            if (hero != null) contentFocus.requestBlock(BLOCK_CONTENT) else topFocus.request(tabRequesters.getOrNull(lastTab))
        }
    }

    // BACK：相册详情 → 返回列表（列表页的 BACK 由 Lead 外壳处理）
    BackHandler(enabled = openedAlbum != null) { openedAlbumId = null }

    LibraryBackdrop(modifier.fillMaxSize())

    Column(Modifier.fillMaxSize()) {
        LibraryTopBar(
            tabs = LibraryTab.entries,
            selectedTab = selectedTab,
            connectionName = state.connectionName,
            isOnline = state.isOnline,
            onTabClick = { tab ->
                selectedTab = tab
                openedAlbumId = null
            },
            onTabFocus = { lastTab = it },
            onActionFocus = { lastAction = it },
            tabFocusRequesters = tabRequesters,
            actionFocusRequesters = actionRequesters,
            onSearch = { selectedTab = LibraryTab.ALL },
            onScreensaver = { onNavigate(AppRoute.ScreensaverPreview) },
            onSettings = { onNavigate(AppRoute.Settings) },
            onTabDown = { contentFocus.requestBlock(BLOCK_CONTENT) },
        )

        val isEmptyLibrary = state.recent.isEmpty() && state.albums.isEmpty()
        when {
            isEmptyLibrary -> LibraryEmptyState(onOpenSettings = { onNavigate(AppRoute.Settings) })

            openedAlbum != null -> AlbumDetailPane(
                state = state,
                album = openedAlbum,
                onBack = { openedAlbumId = null },
                onOpenPhoto = onOpenPhoto,
                onOpenVideo = onOpenVideo,
            )

            else -> when (selectedTab) {
                LibraryTab.ALL -> HomeTab(
                    state = state,
                    hero = hero,
                    allItems = allItems,
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    onOpenAlbum = { openedAlbumId = it.id },
                    onOpenAlbumsTab = { selectedTab = LibraryTab.ALBUMS },
                    contentFocus = contentFocus,
                )

                LibraryTab.CHRONO -> ChronologyTab(
                    allItems = allItems,
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    contentFocus = contentFocus,
                )

                LibraryTab.ALBUMS -> AlbumsTab(                    state = state,
                    onOpenAlbum = { openedAlbumId = it.id },
                    contentFocus = contentFocus,
                )

                LibraryTab.RECENT -> RecentTab(
                    state = state,
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    contentFocus = contentFocus,
                )

                LibraryTab.SMART -> SmartTab(
                    state = state,
                    allItems = allItems,
                    favorites = favorites,
                    onOpenAlbum = { openedAlbumId = it.id },
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                    contentFocus = contentFocus,
                )
            }
        }
    }
}

// ============================== Tab 1：全部照片 ==============================

@Composable
private fun HomeTab(
    state: LibraryState,
    hero: HeroInfo?,
    allItems: List<MediaItem>,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenAlbumsTab: () -> Unit,
    contentFocus: PendingFocus,
) {
    val recentItems = remember(state) { recentRow(state) }
    val albums = remember(state) { folderAlbums(state) }
    val recentState = rememberLazyListState()
    val albumState = rememberLazyListState()

    // 每行按索引懒创建 FocusRequester（数量随数据变化）
    val heroRequester = remember { FocusRequester() }
    val recentRequesters = remember { mutableListOf<FocusRequester>() }
    val albumRequesters = remember { mutableListOf<FocusRequester>() }

    var recentIndex by remember { mutableIntStateOf(0) }
    var albumIndex by remember { mutableIntStateOf(0) }
    var openAllFocused by remember { mutableStateOf(false) }

    // 让聚焦项可见（契约 5）：focus 回调只记录 index，滚动在 LaunchedEffect 里做
    LaunchedEffect(recentIndex) { scrollRowTo(recentState, recentIndex) }
    LaunchedEffect(albumIndex, openAllFocused) {
        scrollRowTo(albumState, if (openAllFocused) albums.size else albumIndex)
    }

    // 主内容落点：Hero 优先，其次最近添加第一张
    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = { if (hero != null) heroRequester else requesterAt(recentRequesters, 0) })

    val heroHeight = (screenHeightDp() * 0.42f).dp

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = TvDimen.SpaceXl),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---------- Hero（上部 42%）----------
        item(key = "hero") {
            if (hero != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding)) {
                    LibraryHero(
                        hero = hero,
                        focusRequester = heroRequester,
                        onOpen = { openMedia(hero.item, hero.items, "全部照片", onOpenPhoto, onOpenVideo) },
                        onFocusChanged = { },
                        onDown = {
                            if (recentItems.isNotEmpty()) {
                                contentFocus.request(requesterAt(recentRequesters, recentIndex.coerceAtMost(recentItems.size - 1)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(heroHeight),
                    )
                }
            } else {
                HeroFallback(allItems.size)
            }
        }

        // ---------- 最近添加（LazyRow）----------
        item(key = "recentHeader") {
            SectionHeader(
                title = "最近添加",
                trailing = if (recentItems.isEmpty()) "" else "${recentItems.size} 项",
                modifier = Modifier.padding(start = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding, top = TvDimen.SpaceS),
            )
        }
        item(key = "recentRow") {
            if (recentItems.isEmpty()) {
                RowEmptyState("还没有新照片 · 检查 NAS 连接")
            } else {
                LazyRow(
                    state = recentState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                ) {
                    itemsIndexed(recentItems, key = { _, it -> it.id }) { index, item ->
                        MediaCard(
                            item = item,
                            width = 300,
                            height = 180,
                            focusRequester = requesterAt(recentRequesters, index),
                            onClick = { openMedia(item, recentItems, "最近添加", onOpenPhoto, onOpenVideo) },
                            onFocusChanged = { focused ->
                                if (focused) recentIndex = index
                            },
                            onUp = { contentFocus.request(heroRequester.takeIf { hero != null } ?: requesterAt(recentRequesters, 0)) },
                            onDown = { if (albums.isNotEmpty()) contentFocus.request(requesterAt(albumRequesters, index.coerceAtMost(albums.size - 1))) },
                        )
                    }
                }
            }
        }

        // ---------- 相册（LazyRow）----------
        item(key = "albumHeader") {
            SectionHeader(
                title = "相册",
                trailing = if (albums.isEmpty()) "" else "共 ${albums.size} 个",
                modifier = Modifier.padding(start = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding, top = TvDimen.SpaceL),
            )
        }
        item(key = "albumRow") {
            if (albums.isEmpty()) {
                RowEmptyState("相册还没加载出来 · 去设置里刷新 NAS")
            } else {
                // 相册行 UP = 回「最近添加」同列；无最近项则回 Hero
                val goUp = {
                    if (recentItems.isNotEmpty()) {
                        contentFocus.request(requesterAt(recentRequesters, albumIndex.coerceAtMost(recentItems.size - 1)))
                    } else {
                        contentFocus.request(heroRequester)
                    }
                }
                LazyRow(
                    state = albumState,
                    contentPadding = RowPadding,
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                ) {
                    itemsIndexed(albums, key = { _, it -> it.id }) { index, album ->
                        AlbumCard(
                            album = album,
                            coverUrl = albumCoverUrl(state, album),
                            width = 380,
                            height = 220,
                            focusRequester = requesterAt(albumRequesters, index),
                            onClick = { onOpenAlbum(album) },
                            onFocusChanged = { focused ->
                                if (focused) {
                                    albumIndex = index
                                    openAllFocused = false
                                }
                            },
                            onUp = goUp,
                        )
                    }
                    item(key = "openAlbums") {
                        OpenFolderCard(
                            width = 380,
                            height = 220,
                            focusRequester = requesterAt(albumRequesters, albums.size),
                            onClick = onOpenAlbumsTab,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    albumIndex = albums.size
                                    openAllFocused = true
                                }
                            },
                            onUp = goUp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(TvDimen.SpaceS))
        }
    }
}

/** 首页没有 Hero 时的标题区（不占满屏，保证下方行可见） */
@Composable
private fun HeroFallback(itemCount: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceL)) {
        Text("全部照片", color = TvColor.TextPrimary, fontSize = TvType.Display, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(TvDimen.SpaceXs))
        Text(
            text = if (itemCount > 0) "共 $itemCount 项媒体" else "正在等待 NAS 媒体库数据…",
            color = TvColor.TextTertiary,
            fontSize = TvType.Caption,
        )
    }
}

// ============================== Tab 2：年 · 月 · 日 ==============================

/**
 * 时间线：年 → 月 → 月内按天分组的照片流（停在月，不再有「日」页）。
 * BACK 从月回到年、从年回到根。
 */
@Composable
private fun ChronologyTab(
    allItems: List<MediaItem>,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
    contentFocus: PendingFocus,
) {
    var yearKey by rememberSaveable { mutableStateOf<String?>(null) }
    var monthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val innerFocus = remember { PendingFocus() }

    // 逐级收窄：每一级都只吃上一级分桶的结果，键与上一层唯一对应
    val years = remember(allItems) { chronoYears(allItems) }
    val yearBucket = years.firstOrNull { it.key == yearKey }
    val months = remember(yearBucket) { chronoMonths(yearBucket?.items.orEmpty()) }
    val monthBucket = months.firstOrNull { it.key == monthKey }
    val days = remember(monthBucket) { chronoDays(monthBucket?.items.orEmpty()) }

    val backToMonths = {
        monthKey = null
        innerFocus.requestBlock(BLOCK_CONTENT)
    }
    val backToYears = {
        yearKey = null
        innerFocus.requestBlock(BLOCK_CONTENT)
    }
    BackHandler(enabled = monthBucket != null) { backToMonths() }
    BackHandler(enabled = monthBucket == null && yearBucket != null) { backToYears() }

    val yearTitle = yearBucket?.title.orEmpty()

    when {
        monthBucket != null -> ChronoMonthPane(
            heading = "$yearTitle 年 ${monthBucket.title}",
            monthItems = monthBucket.items,
            days = days,
            innerFocus = innerFocus,
            topFocus = contentFocus,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
        )

        yearBucket != null -> ChronoGrid(
            heading = yearTitle,
            trailing = "${months.size} 个月 · ${countText(yearBucket.items)}",
            buckets = months,
            emptyText = "这一年没有能显示的照片",
            innerFocus = innerFocus,
            topFocus = contentFocus,
            onPick = {
                monthKey = it.key
                innerFocus.requestBlock(BLOCK_CONTENT)
            },
        )

        allItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "时间线还没有照片",
                subtitle = "NAS 里的照片会自动按年、月归好",
            )
        }

        else -> ChronoGrid(
            heading = "按时间浏览",
            trailing = "${years.size} 个年份 · ${allItems.size} 项",
            buckets = years,
            emptyText = "还没有能按时间归类的照片",
            innerFocus = innerFocus,
            // 顶栏 DOWN 只需要落在「年」这一层
            topFocus = contentFocus,
            onPick = {
                yearKey = it.key
                innerFocus.requestBlock(BLOCK_CONTENT)
            },
        )
    }
}

/** 年 / 月两级共用的分桶网格 */
@Composable
private fun ChronoGrid(
    heading: String,
    trailing: String,
    buckets: List<ChronoBucket>,
    emptyText: String,
    innerFocus: PendingFocus,
    onPick: (ChronoBucket) -> Unit,
    topFocus: PendingFocus? = null,
) {
    val gridState = rememberLazyGridState()
    val requesters = remember { mutableListOf<FocusRequester>() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val columns = CHRONO_GRID_COLUMNS
    val firstCell = { buckets.firstOrNull()?.let { requesterAt(requesters, 0) } }

    ConsumeBlockRequest(innerFocus, BLOCK_CONTENT, blockFallback = firstCell)
    if (topFocus != null) {
        ConsumeBlockRequest(topFocus, BLOCK_CONTENT, blockFallback = firstCell)
    }
    LaunchedEffect(focusedIndex) { scrollGridTo(gridState, focusedIndex, columns) }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = heading,
            trailing = trailing,
            modifier = Modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
        )
        if (buckets.isEmpty()) {
            EmptyState(title = emptyText, subtitle = "", modifier = Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = GridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(buckets, key = { _, it -> it.key }) { index, bucket ->
                    ChronoCard(
                        bucket = bucket,
                        width = CHRONO_CELL_W,
                        height = CHRONO_CELL_H,
                        focusRequester = requesterAt(requesters, index),
                        onClick = { onPick(bucket) },
                        onFocusChanged = { focused ->
                            if (focused) focusedIndex = index
                        },
                    )
                }
            }
        }
    }
}

/**
 * 月份页（时间线的终点）：按天分组，每天一行照片。
 * 上下键在「天」之间跳，左右键在当天内翻；ENTER 直接看图/播放，
 * 不再进入单独的「日」页面。
 */
@Composable
private fun ChronoMonthPane(
    heading: String,
    monthItems: List<MediaItem>,
    days: List<ChronoBucket>,
    innerFocus: PendingFocus,
    topFocus: PendingFocus?,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 每张缩略图一个 FocusRequester（按需创建），并记住每天最后聚焦的那张：
    // 跨天回来时优先回到它，行被回收/滚走导致聚焦失败再退回当天第一张。
    val tileFocus = remember(days) { mutableMapOf<String, FocusRequester>() }
    val lastFocused = remember(days) { mutableMapOf<String, String>() }
    fun requesterFor(item: MediaItem): FocusRequester = tileFocus.getOrPut(item.id) { FocusRequester() }
    // 落点取「当前可见的第一天」：从看图页返回/顶栏按下时，列表可能已经滚过月初，
    // 直接找 days[0] 的第一张会落在未组合的区域，聚焦失败。
    val focusableRequester = {
        val day = days.getOrNull(listState.firstVisibleItemIndex) ?: days.firstOrNull()
        day?.items?.firstOrNull()?.let(::requesterFor)
    }

    ConsumeBlockRequest(innerFocus, BLOCK_CONTENT, blockFallback = focusableRequester)
    if (topFocus != null) {
        ConsumeBlockRequest(topFocus, BLOCK_CONTENT, blockFallback = focusableRequester)
    }

    // 跨天移动：目标天已经完整可见就只移焦点，否则先把它滚进来再聚焦
    val focusDay: (Int) -> Unit = { index ->
        if (index in days.indices) {
            scope.launch {
                val vp = listState.layoutInfo.viewportSize.height
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val fullyVisible = info != null && info.offset >= 0 && info.offset + info.size <= vp
                if (!fullyVisible) {
                    runCatching { listState.animateScrollToItem(index) }
                }
                repeat(2) { withFrameMillis { } }
                val day = days[index]
                val remembered = lastFocused[day.key]?.let { tileFocus[it] }
                val ok = remembered?.let { runCatching { it.requestFocus() }.isSuccess } == true
                if (!ok) {
                    day.items.firstOrNull()?.let { runCatching { requesterFor(it).requestFocus() } }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = heading,
            trailing = "${days.size} 天 · ${countText(monthItems)}",
            modifier = Modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
        )
        LazyColumn(
            state = listState,
            contentPadding = GridContentPadding,
            verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceL),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(days, key = { _, day -> day.key }) { index, day ->
                ChronoDayStrip(
                    day = day,
                    requesterFor = ::requesterFor,
                    onFocusItem = { item -> lastFocused[day.key] = item.id },
                    onUp = if (index > 0) ({ focusDay(index - 1) }) else null,
                    onDown = if (index < days.lastIndex) ({ focusDay(index + 1) }) else null,
                    onOpenPhoto = onOpenPhoto,
                    onOpenVideo = onOpenVideo,
                )
            }
        }
    }
}

/** 一天：日期标题 + 一行按原始宽高比铺开的媒体缩略图 */
@Composable
private fun ChronoDayStrip(
    day: ChronoBucket,
    requesterFor: (MediaItem) -> FocusRequester,
    onFocusItem: (MediaItem) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
) {
    val date = day.items.first().dateTaken
    val rowState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) { scrollRowTo(rowState, focusedIndex) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日",
                color = TvColor.TextPrimary,
                fontSize = TvType.CardTitle,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(TvDimen.SpaceS))
            Text(
                text = "${formatWeekdayShort(date)} · ${countText(day.items)}",
                color = TvColor.TextTertiary,
                fontSize = TvType.Caption,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(day.items, key = { _, item -> item.id }) { index, item ->
                val aspect = remember(item.id) {
                    item.pixelSize()
                        ?.let { it.first.toFloat() / it.second }
                        ?.takeIf { it in 0.2f..5f }
                        ?: (4f / 3f)
                }
                val tileWidth = (MONTH_TILE_H * aspect).roundToInt().coerceIn(96, 360)
                MediaCard(
                    item = item,
                    width = tileWidth,
                    height = MONTH_TILE_H,
                    focusRequester = requesterFor(item),
                    onClick = {
                        openMedia(item, day.items, formatHeroDate(date), onOpenPhoto, onOpenVideo)
                    },
                    onFocusChanged = { focused ->
                        if (focused) {
                            focusedIndex = index
                            onFocusItem(item)
                        }
                    },
                    onUp = onUp,
                    onDown = onDown,
                )
            }
        }
    }
}

// ============================== Tab 3：相册网格 ==============================

@Composable
private fun AlbumsTab(
    state: LibraryState,
    onOpenAlbum: (Album) -> Unit,
    contentFocus: PendingFocus,
) {
    val albums = remember(state) { folderAlbums(state) }
    val gridState = rememberLazyGridState()
    val requesters = remember { mutableListOf<FocusRequester>() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val columns = ALBUM_GRID_COLUMNS

    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = { albums.firstOrNull()?.let { requesterAt(requesters, 0) } })
    LaunchedEffect(focusedIndex) { scrollGridTo(gridState, focusedIndex, columns) }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = "相册",
            trailing = "${albums.size} 个文件夹",
            modifier = Modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
        )
        if (albums.isEmpty()) {
            EmptyState(
                title = "还没有相册",
                subtitle = "NAS 上的 /photo 目录为空，或仍在扫描",
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.Album,
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = GridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(albums, key = { _, it -> it.id }) { index, album ->
                    AlbumCard(
                        album = album,
                        coverUrl = albumCoverUrl(state, album),
                        width = ALBUM_CELL_W,
                        height = ALBUM_CELL_H,
                        focusRequester = requesterAt(requesters, index),
                        onClick = { onOpenAlbum(album) },
                        onFocusChanged = { focused ->
                            if (focused) focusedIndex = index
                        },
                        // 第一行 Up → 顶栏；其余交给 Compose 默认（上一行同列）
                        onUp = null,
                        onDown = null,
                    )
                }
            }
        }
    }
}

// ============================== Tab 4：最近添加（6 列网格） ==============================

@Composable
private fun RecentTab(
    state: LibraryState,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
    contentFocus: PendingFocus,
) {
    val recent = remember(state) { state.recent }
    val gridState = rememberLazyGridState()
    val requesters = remember { mutableListOf<FocusRequester>() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val columns = MEDIA_GRID_COLUMNS

    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = { recent.firstOrNull()?.let { requesterAt(requesters, 0) } })
    LaunchedEffect(focusedIndex) { scrollGridTo(gridState, focusedIndex, columns) }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = "最近添加",
            trailing = countText(recent),
            modifier = Modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
        )
        if (recent.isEmpty()) {
            EmptyState(
                title = "最近没有新的添加",
                subtitle = "新导入到 NAS 的照片和视频会出现在这里",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MediaGrid(
                items = recent,
                columns = columns,
                gridState = gridState,
                requesters = requesters,
                focusedIndex = focusedIndex,
                onFocused = { focusedIndex = it },
                onClick = { index, item -> openMedia(item, recent, "最近添加", onOpenPhoto, onOpenVideo) },
            )
        }
    }
}

// ============================== Tab 5：收藏 / 人物 / 地点 ==============================

@Composable
private fun SmartTab(
    state: LibraryState,
    allItems: List<MediaItem>,
    favorites: List<MediaItem>,
    onOpenAlbum: (Album) -> Unit,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
    contentFocus: PendingFocus,
) {
    val people = remember(state) { smartAlbums(state, AlbumType.SMART_PEOPLE) }
    val places = remember(state) { smartAlbums(state, AlbumType.SMART_PLACE) }
    // 回忆不依赖 NAS 的 AI 结果：直接从已加载的时间线派生（往年今日 / 往年这个月）
    val memories = remember(allItems) { buildMemories(allItems) }
    val hasAny = favorites.isNotEmpty() || people.isNotEmpty() || places.isNotEmpty() || memories.isNotEmpty()

    val favRequesters = remember { mutableListOf<FocusRequester>() }
    val peopleRequesters = remember { mutableListOf<FocusRequester>() }
    val placesRequesters = remember { mutableListOf<FocusRequester>() }
    val memRequesters = remember { mutableListOf<FocusRequester>() }
    val favState = rememberLazyGridState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    val columns = MEDIA_GRID_COLUMNS

    // 收藏区高度固定 2 行，滚动只发生在整页 LazyColumn 上；这里 grid 自身禁用滚动
    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = { favorites.firstOrNull()?.let { requesterAt(favRequesters, 0) } })
    LaunchedEffect(focusedIndex) { scrollGridTo(favState, focusedIndex, columns) }

    if (!hasAny) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "还没有可回顾的内容",
                subtitle = "在图片浏览器里点亮星标，NAS 完成人脸 / 地点识别后这里会更丰富",
                icon = Icons.Outlined.Person,
            )
        }
        return
    }

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = TvDimen.SpaceXl),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "favHeader") {
            SectionHeader(
                title = "收藏",
                trailing = countText(favorites),
                modifier = Modifier.padding(start = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding, top = TvDimen.SpaceM, bottom = TvDimen.SpaceM),
            )
        }
        item(key = "favGrid") {
            if (favorites.isEmpty()) {
                RowEmptyState("还没有星标照片 · 在图片浏览器里点亮星标")
            } else {
                LazyVerticalGrid(
                    state = favState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = GridContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                    modifier = Modifier.fillMaxWidth().height(((MEDIA_CELL_H + 12) * 2).dp),
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(favorites, key = { _, it -> it.id }) { index, item ->
                        MediaCard(
                            item = item,
                            width = MEDIA_CELL_W,
                            height = MEDIA_CELL_H,
                            focusRequester = requesterAt(favRequesters, index),
                            onClick = { openMedia(item, favorites, "收藏", onOpenPhoto, onOpenVideo) },
                            onFocusChanged = { focused ->
                                if (focused) focusedIndex = index
                            },
                        )
                    }
                }
            }
        }

        if (people.isNotEmpty()) {
            item(key = "peopleHeader") { SmartRowHeader("人物", Icons.Outlined.Person, people.size) }
            item(key = "peopleRow") {
                SmartAlbumRow(state, people, peopleRequesters, onOpenAlbum)
            }
        }
        if (places.isNotEmpty()) {
            item(key = "placesHeader") { SmartRowHeader("地点", Icons.Outlined.Place, places.size) }
            item(key = "placesRow") {
                SmartAlbumRow(state, places, placesRequesters, onOpenAlbum)
            }
        }
        if (memories.isNotEmpty()) {
            item(key = "memHeader") { SmartRowHeader("回忆", Icons.Outlined.Star, memories.size) }
            item(key = "memRow") {
                MemoryRow(
                    memories = memories,
                    requesters = memRequesters,
                    onOpen = { memory ->
                        val start = memory.items.firstOrNull { it.type == MediaType.PHOTO } ?: memory.items.first()
                        openMedia(start, memory.items, memory.title, onOpenPhoto, onOpenVideo)
                    },
                )
            }
        }
    }
}

/** 回忆行：卡片就是时间线分桶卡（封面 + 标题 + 数量），ENTER 直接进看图页 */
@Composable
private fun MemoryRow(
    memories: List<ChronoBucket>,
    requesters: MutableList<FocusRequester>,
    onOpen: (ChronoBucket) -> Unit,
) {
    val rowState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedIndex) { scrollRowTo(rowState, focusedIndex) }
    LazyRow(
        state = rowState,
        contentPadding = RowPadding,
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
    ) {
        itemsIndexed(memories, key = { _, it -> it.key }) { index, memory ->
            ChronoCard(
                bucket = memory,
                width = 380,
                height = 220,
                focusRequester = requesterAt(requesters, index),
                onClick = { onOpen(memory) },
                onFocusChanged = { focused -> if (focused) focusedIndex = index },
            )
        }
    }
}

@Composable
private fun SmartRowHeader(title: String, icon: ImageVector, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = TvDimen.ScreenPadding, end = TvDimen.ScreenPadding, top = TvDimen.SpaceL, bottom = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TvColor.AccentAlt, modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(Modifier.width(TvDimen.SpaceXs))
        Text(title, color = TvColor.TextPrimary, fontSize = TvType.Section, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(TvDimen.SpaceS))
        Text("$count 组", color = TvColor.TextTertiary, fontSize = TvType.Caption)
    }
}

@Composable
private fun SmartAlbumRow(
    state: LibraryState,
    albums: List<Album>,
    requesters: MutableList<FocusRequester>,
    onOpenAlbum: (Album) -> Unit,
) {
    val rowState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedIndex) { scrollRowTo(rowState, focusedIndex) }
    LazyRow(
        state = rowState,
        contentPadding = RowPadding,
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
    ) {
        itemsIndexed(albums, key = { _, it -> it.id }) { index, album ->
            AlbumCard(
                album = album,
                coverUrl = albumCoverUrl(state, album),
                width = 380,
                height = 220,
                focusRequester = requesterAt(requesters, index),
                onClick = { onOpenAlbum(album) },
                onFocusChanged = { focused -> if (focused) focusedIndex = index },
            )
        }
    }
}

// ============================== 相册详情（文件夹浏览） ==============================

@Composable
private fun AlbumDetailPane(
    state: LibraryState,
    album: Album,
    onBack: () -> Unit,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
) {
    val items = remember(state, album.id) { albumItems(state, album) }
    val gridState = rememberLazyGridState()
    val backRequester = remember { FocusRequester() }
    val requesters = remember { mutableListOf<FocusRequester>() }
    val innerFocus = remember { PendingFocus() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val columns = MEDIA_GRID_COLUMNS

    // 进入详情：有内容 → 第一张；空相册 → 返回按钮
    LaunchedEffect(album.id) {
        val first = if (items.isNotEmpty()) requesterAt(requesters, 0) else backRequester
        runCatching { first.requestFocus() }
        innerFocus.request(first)
    }
    ConsumeBlockRequest(innerFocus, BLOCK_CONTENT, blockFallback = { if (items.isNotEmpty()) requesterAt(requesters, 0) else backRequester })

    Column(Modifier.fillMaxSize()) {
        AlbumDetailHeader(
            albumName = album.name,
            count = if (items.isEmpty()) "空文件夹" else countText(items),
            backFocusRequester = backRequester,
            onBack = onBack,
            onBackFocusChanged = { },
            onDown = { if (items.isNotEmpty()) innerFocus.request(requesterAt(requesters, 0)) },
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "这个相册还是空的",
                    subtitle = "NAS 上的「${album.name}」暂无照片或视频",
                    icon = Icons.Outlined.Image,
                )
            }
        } else {
            MediaGrid(
                items = items,
                columns = columns,
                gridState = gridState,
                requesters = requesters,
                focusedIndex = focusedIndex,
                onFocused = { focusedIndex = it },
                onFirstRowUp = { innerFocus.request(backRequester) },
                onClick = { index, item -> openMedia(item, items, album.name, onOpenPhoto, onOpenVideo) },
            )
        }
    }
}

// ============================== 通用媒体网格 ==============================

/**
 * 6 列媒体网格（最近添加 / 收藏 / 相册详情共用）。
 * 上下都交给 Compose 默认顺序（同列垂直移动），只在"第一行按 Up"时可选择回到页头。
 */
@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    gridState: LazyGridState,
    requesters: MutableList<FocusRequester>,
    focusedIndex: Int,
    onFocused: (Int) -> Unit,
    onFirstRowUp: (() -> Unit)? = null,
    onClick: (index: Int, item: MediaItem) -> Unit,
) {
    LaunchedEffect(focusedIndex) { scrollGridTo(gridState, focusedIndex, columns) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = GridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            MediaCard(
                item = item,
                width = MEDIA_CELL_W,
                height = MEDIA_CELL_H,
                focusRequester = requesterAt(requesters, index),
                onClick = { onClick(index, item) },
                onFocusChanged = { focused ->
                    if (focused) onFocused(index)
                },
                onUp = if (onFirstRowUp != null && index < columns) onFirstRowUp else null,
            )
        }
    }
}

// ============================== 小工具（放文件底部） ==============================

private const val TOP_ACTION_COUNT = 3
private const val DEFAULT_ACTION = 2
private const val ALBUM_GRID_COLUMNS = 4
private const val MEDIA_GRID_COLUMNS = 6
private const val CHRONO_GRID_COLUMNS = 5
private const val MEDIA_CELL_W = 244
private const val MEDIA_CELL_H = 158
private const val ALBUM_CELL_W = 408
private const val ALBUM_CELL_H = 200
private const val CHRONO_CELL_W = 340
private const val CHRONO_CELL_H = 200

/** 月份页里每天那一行缩略图的高度（宽度按原图宽高比算） */
private const val MONTH_TILE_H = 150

private fun initialTabOf(index: Int): LibraryTab = LibraryTab.entries.getOrElse(index) { LibraryTab.ALL }

/** 按 index 懒创建 FocusRequester（列表项数量随数据变化，不能预分配固定数组） */
private fun requesterAt(list: MutableList<FocusRequester>, index: Int): FocusRequester {
    while (list.size <= index) list += FocusRequester()
    return list[index.coerceAtLeast(0)]
}

/** 屏幕高度（dp），用于计算 Hero 高度 */
@Composable
private fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

// ============================== Preview ==============================

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibraryHomePreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.fullState,
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibraryChronoPreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 1),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibraryAlbumsPreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 2),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibrarySmartPreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 3),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibraryEmptyPreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.emptyState,
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

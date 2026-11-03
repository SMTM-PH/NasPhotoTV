package com.nasphoto.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.nasphoto.tv.data.model.Person
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
    /**
     * 拉取某个人物下的照片。人物照片不属于任何相册，只能走 NAS 的人脸接口，
     * 所以由外壳注入（本页不持有仓库）。
     *
     * 返回 null 表示**这次请求失败**（断网 / 鉴权过期 / NAS 侧异常），
     * 与「这个人确实没有照片」（空列表）必须分开：前者该让用户退出去重试，
     * 后者是正常结果，提示重试只会把人绕进死循环。
     */
    onLoadPersonPhotos: suspend (personId: String) -> List<MediaItem>? = { emptyList() },
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable(state.selectedTabIndex) {
        mutableStateOf(initialTabOf(state.selectedTabIndex))
    }
    var openedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    // 从人物详情 BACK 回来时要落回原来那张脸，否则一排人里跳回第一个很别扭
    var peopleFocusIndex by rememberSaveable { mutableStateOf(0) }
    // 从相册详情 BACK 回来时要落回刚才那个相册卡片；否则焦点掉到顶栏，得重新往下找
    var albumsFocusIndex by rememberSaveable { mutableIntStateOf(0) }

    // 派生数据（全部空安全）
    val hero = remember(state) { buildHero(state) }
    val allItems = remember(state) { allPhotos(state) }
    val favorites = remember(state, FavoritesStore.ids) { favoriteItems(state) }
    val openedAlbum = openedAlbumId?.let { id -> state.albums.firstOrNull { it.id == id } }
    val openedPerson = openedPersonId?.let { id -> state.people.firstOrNull { it.id == id } }

    // ---- 顶栏焦点 ----
    val tabRequesters = remember { LibraryTab.entries.map { FocusRequester() } }
    val actionRequesters = remember { List(TOP_ACTION_COUNT) { FocusRequester() } }
    val accountRequester = remember { FocusRequester() }
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

    // 顶栏落点：主内容按 UP 时精确回到当前 Tab。
    // 不交给 Compose 空间查找 —— 人物卡片 260 宽、顶栏 Tab 位置随 label 长度浮动，
    // 空间查找可能落到账号胶囊或图标按钮，遥控器会"飘"。
    ConsumeBlockRequest(topFocus, BLOCK_TOP, blockFallback = { tabRequesters.getOrNull(lastTab) })

    // BACK：人物详情 → 人物列表；相册详情 → 相册网格（列表页的 BACK 由 Lead 外壳处理）
    BackHandler(enabled = openedAlbum != null) {
        openedAlbumId = null
        // 遥控器 BACK 走这里（不是详情页里的返回按钮）。相册网格已随详情页销毁，
        // 必须显式把焦点交还内容区，否则会落到顶栏 Tab —— 用户得重新往下找相册。
        contentFocus.requestBlock(BLOCK_CONTENT)
    }

    LibraryBackdrop(modifier.fillMaxSize())

    Column(Modifier.fillMaxSize()) {
        LibraryTopBar(
            tabs = LibraryTab.entries,
            selectedTab = selectedTab,
            connectionName = state.connectionName,
            isOnline = state.isOnline,
            accountName = state.accountName,
            accountCount = state.accountCount,
            onAccountClick = { onNavigate(AppRoute.Settings) },
            onAccountDown = { contentFocus.requestBlock(BLOCK_CONTENT) },
            accountFocusRequester = accountRequester,
            onTabClick = { tab ->
                selectedTab = tab
                openedAlbumId = null
                openedPersonId = null
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

        // 有照片列表才算「有库」；但要给人物留一条活路 —— 相册接口失败而人脸结果已到时，
        // 整个页面被空状态盖掉，人物 Tab 就永远进不去了。
        val isEmptyLibrary = state.recent.isEmpty() && state.albums.isEmpty() && state.people.isEmpty()
        when {
            isEmptyLibrary -> LibraryEmptyState(onOpenSettings = { onNavigate(AppRoute.Settings) })

            openedAlbum != null -> AlbumDetailPane(
                state = state,
                album = openedAlbum,
                onBack = {
                    openedAlbumId = null
                    // 相册网格已随详情页销毁（remember 状态没了），不显式交还焦点就会
                    // 掉到顶栏 Tab 上 —— 用户得重新按下方向键找回相册。与人物详情同款处理。
                    contentFocus.requestBlock(BLOCK_CONTENT)
                },
                onOpenPhoto = onOpenPhoto,
                onOpenVideo = onOpenVideo,
            )

            openedPerson != null -> PersonDetailPane(
                person = openedPerson,
                onBack = {
                    openedPersonId = null
                    // 详情页里的卡片已随组合销毁，焦点不会自己回来；显式交还给人物列表
                    contentFocus.requestBlock(BLOCK_CONTENT)
                },
                onOpenPhoto = onOpenPhoto,
                onOpenVideo = onOpenVideo,
                onLoadPhotos = onLoadPersonPhotos,
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

                LibraryTab.ALBUMS -> AlbumsTab(
                    state = state,
                    onOpenAlbum = { openedAlbumId = it.id },
                    contentFocus = contentFocus,
                    restoreIndex = albumsFocusIndex,
                    onFocusAlbum = { albumsFocusIndex = it },
                )

                LibraryTab.PEOPLE -> PeopleTab(
                    people = state.people,
                    peopleLoaded = state.peopleLoaded,
                    onOpenPerson = { openedPersonId = it.id },
                    contentFocus = contentFocus,
                    restoreIndex = peopleFocusIndex,
                    onFocusPerson = { peopleFocusIndex = it },
                    onUpToTopBar = { topFocus.request(tabRequesters.getOrNull(selectedTab.ordinal)) },
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
    // 记住两行各自看到第几张 + 最后焦点在哪一行，看图返回后落回原处（不只是回 Hero）
    var recentIndex by rememberSaveable { mutableIntStateOf(0) }
    var albumIndex by rememberSaveable { mutableIntStateOf(0) }
    var openAllFocused by rememberSaveable { mutableStateOf(false) }
    var lastRow by rememberSaveable { mutableIntStateOf(0) } // 0=Hero 1=最近添加 2=相册
    val recentSafe = recentIndex.coerceIn(0, recentItems.lastIndex.coerceAtLeast(0))
    val albumSafe = albumIndex.coerceIn(0, albums.lastIndex.coerceAtLeast(0))
    val recentState = rememberLazyListState(initialFirstVisibleItemIndex = (recentSafe - 1).coerceAtLeast(0))
    val albumState = rememberLazyListState(initialFirstVisibleItemIndex = (albumSafe - 1).coerceAtLeast(0))

    // 每行按索引懒创建 FocusRequester（数量随数据变化）
    val heroRequester = remember { FocusRequester() }
    val recentRequesters = remember { mutableListOf<FocusRequester>() }
    val albumRequesters = remember { mutableListOf<FocusRequester>() }


    // 让聚焦项可见（契约 5）：focus 回调只记录 index，滚动在 LaunchedEffect 里做
    LaunchedEffect(recentIndex) { scrollRowTo(recentState, recentIndex) }
    LaunchedEffect(albumIndex, openAllFocused) {
        scrollRowTo(albumState, if (openAllFocused) albums.size else albumIndex)
    }

    // 主内容落点：按「上次焦点在哪一行」还原；首次进入仍以 Hero 优先
    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = {
        when (lastRow) {
            1 -> recentItems.getOrNull(recentSafe)?.let { requesterAt(recentRequesters, recentSafe) }
            2 -> if (openAllFocused) {
                requesterAt(albumRequesters, albums.size)
            } else {
                albums.getOrNull(albumSafe)?.let { requesterAt(albumRequesters, albumSafe) }
            }

            else -> if (hero != null) heroRequester else requesterAt(recentRequesters, 0)
        }
    })

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
                        onFocusChanged = { focused -> if (focused) lastRow = 0 },
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
                                if (focused) {
                                    recentIndex = index
                                    lastRow = 1
                                }
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
                                    lastRow = 2
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
                                    lastRow = 2
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
    /** 从相册详情 BACK 回来时要落回的相册索引（外层 rememberSaveable 记住的落点） */
    restoreIndex: Int = 0,
    /** 聚焦变化上报给外层保存，详情页返回后才有「回到原来那个相册」可言 */
    onFocusAlbum: (Int) -> Unit = {},
) {
    val albums = remember(state) { folderAlbums(state) }
    val columns = ALBUM_GRID_COLUMNS
    // 恢复位置可能已滚出可视区，而 LazyGrid 只组合可见项：让网格直接初始化在恢复位置
    // 所在行，目标卡片才在组合树里，requestFocus 才有意义（与 PeopleTab 同款处理）。
    val safeRestore = restoreIndex.coerceIn(0, albums.lastIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (safeRestore / columns) * columns,
    )
    val requesters = remember { mutableListOf<FocusRequester>() }
    var focusedIndex by remember { mutableIntStateOf(safeRestore) }

    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = {
        albums.getOrNull(focusedIndex)?.let { requesterAt(requesters, focusedIndex) }
    })
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
                            if (focused) {
                                focusedIndex = index
                                onFocusAlbum(index)
                            }
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
    val columns = MEDIA_GRID_COLUMNS
    // 全屏看图返回后 LibraryScreen 重新组合，落点若是第 0 张就等于把用户丢回列表开头。
    // rememberSaveable 由外层 SaveableStateProvider("library") 保管，看图往返不丢。
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeRestore = focusedIndex.coerceIn(0, recent.lastIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (safeRestore / columns) * columns,
    )
    val requesters = remember { mutableListOf<FocusRequester>() }

    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = {
        recent.getOrNull(safeRestore)?.let { requesterAt(requesters, safeRestore) }
    })
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
    val columns = MEDIA_GRID_COLUMNS
    // 记住「看的是哪一张」：从全屏看图返回时，LibraryScreen 整体重新组合，
    // 若落点永远是第 0 张，用户就被丢回相册开头 —— 这与顶栏掉焦点一样恼人。
    // rememberSaveable 由外层 SaveableStateProvider("library") 保管，看图往返不丢。
    var focusedIndex by rememberSaveable(album.id) { mutableIntStateOf(0) }
    // 网格初始化在记忆位置所在行，目标图才被组合（Lazy 只组合可见项），requestFocus 才有效
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (focusedIndex / columns) * columns,
    )
    val backRequester = remember { FocusRequester() }
    val requesters = remember { mutableListOf<FocusRequester>() }
    val innerFocus = remember { PendingFocus() }

    // 进入详情：有内容 → 记忆位置（默认第一张）；空相册 → 返回按钮
    LaunchedEffect(album.id) {
        val target = if (items.isNotEmpty()) {
            requesterAt(requesters, focusedIndex.coerceIn(0, items.lastIndex))
        } else {
            backRequester
        }
        // 网格刚进组合时节点可能还没挂上布局树，requestFocus 会静默失败、焦点留在顶栏，
        // 等一帧再试一次（与 PersonDetailPane 同款兜底）。
        if (runCatching { target.requestFocus() }.isFailure) {
            withFrameMillis { }
            runCatching { target.requestFocus() }
        }
        innerFocus.request(target)
    }
    ConsumeBlockRequest(innerFocus, BLOCK_CONTENT, blockFallback = {
        if (items.isNotEmpty()) requesterAt(requesters, focusedIndex.coerceIn(0, items.lastIndex)) else backRequester
    })

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

// ============================== 人物详情（一个人下的全部照片） ==============================

/**
 * 人物详情页：视觉与导航完全沿用 [AlbumDetailPane]（同一套返回按钮 + 6 列网格），
 * 只有数据来源不同 —— 人物照片不属于任何相册，要按 personId 走 NAS 人脸接口现拉。
 *
 * 为什么不在进入时就同步拉：人脸接口在 4 核机器上可能要几秒，
 * 先给一个「正在加载 <人名> 的照片…」的居中进度，比停在空网格上让人以为没有照片要好。
 */
@Composable
private fun PersonDetailPane(
    person: Person,
    onBack: () -> Unit,
    onOpenPhoto: (List<MediaItem>, Int, String) -> Unit,
    onOpenVideo: (List<MediaItem>, Int, String) -> Unit,
    onLoadPhotos: suspend (String) -> List<MediaItem>?,
) {
    // 四态用类型表达，不再拿 null 兼「加载中 / 失败」两义：
    // 「读失败」和「确实没有照片」对用户是两件事 —— 前者要提示可以重试，后者是正常结果。
    var photosState by remember(person.id) { mutableStateOf<PersonPhotos>(PersonPhotos.Loading) }
    val columns = MEDIA_GRID_COLUMNS
    // 同 AlbumDetailPane：记住看到的是哪一张，全屏看图返回后落回它而不是第一张
    var focusedIndex by rememberSaveable(person.id) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (focusedIndex / columns) * columns,
    )
    val backRequester = remember { FocusRequester() }
    val requesters = remember { mutableListOf<FocusRequester>() }
    val innerFocus = remember { PendingFocus() }
    val contextTitle = "人物 · ${person.displayName}"
    val readyItems = (photosState as? PersonPhotos.Ready)?.items.orEmpty()

    LaunchedEffect(person.id) {
        photosState = PersonPhotos.Loading
        // 外层注入的接口返回 null = 这次失败；这里不再 try/catch 兜底成空列表，
        // 否则一次网络抖动会被显示成「这个人没有照片」，用户以为数据丢了。
        photosState = when (val loaded = onLoadPhotos(person.id)) {
            null -> PersonPhotos.Failed
            else -> if (loaded.isEmpty()) PersonPhotos.Empty else PersonPhotos.Ready(loaded)
        }
    }
    BackHandler { onBack() }

    // 落点：只有拿到照片才落第一张；加载中 / 失败 / 空都落返回按钮
    // —— 这三种状态下网格并不存在，指过去的 requester 会 requestFocus 失败、焦点留在顶栏。
    val firstTarget = {
        if (photosState is PersonPhotos.Ready) {
            val last = readyItems.lastIndex.coerceAtLeast(0)
            requesterAt(requesters, focusedIndex.coerceIn(0, last))
        } else {
            backRequester
        }
    }
    LaunchedEffect(person.id, photosState) {
        val target = firstTarget()
        // 照片是异步到的：网格刚进组合时节点可能还没挂上布局树，requestFocus 会直接抛，
        // 于是等一帧再试一次，避免「加载完成但焦点留在顶栏」的掉焦点问题。
        if (runCatching { target.requestFocus() }.isFailure) {
            withFrameMillis { }
            runCatching { target.requestFocus() }
        }
        innerFocus.request(target)
    }
    ConsumeBlockRequest(innerFocus, BLOCK_CONTENT, blockFallback = firstTarget)

    Column(Modifier.fillMaxSize()) {
        AlbumDetailHeader(
            albumName = person.displayName,
            count = when (photosState) {
                PersonPhotos.Loading -> "正在加载…"
                PersonPhotos.Failed -> "读取失败"
                PersonPhotos.Empty -> "还没有照片"
                is PersonPhotos.Ready -> countText(readyItems)
            },
            backFocusRequester = backRequester,
            onBack = onBack,
            onBackFocusChanged = { },
            onDown = { if (photosState is PersonPhotos.Ready) innerFocus.request(requesterAt(requesters, 0)) },
        )
        when (photosState) {
            PersonPhotos.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TvColor.Accent)
                    Spacer(Modifier.height(TvDimen.SpaceM))
                    Text(
                        text = "正在加载${person.displayName}的照片…",
                        color = TvColor.TextTertiary,
                        fontSize = TvType.Caption,
                    )
                }
            }

            // 失败：明确说是「读取失败」并给出下一步，而不是伪装成「没有照片」
            PersonPhotos.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "读取这个人物失败了",
                    subtitle = "可能是 NAS 连接中断或 AI 相册服务未响应，按 BACK 返回后可以重试",
                    icon = Icons.Outlined.Person,
                )
            }

            PersonPhotos.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "这个人物下还没有照片",
                    subtitle = "可能刚完成识别，或该人物的照片已被移出 NAS 相册",
                    icon = Icons.Outlined.Person,
                )
            }

            is PersonPhotos.Ready -> MediaGrid(
                items = readyItems,
                columns = columns,
                gridState = gridState,
                requesters = requesters,
                focusedIndex = focusedIndex,
                onFocused = { focusedIndex = it },
                onFirstRowUp = { innerFocus.request(backRequester) },
                onClick = { _, item ->
                    openMedia(item, readyItems, contextTitle, onOpenPhoto, onOpenVideo)
                },
            )
        }
    }
}

/**
 * 人物照片的四种状态。
 *
 * 为什么单独建类型：用 `List<MediaItem>?` 时 null 同时代表「还在读」和「读失败」，
 * 空列表又代表「读到了但没有」—— 三种含义挤在两个值里，改文案时必然漏掉一种。
 */
private sealed interface PersonPhotos {
    data object Loading : PersonPhotos
    data object Failed : PersonPhotos
    data object Empty : PersonPhotos
    data class Ready(val items: List<MediaItem>) : PersonPhotos
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
private fun LibraryPeoplePreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 3),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
            onLoadPersonPhotos = { id -> LibraryPreviewData.personPhotos[id].orEmpty() },
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibrarySmartPreview() {
    NasPhotoTvTheme {
        LibraryScreen(
            // 插入「人物」Tab 后 SELECT 的下标整体后移一位（SMART 现在是第 6 个）
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 5),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun LibraryPeopleEmptyPreview() {
    NasPhotoTvTheme {
        // 人脸结果为空：验证「还没有人物」引导页，不是白屏
        LibraryScreen(
            state = LibraryPreviewData.fullState.copy(selectedTabIndex = 3, people = emptyList()),
            onOpenPhoto = { _, _, _ -> },
            onOpenVideo = { _, _, _ -> },
            onNavigate = {},
        )
    }
}

/**
 * 人物详情·读取失败。
 *
 * 直接预览 [PersonDetailPane] 而不是走 LibraryScreen：失败态依赖注入的 loader 返回 null，
 * 从列表页点进去的路径没法在 Preview 里表达（Preview 不执行点击），
 * 而这条分支恰恰是最容易被漏掉、也最容易被误写成「没有照片」的一条。
 */
@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PersonDetailFailedPreview() {
    NasPhotoTvTheme {
        Box(Modifier.fillMaxSize().background(TvColor.Bg)) {
            PersonDetailPane(
                person = LibraryPreviewData.people.first(),
                onBack = {},
                onOpenPhoto = { _, _, _ -> },
                onOpenVideo = { _, _, _ -> },
                onLoadPhotos = { null },
            )
        }
    }
}

/** 人物详情·读取成功但这个人确实没有照片（与失败态区分开，文案不能一样） */
@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PersonDetailEmptyPreview() {
    NasPhotoTvTheme {
        Box(Modifier.fillMaxSize().background(TvColor.Bg)) {
            PersonDetailPane(
                person = LibraryPreviewData.people.first(),
                onBack = {},
                onOpenPhoto = { _, _, _ -> },
                onOpenVideo = { _, _, _ -> },
                onLoadPhotos = { emptyList() },
            )
        }
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

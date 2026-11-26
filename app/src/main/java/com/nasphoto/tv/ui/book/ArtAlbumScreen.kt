package com.nasphoto.tv.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.common.MediaPlaceholder
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvImage
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** 按拍摄年份装订照片。遥控器左右翻页，确认可跳到某个月。 */
@Composable
fun ArtAlbumScreen(
    photos: List<MediaItem>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books by produceState<List<YearBook>?>(null, photos) {
        value = withContext(Dispatchers.Default) { assembleBooks(photos) }
    }
    var openYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var monthIndex by rememberSaveable { mutableIntStateOf(0) }
    var spreadIndex by rememberSaveable { mutableIntStateOf(0) }
    var openDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }

    val book = books?.firstOrNull { it.year == openYear }
    val day = openDay
    val dayPhotos = book?.months?.get(monthIndex)?.days?.get(day)
    val closeBook = {
        openDay = null
        choosingMonth = false
        openYear = null
    }
    val goBack = {
        when {
            !dayPhotos.isNullOrEmpty() -> openDay = null
            book != null && choosingMonth -> choosingMonth = false
            book != null || books == null && openYear != null -> closeBook()
            else -> onClose()
        }
    }
    BackHandler(onBack = goBack)

    val shelf = books
    Column(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(TvColor.BgElevated, TvColor.Bg)),
        ).padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
    ) {
        when {
            book != null -> key(book.year) {
                val month = book.months[monthIndex]
                if (day != null && !dayPhotos.isNullOrEmpty()) {
                    DayReader(
                        date = LocalDate.of(book.year, month.number, day),
                        photos = dayPhotos,
                        index = spreadIndex,
                        onPage = { spreadIndex = it },
                        onBack = goBack,
                    )
                } else {
                    CalendarReader(
                        book = book,
                        index = monthIndex,
                        lastDay = lastDay,
                        onMonth = {
                            monthIndex = it
                            lastDay = null
                            openDay = null
                        },
                        onOpenDay = {
                            openDay = it
                            lastDay = it
                            spreadIndex = 0
                        },
                        choosingMonth = choosingMonth,
                        setChoosingMonth = { choosingMonth = it },
                        onBack = goBack,
                    )
                }
            }
            shelf != null && shelf.isNotEmpty() -> BookShelf(
                books = shelf,
                lastYear = lastYear,
                onOpen = {
                    lastYear = it
                    val opened = shelf.first { book -> book.year == it }
                    monthIndex = opened.months.indexOfFirst { month -> month.days.isNotEmpty() }.coerceAtLeast(0)
                    openDay = null
                    lastDay = null
                    choosingMonth = false
                    openYear = it
                },
                onBack = goBack,
            )
            else -> {
                val back = remember { FocusRequester() }
                LaunchedEffect(Unit) { back.focusAfterLayout() }
                AlbumHeader("艺术画册", "", goBack, back)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (shelf == null) "正在整理照片…" else "按年份整理的照片会在这里成册",
                        color = TvColor.TextSecondary,
                        fontSize = TvType.Body,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookShelf(books: List<YearBook>, lastYear: Int?, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val grid = rememberLazyGridState()
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val back = remember { FocusRequester() }
    val initialIndex = books.indexOfFirst { it.year == lastYear }.coerceAtLeast(0)
    val initialYear = books[initialIndex].year
    LaunchedEffect(initialYear) {
        grid.scrollToItem(initialIndex)
        requesters.getOrPut(initialYear, ::FocusRequester).focusAfterLayout()
    }
    Column(Modifier.fillMaxSize()) {
        AlbumHeader("艺术画册", "${books.size} 本年度画册", onBack, back, onDown = {
            requesters[initialYear]?.requestFocus()
        })
        Spacer(Modifier.height(TvDimen.SpaceM))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
            verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
            contentPadding = PaddingValues(TvDimen.SpaceXs),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(books, key = { _, book -> book.year }) { index, book ->
                TvFocusBox(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button },
                    focusRequester = requesters.getOrPut(book.year, ::FocusRequester),
                    onConfirm = { onOpen(book.year) },
                    onUp = if (index < 3) ({ back.requestFocus() }) else null,
                    focusedScale = 1.02f,
                ) {
                    YearCover(book)
                }
            }
        }
    }
}

@Composable
private fun YearCover(book: YearBook) {
    val shape = RoundedCornerShape(TvDimen.RadiusS, TvDimen.RadiusM, TvDimen.RadiusM, TvDimen.RadiusS)
    Row(Modifier.fillMaxWidth().clip(shape).background(BookPaper)) {
        Box(Modifier.width(TvDimen.SpaceXs).height(266.dp).background(BookSpine))
        Column(Modifier.weight(1f).padding(TvDimen.SpaceS)) {
            MediaImage(
                book.cover.bookUrl,
                null,
                Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(TvDimen.RadiusS)),
                targetWidth = TvImage.AlbumCoverPx,
            )
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(
                "${book.year} 年",
                color = BookInk,
                fontFamily = FontFamily.Serif,
                fontSize = TvType.Section,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text("${book.photoCount} 张照片", color = BookSecondary, fontSize = TvType.Caption, maxLines = 1)
        }
    }
}

@Composable
private fun CalendarReader(
    book: YearBook,
    index: Int,
    lastDay: Int?,
    onMonth: (Int) -> Unit,
    onOpenDay: (Int) -> Unit,
    choosingMonth: Boolean,
    setChoosingMonth: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val month = book.months[index]
    val back = remember { FocusRequester() }
    val monthControl = remember { FocusRequester() }
    val activeMonth = remember(month.number) { FocusRequester() }
    val featuredDay = lastDay?.takeIf { it in month.days } ?: month.days.keys.firstOrNull()
    val dayControl = remember(month.number, featuredDay) { FocusRequester() }
    var footerFocused by remember { mutableStateOf(false) }
    val hasPhotos = featuredDay != null
    LaunchedEffect(choosingMonth, month.number) {
        when {
            choosingMonth -> activeMonth.focusAfterLayout()
            lastDay != null && hasPhotos -> dayControl.focusAfterLayout()
            else -> monthControl.focusAfterLayout()
        }
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (choosingMonth || !footerFocused || event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }
            val direction = event.turnDirection() ?: return@onPreviewKeyEvent false
            val target = (index + direction).coerceIn(book.months.indices)
            if (target != index) onMonth(target)
            true
        },
    ) {
        AlbumHeader(
            "${book.year} 年画册",
            if (choosingMonth) "选择月份" else "${book.photoCount} 张照片",
            onBack,
            back,
            onDown = {
                when {
                    choosingMonth -> activeMonth.requestFocus()
                    hasPhotos -> dayControl.requestFocus()
                    else -> monthControl.requestFocus()
                }
            },
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        if (choosingMonth) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                contentPadding = PaddingValues(TvDimen.SpaceXs),
            ) {
                itemsIndexed(book.months, key = { _, value -> value.number }) { target, value ->
                    val selectedMonth = target == index
                    TvFocusBox(
                        modifier = Modifier.fillMaxWidth().height(86.dp)
                            .semantics(mergeDescendants = true) {
                                selected = selectedMonth
                                role = Role.Button
                            }
                            .background(
                                if (selectedMonth) TvColor.SurfaceSoft else TvColor.Surface,
                                RoundedCornerShape(TvDimen.RadiusM),
                            ),
                        focusRequester = activeMonth.takeIf { selectedMonth },
                        onConfirm = {
                            onMonth(target)
                            setChoosingMonth(false)
                        },
                        onUp = if (target < 4) ({ back.requestFocus() }) else null,
                    ) {
                        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = TvDimen.SpaceM)) {
                            Text("${value.number} 月", color = TvColor.TextPrimary, fontSize = TvType.CardTitle)
                            Text("${value.photoCount} 张", color = TvColor.TextSecondary, fontSize = TvType.Caption)
                        }
                    }
                }
            }
        } else {
            PageTurn(index, Modifier.weight(1f).fillMaxWidth()) { page ->
                val current = page == index
                CalendarSpread(
                    year = book.year,
                    month = book.months[page],
                    active = current,
                    featuredDay = featuredDay.takeIf { current },
                    dayRequester = dayControl.takeIf { current },
                    onOpenDay = onOpenDay,
                    onHeader = { back.requestFocus() },
                    onFooter = { monthControl.requestFocus() },
                )
            }
            Spacer(Modifier.height(TvDimen.SpaceS))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasPhotos) "← → 翻月  ·  ↑ 选日期" else "← → 翻月",
                    color = TvColor.TextSecondary,
                    fontSize = TvType.Caption,
                )
                Spacer(Modifier.weight(1f))
                TvFocusBox(
                    modifier = Modifier.heightIn(min = TvDimen.ControlHeight).background(
                        TvColor.SurfaceSoft,
                        RoundedCornerShape(TvDimen.RadiusS),
                    ).padding(horizontal = TvDimen.SpaceM),
                    focusRequester = monthControl,
                    onFocusChanged = { footerFocused = it },
                    onConfirm = { setChoosingMonth(true) },
                    onUp = {
                        if (hasPhotos) dayControl.requestFocus() else back.requestFocus()
                    },
                    focusedScale = 1.025f,
                ) {
                    Text("选择月份", Modifier.align(Alignment.Center), color = TvColor.TextPrimary, fontSize = TvType.Label)
                }
                Spacer(Modifier.weight(1f))
                Text("${month.number} / 12 月", color = TvColor.TextSecondary, fontSize = TvType.Caption)
            }
        }
    }
}

@Composable
private fun CalendarSpread(
    year: Int,
    month: BookMonth,
    active: Boolean,
    featuredDay: Int?,
    dayRequester: FocusRequester?,
    onOpenDay: (Int) -> Unit,
    onHeader: () -> Unit,
    onFooter: () -> Unit,
) {
    val calendar = YearMonth.of(year, month.number)
    val firstSlot = calendar.atDay(1).dayOfWeek.value - 1
    val lastSlot = firstSlot + calendar.lengthOfMonth() - 1
    val weeks = lastSlot / 7 + 1
    val shape = RoundedCornerShape(TvDimen.RadiusM)
    var previewDay by remember(year, month.number, featuredDay) { mutableStateOf(featuredDay) }
    val lead = month.days[previewDay]?.firstOrNull() ?: month.days.values.firstOrNull()?.first()
    val firstAvailableWeek = month.days.keys.firstOrNull()?.let { (firstSlot + it - 1) / 7 }
    val lastAvailableWeek = month.days.keys.lastOrNull()?.let { (firstSlot + it - 1) / 7 }
    Row(Modifier.fillMaxSize().clip(shape).background(BookPaper).border(1.dp, BookEdge, shape)) {
        Column(Modifier.weight(0.32f).fillMaxHeight().padding(TvDimen.SpaceM)) {
            Text("$year 年", color = BookSecondary, fontSize = TvType.Caption)
            Text(
                "${month.number} 月",
                color = BookInk,
                fontSize = TvType.CinemaCompactDisplay,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(TvDimen.SpaceS))
            if (lead != null) {
                BookPhoto(lead, Modifier.weight(1f), TvImage.AlbumCoverPx)
            } else {
                Text("本月暂无照片", color = BookSecondary, fontSize = TvType.Caption)
            }
        }
        Box(
            Modifier.fillMaxHeight().width(TvDimen.SpaceXs)
                .background(Brush.horizontalGradient(listOf(BookPaper, BookEdge, BookHighlight))),
        )
        Column(
            Modifier.weight(0.68f).fillMaxHeight().background(BookHighlight).padding(TvDimen.SpaceM),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Weekdays.forEach { weekday ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(weekday, color = BookSecondary, fontSize = TvType.Caption)
                    }
                }
            }
            repeat(weeks) { week ->
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(7) { weekday ->
                        val slot = week * 7 + weekday
                        val day = slot - firstSlot + 1
                        val dayModifier = Modifier.weight(1f).fillMaxHeight()
                        if (slot !in firstSlot..lastSlot) {
                            Spacer(dayModifier)
                        } else {
                            CalendarDay(
                                day = day,
                                photos = month.days[day].orEmpty(),
                                modifier = dayModifier,
                                requester = dayRequester.takeIf { day == featuredDay },
                                onOpen = if (active) ({ onOpenDay(day) }) else null,
                                onFocus = { if (active) previewDay = day },
                                onUp = onHeader.takeIf { week == firstAvailableWeek },
                                onDown = onFooter.takeIf { week == lastAvailableWeek },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    photos: List<MediaItem>,
    modifier: Modifier,
    requester: FocusRequester?,
    onOpen: (() -> Unit)?,
    onFocus: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(5.dp)
    val representative = photos.firstOrNull()
    if (representative == null) {
        Box(modifier.background(BookMatte, shape).padding(4.dp)) {
            Text(day.toString(), color = BookSecondary, fontSize = TvType.Tiny)
        }
        return
    }
    TvFocusBox(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "$day 日 · ${photos.size} 张照片"
        },
        cornerRadiusDp = 5,
        focusRequester = requester,
        enabled = onOpen != null,
        onFocusChanged = { if (it) onFocus() },
        onConfirm = onOpen,
        onUp = onUp,
        onDown = onDown,
        focusedScale = 1.02f,
    ) {
        Box(Modifier.fillMaxSize().clip(shape).background(BookMatte)) {
            MediaImage(representative.bookUrl, null, Modifier.fillMaxSize(), targetWidth = 240)
            Text(
                day.toString(),
                Modifier.padding(3.dp).background(BookInk, CircleShape).padding(horizontal = 4.dp),
                color = BookHighlight,
                fontSize = TvType.Tiny,
                maxLines = 1,
            )
            if (photos.size > 1) {
                Text(
                    photos.size.toString(),
                    Modifier.align(Alignment.BottomEnd).padding(3.dp)
                        .background(BookInk, CircleShape).padding(horizontal = 4.dp),
                    color = BookHighlight,
                    fontSize = TvType.Tiny,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DayReader(
    date: LocalDate,
    photos: List<MediaItem>,
    index: Int,
    onPage: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val pages = remember(photos) { photos.chunked(2) }
    val page = index.coerceIn(pages.indices)
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { back.focusAfterLayout() }
    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val direction = event.turnDirection() ?: return@onPreviewKeyEvent false
            val target = (page + direction).coerceIn(pages.indices)
            if (target != page) onPage(target)
            true
        },
    ) {
        AlbumHeader(
            "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日",
            "${photos.size} 张照片",
            onBack,
            back,
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        PageTurn(page, Modifier.weight(1f).fillMaxWidth()) { current ->
            val spread = pages.getOrNull(current) ?: return@PageTurn
            PaperSpread(date, spread, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(TvDimen.SpaceS))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← → 翻页", color = TvColor.TextSecondary, fontSize = TvType.Caption)
            Text("${page + 1} / ${pages.size} 跨页", color = TvColor.TextSecondary, fontSize = TvType.Caption)
        }
    }
}

@Composable
private fun PageTurn(index: Int, modifier: Modifier, content: @Composable (Int) -> Unit) {
    AnimatedContent(
        targetState = index,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (fadeIn(tween(240)) + slideInHorizontally(tween(300)) { it / 9 * direction }) togetherWith
                (fadeOut(tween(190)) + slideOutHorizontally(tween(270)) { -it / 12 * direction })
        },
        label = "artBookTurn",
    ) { page ->
        val angle by transition.animateFloat(
            transitionSpec = { tween(300) },
            label = "artBookHinge",
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> -16f
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> 14f
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationY = angle
                cameraDistance = size.width * 2f
                transformOrigin = TransformOrigin.Center
            },
        ) { content(page) }
    }
}

private fun KeyEvent.turnDirection(): Int? = when (key) {
    Key.DirectionLeft -> -1
    Key.DirectionRight -> 1
    else -> null
}

@Composable
private fun PaperSpread(date: LocalDate, photos: List<MediaItem>, modifier: Modifier = Modifier) {
    val hasPair = photos.size == 2
    val pageShape = RoundedCornerShape(TvDimen.RadiusM)
    Row(modifier.clip(pageShape).background(BookPaper).border(1.dp, BookEdge, pageShape)) {
        Column(
            Modifier.weight(0.42f).fillMaxHeight().padding(TvDimen.SpaceM),
        ) {
            Text("${date.year} 年 · ${date.monthValue} 月", color = BookSecondary, fontSize = TvType.Caption)
            Text(
                "${date.dayOfMonth} 日",
                color = BookInk,
                fontSize = TvType.CinemaCompactDisplay,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(TvDimen.SpaceS))
            if (hasPair) {
                BookPhoto(photos.first(), Modifier.weight(1f), TvImage.AlbumCoverPx)
            }
        }
        Box(
            Modifier.fillMaxHeight().width(TvDimen.SpaceXs)
                .background(Brush.horizontalGradient(listOf(BookPaper, BookEdge, BookHighlight))),
        )
        Column(
            Modifier.weight(0.58f).fillMaxHeight().background(BookHighlight).padding(TvDimen.SpaceM),
        ) {
            BookPhoto(photos.last(), Modifier.weight(1f), TvImage.DetailBackdropPx)
        }
    }
}

@Composable
private fun BookPhoto(photo: MediaItem, modifier: Modifier, width: Int) {
    Column(modifier.fillMaxWidth()) {
        MediaImage(
            photo.bookUrl,
            photo.title.ifBlank { "照片" },
            Modifier.weight(1f).fillMaxWidth().background(BookMatte),
            contentScale = ContentScale.Fit,
            targetWidth = width,
            placeholder = MediaPlaceholder.HOLD,
        )
        Spacer(Modifier.height(TvDimen.SpaceXs))
        Text(
            "${photo.dateTaken.monthValue} 月 ${photo.dateTaken.dayOfMonth} 日",
            color = BookSecondary,
            fontSize = TvType.Caption,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumHeader(
    title: String,
    summary: String,
    onBack: () -> Unit,
    requester: FocusRequester,
    onDown: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TvFocusBox(
            modifier = Modifier.heightIn(min = TvDimen.ControlHeight)
                .background(TvColor.Surface, RoundedCornerShape(TvDimen.RadiusS))
                .padding(horizontal = TvDimen.SpaceS),
            focusRequester = requester,
            onConfirm = onBack,
            onDown = onDown,
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = TvColor.TextPrimary)
                Spacer(Modifier.width(TvDimen.SpaceXs))
                Text("返回", color = TvColor.TextPrimary, fontSize = TvType.Label)
            }
        }
        Spacer(Modifier.width(TvDimen.SpaceM))
        Text(
            title,
            Modifier.weight(1f),
            color = TvColor.TextPrimary,
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(TvDimen.SpaceS))
        Text(summary, color = TvColor.Warm, fontSize = TvType.Caption, maxLines = 1)
    }
}

private suspend fun FocusRequester.focusAfterLayout() {
    repeat(3) {
        withFrameMillis { }
        if (runCatching { requestFocus() }.isSuccess) return
    }
}

private data class YearBook(val year: Int, val months: List<BookMonth>, val cover: MediaItem, val photoCount: Int)
private data class BookMonth(
    val number: Int,
    val days: Map<Int, List<MediaItem>>,
    val photoCount: Int,
)

private fun assembleBooks(input: List<MediaItem>): List<YearBook> = input.asSequence()
    .filter { it.type == MediaType.PHOTO && it.bookUrl.isNotBlank() }
    .distinctBy { it.id.ifBlank { it.bookUrl } }
    .groupBy { it.dateTaken.year }
    .toSortedMap(compareByDescending { it })
    .map { (year, photos) ->
        val byMonth = photos.sortedBy(MediaItem::dateTaken).groupBy { it.dateTaken.monthValue }
        val months = (1..12).map { month ->
            val items = byMonth[month].orEmpty()
            BookMonth(month, items.groupBy { it.dateTaken.dayOfMonth }, items.size)
        }
        YearBook(
            year = year,
            months = months,
            cover = photos.lastOrNull { it.starred } ?: photos.maxBy(MediaItem::dateTaken),
            photoCount = photos.size,
        )
    }

private val MediaItem.bookUrl: String get() = thumbnailUrl.ifBlank { url }
private val Weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
private val BookPaper = Color(0xFFF1E7D8)
private val BookHighlight = Color(0xFFF8F0E4)
private val BookMatte = Color(0xFFE9DED0)
private val BookInk = Color(0xFF282018)
private val BookSecondary = Color(0xFF6A5341)
private val BookEdge = Color(0xFFC3AD9A)
private val BookSpine = Color(0xFF92624A)

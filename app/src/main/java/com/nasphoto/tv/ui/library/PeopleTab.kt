package com.nasphoto.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.model.Person
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * ================= Tab：人物（NAS 人脸聚类结果） =================
 *
 * 为什么独立成 Tab：「找家人 / 找自己」是相册里最高频的浏览方式。
 * 塞在「收藏 · 回忆」里的一行横排只能一路往右翻，进到某个人的照片还要多按两次键；
 * 独立成 Tab 后，顶栏一次 DOWN 就能落在人物脸上。
 *
 * 【焦点】与 HomeTab / AlbumsTab 完全同一套：卡片按索引懒创建 FocusRequester，
 * 聚焦时只记 index，落点由 PendingFocus 精确给出；行/网格以 TvDimen.ScreenPadding 作
 * contentPadding，卡片 1.08 缩放的溢出不会被裁。
 *
 * 【空数据】peopleLoaded == false 时是「还在读」而不是「没有人物」——
 * 人脸接口通常比相册列表慢，先给加载态才不会闪一下「未开启人脸识别」的引导页。
 */
@Composable
fun PeopleTab(
    people: List<Person>,
    peopleLoaded: Boolean,
    onOpenPerson: (Person) -> Unit,
    contentFocus: PendingFocus,
    /** 从人物详情 BACK 回来时要落回的人物索引（外层用 rememberSaveable 记住的落点） */
    restoreIndex: Int = 0,
    /** 聚焦变化上报给外层保存，详情页返回后才有「回到原来那个人」可言 */
    onFocusPerson: (Int) -> Unit = {},
    /**
     * 第一排按 UP 时的落点：显式交回顶栏当前 Tab。
     * 不交给 Compose 空间查找 —— 人物卡片是 260 宽、顶栏 Tab 位置不固定，
     * 空间查找可能落到账号胶囊或图标按钮上，遥控器体验会"飘"。
     */
    onUpToTopBar: () -> Unit = {},
) {
    // 一排放得下 6 张（1920 - 两侧 ScreenPadding 的可用宽度刚好），超过就换 6 列网格：
    // 人物多的 NAS 一屏能扫完，而不是把遥控器右键按到底。
    val useRow = people.size <= PEOPLE_ROW_MAX
    val columns = PEOPLE_GRID_COLUMNS
    // 从详情 BACK 回来时人可能已经滚出可视区，而 Lazy 行只会组合当前可见项：
    // 让行状态直接初始化在恢复位置，目标卡片才存在于组合树里，requestFocus 才有意义。
    // lastIndex 在空列表上是 -1，coerceIn(0, -1) 会抛空区间异常，必须先抬到 0
    val safeRestore = restoreIndex.coerceIn(0, people.lastIndex.coerceAtLeast(0))
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = (safeRestore - 1).coerceAtLeast(0),
    )
    // 网格同理：初始化在恢复位置所在行，否则远处那张卡还没组合，requestFocus 会静默失败、
    // 焦点卡在顶栏（比"落在第一排"更难用，因为用户看不出发生了什么）。
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (safeRestore / columns) * columns,
    )
    val requesters = remember { mutableListOf<FocusRequester>() }
    var focusedIndex by remember(useRow) { mutableIntStateOf(safeRestore) }

    ConsumeBlockRequest(contentFocus, BLOCK_CONTENT, blockFallback = {
        people.getOrNull(focusedIndex)?.let { requesterAt(requesters, focusedIndex) }
    })

    LaunchedEffect(focusedIndex, useRow) {
        if (useRow) scrollRowTo(rowState, focusedIndex) else scrollGridTo(gridState, focusedIndex, columns)
    }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = "人物",
            trailing = if (people.isEmpty()) "" else "${people.size} 位",
            modifier = Modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
        )
        when {
            // 还在读人脸结果：给进度而不是空态，避免误判「没开 AI 相册」
            !peopleLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TvColor.Accent)
                    Spacer(Modifier.height(TvDimen.SpaceM))
                    Text("正在读取 NAS 上的人物分类…", color = TvColor.TextTertiary, fontSize = TvType.Caption)
                }
            }

            people.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "还没有人物",
                    subtitle = "人物分类需要 NAS 开启「AI 相册 / 人脸识别」并完成识别，开启后这里会按人物列出照片",
                    icon = Icons.Outlined.Person,
                )
            }

            useRow -> LazyRow(
                state = rowState,
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(people, key = { _, it -> it.id }) { index, person ->
                    PersonCard(
                        person = person,
                        focusRequester = requesterAt(requesters, index),
                        onClick = { onOpenPerson(person) },
                        onFocusChanged = { focused ->
                            if (focused) {
                                focusedIndex = index
                                onFocusPerson(index)
                            }
                        },
                        onUp = onUpToTopBar,
                        modifier = Modifier.width(PERSON_CARD_W.dp).height(PERSON_CARD_H.dp),
                    )
                }
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = GridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(people, key = { _, it -> it.id }) { index, person ->
                    PersonCard(
                        person = person,
                        focusRequester = requesterAt(requesters, index),
                        onClick = { onOpenPerson(person) },
                        onFocusChanged = { focused ->
                            if (focused) {
                                focusedIndex = index
                                onFocusPerson(index)
                            }
                        },
                        // 只有第一排显式上顶栏，其余交给 Compose 做"上一排同列"
                        onUp = if (index < columns) onUpToTopBar else null,
                        // 网格里宽度交给格子，固定 260 会在 282 的格子里靠左留一条缝
                        modifier = Modifier.fillMaxWidth().height(PERSON_CARD_H.dp),
                    )
                }
            }
        }
    }
}

/**
 * 人物卡片 260×320：上方是头像区（方正），下方是名字 + 张数。
 *
 * 头像为什么用首字兜底：AI 聚类返回的人物经常没有 coverUrl（还没挑封面，
 * 或该人物只有视频），只画一个破图图标会让整排人看起来都"坏了"。
 */
@Composable
private fun PersonCard(
    person: Person,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 16,
        onFocusChanged = onFocusChanged,
        onConfirm = onClick,
        onUp = onUp,
        modifier = modifier
            .clip(RoundedCornerShape(TvDimen.RadiusM))
            .background(TvColor.BgElevated),
    ) { focused ->
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TvColor.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (person.coverUrl.isNotBlank()) {
                    MediaImage(
                        url = person.coverUrl,
                        contentDescription = person.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetWidth = 480,
                    )
                } else {
                    Text(
                        text = person.initial,
                        color = TvColor.Accent,
                        fontSize = TvType.Display,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TvDimen.SpaceS, vertical = TvDimen.SpaceXs),
            ) {
                Text(
                    text = person.displayName,
                    color = if (focused) TvColor.Accent else TvColor.TextPrimary,
                    fontSize = TvType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 张数可能取不到（服务端没给 itemCount），0 时不显示比显示「0 张」诚实
                if (person.itemCount > 0) {
                    Text(
                        text = "${person.itemCount} 张",
                        color = TvColor.TextTertiary,
                        fontSize = TvType.Tiny,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 卡片尺寸：4:5 竖版，一屏 6 张正好铺满可用宽度 */
private const val PERSON_CARD_W = 260
private const val PERSON_CARD_H = 320
private const val PEOPLE_ROW_MAX = 6
private const val PEOPLE_GRID_COLUMNS = 6

// ============================== Preview ==============================

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PeopleTabPreview() {
    NasPhotoTvTheme {
        PeopleTab(
            people = LibraryPreviewData.people,
            peopleLoaded = true,
            onOpenPerson = {},
            contentFocus = remember { PendingFocus() },
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PeopleTabGridPreview() {
    NasPhotoTvTheme {
        // 超过一排（>6）才会走 6 列网格，这里复制一份验证网格态
        PeopleTab(
            people = LibraryPreviewData.people + LibraryPreviewData.people.map {
                it.copy(id = it.id + "-b", itemCount = it.itemCount / 2)
            },
            peopleLoaded = true,
            onOpenPerson = {},
            contentFocus = remember { PendingFocus() },
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PeopleTabEmptyPreview() {
    NasPhotoTvTheme {
        PeopleTab(
            people = emptyList(),
            peopleLoaded = true,
            onOpenPerson = {},
            contentFocus = remember { PendingFocus() },
        )
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PeopleTabLoadingPreview() {
    NasPhotoTvTheme {
        PeopleTab(
            people = emptyList(),
            peopleLoaded = false,
            onOpenPerson = {},
            contentFocus = remember { PendingFocus() },
        )
    }
}

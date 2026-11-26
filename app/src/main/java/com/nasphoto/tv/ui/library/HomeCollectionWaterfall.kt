package com.nasphoto.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.common.MediaPlaceholder
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import kotlinx.coroutines.launch

/** 按封面浏览全部飞牛智能分类，照片与视频仍在打开分类时加载。 */
@Composable
internal fun HomeCollectionWaterfall(
    collections: List<HomeCollection>,
    selectedIndex: Int,
    onSelect: (HomeCollection) -> Unit,
    onOpenCollection: (HomeCollection) -> Unit,
    contentFocus: PendingFocus,
    onUpToTopBar: () -> Unit,
    onClose: () -> Unit,
) {
    val landingIndex = selectedIndex.coerceIn(0, collections.lastIndex.coerceAtLeast(0))
    val gridState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = landingIndex)
    val backRequester = remember { FocusRequester() }
    val requestsById = remember { mutableMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()
    fun requesterFor(id: String) = requestsById.getOrPut(id, ::FocusRequester)

    suspend fun focusSelected() {
        val collection = collections.getOrNull(landingIndex)
        if (collection != null && gridState.layoutInfo.visibleItemsInfo.none { it.key == collection.id }) {
            gridState.scrollToItem(landingIndex)
        }
        val requester = collection?.let { requesterFor(it.id) } ?: backRequester
        repeat(4) {
            withFrameMillis { }
            if (runCatching { requester.requestFocus() }.isSuccess) return
        }
    }

    val token = contentFocus.token
    LaunchedEffect(token, collections.isEmpty()) {
        focusSelected()
        contentFocus.consume(token)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = TvDimen.ScreenPadding,
                vertical = TvDimen.SpaceXs,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("全部智能分类", color = TvColor.TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Text("${collections.size} 个分类", color = TvColor.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            DetailAction(
                label = "返回回忆",
                modifier = Modifier.verticalNav(
                    onUp = onUpToTopBar,
                    onDown = { scope.launch { focusSelected() } },
                ),
                focusRequester = backRequester,
                onClick = onClose,
            )
        }
        if (collections.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无智能分类", color = TvColor.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (maxWidth < 800.dp) 3 else 4
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = TvDimen.ScreenPadding,
                        vertical = TvDimen.SpaceXs,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
                    verticalItemSpacing = TvDimen.SpaceS,
                ) {
                    itemsIndexed(collections, key = { _, collection -> collection.id }) { index, collection ->
                        val height = when (index % 5) {
                            0, 3 -> 166.dp
                            1 -> 124.dp
                            else -> 146.dp
                        }
                        val shape = RoundedCornerShape(TvDimen.CinemaThumbnailRadius)
                        TvFocusBox(
                            modifier = Modifier.fillMaxWidth().height(height)
                                .semantics(mergeDescendants = true) { role = Role.Button }
                                .clip(shape)
                                .background(TvColor.Surface),
                            focusRequester = requesterFor(collection.id),
                            focusedScale = 1.035f,
                            cornerRadiusDp = 8,
                            onUp = if (index < columns) ({ backRequester.requestFocus() }) else null,
                            onFocusChanged = { if (it) onSelect(collection) },
                            onConfirm = {
                                onSelect(collection)
                                onOpenCollection(collection)
                            },
                        ) {
                            val cover = collection.cover
                            MediaImage(
                                cover.thumbnailUrl.ifBlank { cover.url },
                                null,
                                Modifier.fillMaxSize(),
                                targetWidth = 420,
                                placeholder = MediaPlaceholder.HOLD,
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, TvColor.Bg)))
                                    .padding(horizontal = TvDimen.SpaceS, vertical = TvDimen.SpaceXs),
                            ) {
                                Text(
                                    collection.title,
                                    color = TvColor.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (collection.itemCount > 0) {
                                    Text(
                                        "${collection.itemCount} 项",
                                        color = TvColor.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

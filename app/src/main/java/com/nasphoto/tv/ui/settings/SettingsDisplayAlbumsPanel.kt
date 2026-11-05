package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.mediaIdentityKey
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/** 换账号时缓存可能还未清空：设置列表也必须核对归属，不能显示上个账号的相册。 */
internal fun currentDisplayAlbums(store: SettingsStore): List<Album> {
    val accountId = store.nas.activeAccountId
    if (accountId.isBlank() || store.nas.account(accountId) == null || MediaCache.ownerAccountId != accountId ||
        MediaCache.ownerIdentityKey != store.nas.mediaIdentityKey()) {
        return emptyList()
    }
    return MediaCache.albums.filter { it.id.isNotBlank() }.distinctBy { it.id }
}

internal fun displayAlbumSelectionCount(store: SettingsStore): Int =
    currentDisplayAlbums(store).count { it.id in store.displayAlbumIds }

/** 确认逐项勾选；上下显式移动，左键或返回退出并将焦点交还入口。 */
@Composable
internal fun SettingsDisplayAlbumsPanel(store: SettingsStore, onClose: () -> Unit) {
    val accountId = store.nas.activeAccountId
    val identityKey = store.nas.mediaIdentityKey()
    val albums = currentDisplayAlbums(store)
    val selectedIds = store.displayAlbumIds
    val selectedCount = albums.count { it.id in selectedIds }
    val unavailableCount = selectedIds.count { id -> albums.none { it.id == id } }
    val rowIds = listOf("back", "clear") + albums.map { "album:${it.id}" }
    val requesters = remember(identityKey, rowIds) { List(rowIds.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedIndex by remember(identityKey) { mutableIntStateOf(0) }

    LaunchedEffect(identityKey, rowIds, focusedIndex) {
        val index = focusedIndex.coerceIn(rowIds.indices)
        listState.scrollToItem(index)
        withFrameMillis { }
        runCatching { requesters[index].requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionDown -> {
                    focusedIndex = (focusedIndex + 1).coerceAtMost(rowIds.lastIndex)
                    true
                }
                Key.DirectionLeft -> { onClose(); true }
                Key.DirectionRight -> true
                else -> false
            }
        },
    ) {
        PanelTitle("客厅展示相册", "用于首页回忆和屏保 · 已选 $selectedCount 个")
        Spacer(Modifier.height(TvDimen.SpaceS))
        Text(
            when {
                accountId.isBlank() || store.nas.account(accountId) == null -> "请先配置 NAS 账号，再选择可展示的相册。"
                albums.isEmpty() -> "当前账号暂无可选相册。返回刷新媒体库后再试。"
                unavailableCount > 0 -> "$unavailableCount 个已选相册暂不可用，不会展示其中的照片。"
                else -> "仅展示勾选的相册。未选择时，首页回忆和屏保不会显示照片。"
            },
            fontSize = TvType.Caption, color = TvColor.TextSecondary,
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(TvDimen.SpaceXs),
            verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
        ) {
            item(key = rowIds[0]) {
                SettingsFocusRow(
                    focusRequester = requesters[0], onConfirm = onClose,
                    onFocused = { if (it) focusedIndex = 0 }, rowHeight = TvDimen.ControlHeight,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = TvDimen.SpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = TvColor.TextPrimary,
                            modifier = Modifier.size(TvDimen.IconSize))
                        Text("返回设置", color = TvColor.TextPrimary, fontSize = TvType.Caption)
                    }
                }
            }
            item(key = rowIds[1]) {
                SettingsFocusRow(
                    focusRequester = requesters[1], onConfirm = {
                        if (store.nas.mediaIdentityKey() == identityKey) store.clearDisplayAlbums()
                    },
                    onFocused = { if (it) focusedIndex = 1 }, rowHeight = TvDimen.ControlHeight,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = TvDimen.SpaceM),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("全部取消", color = TvColor.TextPrimary, fontSize = TvType.Caption)
                        Spacer(Modifier.weight(1f))
                        Text("停止照片展示", color = TvColor.TextMuted, fontSize = TvType.Caption)
                    }
                }
            }
            itemsIndexed(albums, key = { _, album -> "album:${album.id}" }) { index, album ->
                val selected = album.id in selectedIds
                SettingsFocusRow(
                    focusRequester = requesters[index + 2],
                    onConfirm = {
                        // A queued key event must not authorize an old row after an identity change.
                        if (store.nas.mediaIdentityKey() == identityKey && MediaCache.ownerIdentityKey == identityKey) {
                            store.setDisplayAlbumEnabled(album.id, !selected)
                        }
                    },
                    onFocused = { if (it) focusedIndex = index + 2 },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = TvDimen.SpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
                        Icon(if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            null, tint = if (selected) TvColor.Accent else TvColor.TextSecondary,
                            modifier = Modifier.size(TvDimen.IconSize))
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            Text(album.name, fontSize = TvType.Body, fontWeight = FontWeight.Medium,
                                color = TvColor.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.itemCount} 项", fontSize = TvType.Caption, color = TvColor.TextMuted)
                        }
                        Text(if (selected) "已选" else "选择", fontSize = TvType.Caption,
                            color = if (selected) TvColor.Accent else TvColor.TextSecondary)
                    }
                }
            }
        }
    }
}

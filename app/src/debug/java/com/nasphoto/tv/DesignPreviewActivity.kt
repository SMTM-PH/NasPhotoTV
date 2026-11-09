package com.nasphoto.tv

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nasphoto.tv.data.AppContextHolder
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.displayPhotos
import com.nasphoto.tv.data.mediaIdentityKey
import com.nasphoto.tv.data.model.NasAccount
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.Person
import com.nasphoto.tv.ui.AppRoute
import com.nasphoto.tv.ui.LibraryState
import com.nasphoto.tv.ui.ViewerRequest
import com.nasphoto.tv.ui.library.LibraryScreen
import com.nasphoto.tv.ui.screensaver.ScreensaverScreen
import com.nasphoto.tv.ui.settings.SettingsScreen
import com.nasphoto.tv.ui.settings.currentDisplayAlbums
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.viewer.PhotoViewerScreen
import java.time.LocalDate

/**
 * Debug-only offline review of the real Compose screens with clearly named sample data.
 * No repository, NAS credentials, discovery, settings restore or settings persist runs here.
 * Launch with optional `--ei tab 2` or `--es screen settings|viewer|screensaver`.
 */
class DesignPreviewActivity : ComponentActivity() {
    private var previousContext: Context? = null
    private val previousNas = SettingsStore.nas
    private val previousScreensaver = SettingsStore.screensaver
    private val previousPlayback = SettingsStore.playback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousContext = AppContextHolder.context
        // Favorites/pinning from this preview must never write to the user's normal storage.
        AppContextHolder.context = PreviewStorageContext(applicationContext)
        FavoritesStore.load("design-preview")
        SettingsStore.reset()
        SettingsStore.updateNas { it.copy(name = "示例 NAS", host = "",
            accounts = listOf(NasAccount(id = "design-preview", username = "preview", label = "设计预览"),
                NasAccount(id = "design-other", username = "other")), activeAccountId = "design-preview") }
        SettingsStore.updateScreensaver { it.copy(enabled = false, kenBurns = false) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val baseSample = previewLibrary(intent.getIntExtra("tab", 0))
        val emptyAlbum = Album("design-empty", "空的展示相册", "", 0)
        val sample = baseSample.copy(ownerAccountId = "design-preview",
            albums = baseSample.albums + emptyAlbum,
            mediaByAlbum = baseSample.mediaByAlbum + (emptyAlbum.id to emptyList()))
        MediaCache.update(sample.recent, sample.albums, sample.recent,
            photoCount = sample.recent.size, videoCount = 0, people = sample.people,
            ownerAccountId = "design-preview", ownerIdentityKey = SettingsStore.nas.mediaIdentityKey())
        val previewNas = SettingsStore.nas
        check(currentDisplayAlbums(SettingsStore).size == sample.albums.size)
        SettingsStore.updateNas { it.copy(host = "identity-check.invalid") }
        check(currentDisplayAlbums(SettingsStore).isEmpty())
        SettingsStore.updateNas { previewNas }
        check(currentDisplayAlbums(SettingsStore).size == sample.albums.size)
        check(displayPhotos(sample.albums, sample.mediaByAlbum, emptySet()).isEmpty())
        check(displayPhotos(sample.albums, sample.mediaByAlbum, setOf("missing-album")).isEmpty())
        check(displayPhotos(sample.albums, sample.mediaByAlbum, setOf(emptyAlbum.id)).isEmpty())
        val approved = setOf("design-album-0", "design-album-1")
        check(displayPhotos(sample.albums, sample.mediaByAlbum, approved).all { it.albumId in approved })
        SettingsStore.setDisplayAlbumEnabled("design-album-0", true)
        SettingsStore.switchAccount("design-other")
        check(SettingsStore.displayAlbumIds.isEmpty())
        SettingsStore.switchAccount("design-preview")
        SettingsStore.clearDisplayAlbums()
        when {
            intent.getBooleanExtra("empty-display", false) -> {}
            intent.getBooleanExtra("empty-album", false) -> SettingsStore.setDisplayAlbumEnabled(emptyAlbum.id, true)
            else -> approved.forEach { SettingsStore.setDisplayAlbumEnabled(it, true) }
        }
        android.util.Log.i("DisplayScopeQA", "PASS empty, stale, empty-album, approved-only, account-switch, identity-change")
        val initialScreen = intent.getStringExtra("screen").orEmpty()
        setContent {
            NasPhotoTvTheme {
                val libraryStateHolder = rememberSaveableStateHolder()
                var route by remember {
                    mutableStateOf<AppRoute>(when (initialScreen) {
                        "settings" -> AppRoute.Settings
                        "screensaver" -> AppRoute.ScreensaverPreview
                        else -> AppRoute.Library
                    })
                }
                var viewer by remember {
                    mutableStateOf<ViewerRequest?>(
                        if (initialScreen == "viewer") ViewerRequest(sample.recent, 0, "示例照片") else null,
                    )
                }
                BackHandler(enabled = viewer == null && route != AppRoute.Library) {
                    route = AppRoute.Library
                }
                Box(Modifier.fillMaxSize().background(TvColor.Bg)) {
                    val activeViewer = viewer
                    if (activeViewer != null) {
                        PhotoViewerScreen(
                            items = activeViewer.items,
                            initialIndex = activeViewer.initialIndex,
                            contextTitle = activeViewer.contextTitle,
                            startSlideshow = activeViewer.startSlideshow,
                            onClose = { viewer = null },
                        )
                    } else when (route) {
                        AppRoute.Library -> libraryStateHolder.SaveableStateProvider("library") {
                            LibraryScreen(
                                state = sample,
                                onOpenPhoto = { items, index, title ->
                                    viewer = ViewerRequest(items, index, title)
                                },
                                onPlaySlideshow = { items, index, title ->
                                    viewer = ViewerRequest(items, index, title, startSlideshow = true)
                                },
                                onOpenVideo = { _, _, _ -> },
                                onNavigate = { route = it },
                                onLoadPersonPhotos = { sample.recent },
                            )
                        }
                        AppRoute.Settings -> SettingsScreen(
                            store = SettingsStore,
                            onClose = { route = AppRoute.Library },
                            onPreviewScreensaver = { route = AppRoute.ScreensaverPreview },
                            onOpenLibrary = { route = AppRoute.Library },
                            screensaverPhotos = displayPhotos(sample.albums, sample.mediaByAlbum, SettingsStore.displayAlbumIds),
                        )
                        AppRoute.ScreensaverPreview -> ScreensaverScreen(
                            settings = SettingsStore.screensaver,
                            photos = displayPhotos(sample.albums, sample.mediaByAlbum, SettingsStore.displayAlbumIds),
                            onExit = { route = AppRoute.Library },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SettingsStore.updateNas { previousNas }
        SettingsStore.updateScreensaver { previousScreensaver }
        SettingsStore.updatePlayback { previousPlayback }
        AppContextHolder.context = previousContext
        FavoritesStore.load(previousNas.activeAccountId)
        super.onDestroy()
    }
}

/** Separate preferences namespace even if normal MainActivity was used on the same emulator. */
private class PreviewStorageContext(base: Context) : ContextWrapper(base) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences("design_preview_$name", mode)
}

private fun previewLibrary(tab: Int): LibraryState {
    val scenes = listOf(
        "wall_ocean" to "蓝色海岸",
        "wall_forest" to "山野漫游",
        "wall_sunset" to "落日之后",
        "wall_aurora" to "湖山来信",
        "wall_mono" to "星夜山川",
        "wall_candy" to "湖畔时光",
    )
    fun resource(name: String) = "android.resource://com.nasphoto.tv/raw/design_$name"
    val items = scenes.mapIndexed { index, (file, title) ->
        MediaItem(
            id = "design-sample-$index",
            title = title,
            type = MediaType.PHOTO,
            url = resource(file),
            dateTaken = when (index) {
                4 -> LocalDate.of(2025, 11, 4)
                5 -> LocalDate.of(2024, 11, 4)
                else -> LocalDate.of(2026, 11, 4).minusDays(index.toLong() * 18)
            },
            sizeBytes = 4_200_000L + index * 100_000,
            resolution = "3840x2160",
            albumId = "design-album-${index % 4}",
            starred = index % 3 == 0,
            extension = "JPG",
        )
    }
    val albumNames = listOf("山海之间", "山野漫游", "日落收藏", "一起的时光")
    val albums = albumNames.mapIndexed { index, title ->
        Album(
            id = "design-album-$index",
            name = title,
            coverUrl = items[index].url,
            itemCount = items.count { it.albumId == "design-album-$index" },
            subtitle = "示例相册 · 2026",
        )
    }
    return LibraryState(
        recent = items,
        albums = albums,
        mediaByAlbum = albums.associate { album -> album.id to items.filter { it.albumId == album.id } },
        selectedTabIndex = tab.coerceIn(0, 5),
        connectionName = "本地示例图库",
        isOnline = true,
        accountName = "设计预览",
        people = listOf(
            Person("design-person-1", "示例人物 A", itemCount = 6),
            Person("design-person-2", "示例人物 B", itemCount = 6),
            Person("design-person-3", "示例人物 C", itemCount = 6),
        ),
    )
}

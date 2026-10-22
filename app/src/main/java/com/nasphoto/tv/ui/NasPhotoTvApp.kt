package com.nasphoto.tv.ui

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import com.nasphoto.tv.data.FnOsMediaRepository
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.fn.LanNasDevice
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.connection.ConnectionMode
import com.nasphoto.tv.ui.connection.ConnectionScreen
import com.nasphoto.tv.ui.library.LibraryScreen
import com.nasphoto.tv.ui.screensaver.ScreensaverScreen
import com.nasphoto.tv.ui.settings.SettingsScreen
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.video.VideoPlayerScreen
import com.nasphoto.tv.ui.viewer.PhotoViewerScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * ================= 应用外壳 =================
 *
 * 负责：真实数据加载、路由、屏保待机与空闲检测。
 * 媒体数据全部来自飞牛相册；没有配置 NAS 时只显示连接引导，不再使用任何静态假数据。
 */
@Composable
fun NasPhotoTvApp() {
    NasPhotoTvTheme {
        var route by remember { mutableStateOf<AppRoute>(AppRoute.Library) }
        var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
        var player by remember { mutableStateOf<PlayerRequest?>(null) }

        val screensaverSettings = SettingsStore.screensaver
        val playbackSettings = SettingsStore.playback
        val nas = SettingsStore.nas

        var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var screensaverVisible by remember { mutableStateOf(false) }
        var refreshToken by remember { mutableIntStateOf(0) }
        var loadState by remember { mutableStateOf<LibraryLoad>(LibraryLoad.Loading) }
        // 看图/播放/设置都走 AnimatedContent 换内容，库页会被移出组合：
        // 用 SaveableStateHolder 保住 Tab、年/月层级和滚动位置，BACK 才能回到原处。
        val libraryStateHolder = rememberSaveableStateHolder()

        // 只有连接参数变化才重建仓库；「测试连接」只改 connected/latency，不应触发重连
        val repository = remember(
            nas.name,
            nas.host,
            nas.port,
            nas.username,
            nas.password,
            nas.accessCode,
        ) {
            if (nas.isConfigured) FnOsMediaRepository(nas) else null
        }
        DisposableEffect(repository) {
            onDispose { repository?.close() }
        }

        LaunchedEffect(repository, refreshToken, route) {
            if (route != AppRoute.Library) return@LaunchedEffect
            if (repository == null) {
                MediaCache.clear()
                SettingsStore.updateNas { it.copy(connected = false) }
                loadState = LibraryLoad.NeedsSetup
                return@LaunchedEffect
            }
            loadState = LibraryLoad.Loading
            try {
                // 「最近添加」横排与「年·月·日」时间线共用这份数据：
                // 取 24 会让时间线只剩最近一天、用户永远看不到更早的照片，
                // 这里按服务端可分页上限取足，保证时间线覆盖整个照片库。
                val recent = repository.getRecent(1000)
                val albums = repository.getAlbums()
                val albumMedia = coroutineScope {
                    albums.map { album ->
                        async {
                            album.id to runCatching { repository.getMedia(album.id) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll().toMap()
                }
                val screensaverPhotos = runCatching { repository.getScreensaverPhotos() }
                    .getOrDefault(emptyList())
                val stats = runCatching { repository.stats() }.getOrNull()

                MediaCache.update(
                    recent = recent,
                    albums = albums,
                    screensaverPhotos = screensaverPhotos,
                    photoCount = stats?.photoCount ?: recent.count { it.type == MediaType.PHOTO },
                    videoCount = stats?.videoCount ?: recent.count { it.type == MediaType.VIDEO },
                )
                SettingsStore.updateNas { it.copy(connected = true) }
                loadState = LibraryLoad.Ready(
                    library = LibraryState(
                        recent = recent,
                        albums = albums,
                        mediaByAlbum = albumMedia,
                        connectionName = nas.name.ifBlank { "Home-NAS" },
                        isOnline = true,
                    ),
                    screensaverPhotos = screensaverPhotos,
                )
            } catch (t: Throwable) {
                SettingsStore.updateNas { it.copy(connected = false) }
                loadState = LibraryLoad.Error(t.message ?: "无法连接 NAS")
            }
        }

        // 屏保实际使用的照片池：按来源切换（人物需要走 NAS 人脸接口，失败时回退到最近）
        var peoplePhotos by remember { mutableStateOf<List<MediaItem>?>(null) }
        var saverPhotos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        LaunchedEffect(refreshToken) { peoplePhotos = null }
        LaunchedEffect(
            loadState,
            screensaverSettings.mode,
            screensaverSettings.background,
            screensaverSettings.photoSource,
            screensaverSettings.albumId,
            repository,
        ) {
            val ready = loadState as? LibraryLoad.Ready
            if (ready == null) {
                saverPhotos = emptyList()
                return@LaunchedEffect
            }
            val usesPhotos = screensaverSettings.mode == ScreensaverSettings.Mode.PHOTO_ONLY ||
                screensaverSettings.background == ScreensaverSettings.BackgroundStyle.PHOTO
            if (!usesPhotos) {
                saverPhotos = emptyList()
                return@LaunchedEffect
            }
            val recentPool = ready.screensaverPhotos
            saverPhotos = when (screensaverSettings.photoSource) {
                ScreensaverSettings.PhotoSource.RECENT,
                ScreensaverSettings.PhotoSource.RANDOM,
                -> recentPool

                ScreensaverSettings.PhotoSource.ALBUM -> {
                    val albumId = screensaverSettings.albumId
                        .ifBlank { ready.library.albums.firstOrNull()?.id.orEmpty() }
                    ready.library.mediaByAlbum[albumId].orEmpty()
                        .filter { it.type == MediaType.PHOTO }
                        .ifEmpty { recentPool }
                }

                ScreensaverSettings.PhotoSource.PEOPLE -> {
                    val cached = peoplePhotos
                    if (cached != null) {
                        cached.ifEmpty { recentPool }
                    } else {
                        // 先给最近照片，避免人脸接口慢时屏保空窗
                        saverPhotos = recentPool
                        val fetched = runCatching { repository?.getPeoplePhotos() }
                            .getOrNull()
                            .orEmpty()
                        peoplePhotos = fetched
                        fetched.ifEmpty { recentPool }
                    }
                }
            }
        }

        // 任何设置变更立即落盘
        LaunchedEffect(screensaverSettings, playbackSettings, nas) {
            SettingsStore.persist()
        }

        // 首次连接页搜到 NAS：先填地址和端口，再进设置补账号密码
        val selectDiscoveredNas: (LanNasDevice) -> Unit = { device ->
            SettingsStore.updateNas { it.copy(host = device.host, port = device.port) }
            route = AppRoute.Settings
        }

        // 显式进入屏保（顶栏 / 设置预览）：屏保关闭时顺手打开，退出计时重新起算
        val enterScreensaver = {
            if (!screensaverSettings.enabled) {
                SettingsStore.updateScreensaver { it.copy(enabled = true) }
            }
            lastInteractionMs = System.currentTimeMillis()
            screensaverVisible = true
        }

        // 任意按键刷新「最后交互时间」（Compsoe 层：View 级监听收不到 Compose 已消费的按键）
        val view = LocalView.current
        DisposableEffect(view) {
            val handler = View.OnKeyListener { _, keyCode, event ->
                lastInteractionMs = System.currentTimeMillis()
                // Compose 里没有任何节点持有焦点时，按键会落到 View 层：这里兜底退出屏保。
                // 左右键必须放行给屏保，用于切换时钟样式/轮播来源。
                if (screensaverVisible && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    val switchKey = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                        keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    if (switchKey) {
                        false
                    } else {
                        screensaverVisible = false
                        true
                    }
                } else false
            }
            view.setOnKeyListener(handler)
            onDispose { view.setOnKeyListener(null) }
        }

        // 屏保覆盖层若没抢到焦点（下层页面仍持有），按键会走下层页面的预览链。
        // 在根节点兜底：任何 KeyDown 都退出屏保，并吞掉配对的 KeyUp，避免松手时误触下层。
        var swallowScreensaverKeyUp by remember { mutableStateOf(false) }

        // 空闲检测：有真实照片时才进入屏保
        LaunchedEffect(screensaverSettings.enabled, screensaverSettings.idleTimeoutSec, loadState) {
            while (true) {
                delay(1000)
                val idleSec = screensaverSettings.idleTimeoutSec
                if (!screensaverSettings.enabled || idleSec <= 0) continue
                if (screensaverVisible) continue
                val idle = (System.currentTimeMillis() - lastInteractionMs) / 1000
                val eligible = route != AppRoute.ScreensaverPreview &&
                    player == null &&
                    viewer == null &&
                    loadState is LibraryLoad.Ready
                if (eligible && idle >= idleSec) screensaverVisible = true
            }
        }

        Surface(color = TvColor.Bg, modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        lastInteractionMs = System.currentTimeMillis()
                        if (screensaverVisible && event.type == KeyEventType.KeyDown) {
                            if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                                // 待机界面左右键换样式：时钟模式换表盘，纯照片模式换轮播来源
                                val reverse = event.key == Key.DirectionLeft
                                if (screensaverSettings.mode == ScreensaverSettings.Mode.PHOTO_ONLY) {
                                    SettingsStore.cycleScreensaverSource(MediaCache.albums, reverse)
                                } else {
                                    SettingsStore.cycleClockStyle(reverse)
                                }
                                swallowScreensaverKeyUp = true
                                true
                            } else {
                                screensaverVisible = false
                                swallowScreensaverKeyUp = true
                                true
                            }
                        } else if (swallowScreensaverKeyUp && event.type == KeyEventType.KeyUp) {
                            swallowScreensaverKeyUp = false
                            true
                        } else {
                            false
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = Triple(route, viewer, player),
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "route",
                ) { current ->
                    val r: AppRoute = current.first
                    val v: ViewerRequest? = current.second
                    val p: PlayerRequest? = current.third
                    when {
                        p != null -> VideoPlayerScreen(
                            items = p.items,
                            initialIndex = p.initialIndex,
                            contextTitle = p.contextTitle,
                            settings = playbackSettings,
                            onClose = { player = null },
                            modifier = Modifier.fillMaxSize(),
                        )

                        v != null -> PhotoViewerScreen(
                            items = v.items,
                            initialIndex = v.initialIndex,
                            contextTitle = v.contextTitle,
                            onClose = { viewer = null },
                            onOpenVideo = { item, list ->
                                viewer = null
                                player = PlayerRequest(
                                    items = list,
                                    initialIndex = list.indexOf(item).coerceAtLeast(0),
                                    contextTitle = v.contextTitle,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        r is AppRoute.Settings -> SettingsScreen(
                            store = SettingsStore,
                            onClose = { route = AppRoute.Library },
                            onPreviewScreensaver = enterScreensaver,
                            onOpenLibrary = { route = AppRoute.Library },
                            modifier = Modifier.fillMaxSize(),
                            screensaverPhotos = saverPhotos,
                        )

                        else -> when (val state = loadState) {
                            is LibraryLoad.Ready -> libraryStateHolder.SaveableStateProvider("library") {
                                LibraryScreen(
                                    state = state.library,
                                    onOpenPhoto = { items, index, title ->
                                        viewer = ViewerRequest(items, index, title)
                                    },
                                    onOpenVideo = { items, index, title ->
                                        player = PlayerRequest(items, index, title)
                                    },
                                    onNavigate = { next ->
                                        if (next == AppRoute.ScreensaverPreview) {
                                            route = AppRoute.Library
                                            enterScreensaver()
                                        } else {
                                            route = next
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            is LibraryLoad.NeedsSetup -> ConnectionScreen(
                                mode = ConnectionMode.NeedsSetup,
                                host = nas.host,
                                onOpenSettings = { route = AppRoute.Settings },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )

                            is LibraryLoad.Loading -> ConnectionScreen(
                                mode = ConnectionMode.Loading,
                                host = nas.host,
                                onOpenSettings = { route = AppRoute.Settings },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )

                            is LibraryLoad.Error -> ConnectionScreen(
                                mode = ConnectionMode.Error(state.message),
                                host = nas.host,
                                onOpenSettings = { route = AppRoute.Settings },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // 屏保是覆盖层而非替换：下层页面保持组合，退出后焦点与滚动位置原样回来
                if (screensaverVisible) {
                    ScreensaverScreen(
                        settings = screensaverSettings,
                        photos = saverPhotos,
                        onExit = {
                            screensaverVisible = false
                            lastInteractionMs = System.currentTimeMillis()
                        },
                        onCycleStyle = { SettingsStore.cycleClockStyle() },
                        onCycleSource = {
                            SettingsStore.cycleScreensaverSource(MediaCache.albums)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** 媒体库的加载状态：所有内容都来自 NAS，没有就绪数据时显示对应引导页 */
private sealed interface LibraryLoad {
    data object NeedsSetup : LibraryLoad
    data object Loading : LibraryLoad
    data class Error(val message: String) : LibraryLoad
    data class Ready(
        val library: LibraryState,
        val screensaverPhotos: List<MediaItem>,
    ) : LibraryLoad
}

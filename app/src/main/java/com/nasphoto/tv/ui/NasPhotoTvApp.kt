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
import com.nasphoto.tv.data.AppContextHolder
import com.nasphoto.tv.data.displayPhotos
import com.nasphoto.tv.data.mediaIdentityKey
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.FnOsMediaRepository
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.fn.FnOsTransport
import com.nasphoto.tv.data.fn.LanNasDevice
import com.nasphoto.tv.data.model.Album
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.Person
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.connection.ConnectionMode
import com.nasphoto.tv.ui.connection.ConnectionScreen
import com.nasphoto.tv.ui.library.LibraryScreen
import com.nasphoto.tv.ui.common.NasImageLoader
import com.nasphoto.tv.ui.common.ScreenIdleGuard
import com.nasphoto.tv.ui.screensaver.ScreensaverScreen
import com.nasphoto.tv.ui.settings.SettingsScreen
import com.nasphoto.tv.ui.theme.NasPhotoTvTheme
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.video.VideoPlayerScreen
import com.nasphoto.tv.ui.viewer.PhotoViewerScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

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
        val identityKey = remember(nas.host, nas.port, nas.activeAccountId, nas.username, nas.password, nas.accessCode) {
            nas.mediaIdentityKey()
        }

        var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var screensaverVisible by remember { mutableStateOf(false) }
        var refreshToken by remember { mutableIntStateOf(0) }
        // Reset during composition, before effects run: the previous identity is never Ready for a new one.
        var loadState by remember(identityKey) { mutableStateOf<LibraryLoad>(LibraryLoad.Loading) }
        // 看图/播放/设置都走 AnimatedContent 换内容，库页会被移出组合：
        // 用 SaveableStateHolder 保住 Tab、年/月层级和滚动位置，BACK 才能回到原处。
        val libraryStateHolder = rememberSaveableStateHolder()

        // 只有连接参数变化才重建仓库；「测试连接」只改 connected/latency，不应触发重连。
        // activeAccountId 必须进 key：两个账号可能共用同一套地址，只比较 username/password
        // 也能区分，但显式带上账号 id 能保证"切回同一个账号"也会走一遍重建（token 已过期）。
        val repository = remember(
            nas.name,
            nas.host,
            nas.port,
            nas.activeAccountId,
            nas.username,
            nas.password,
            nas.accessCode,
        ) {
            if (nas.isConfigured) FnOsMediaRepository(nas) else null
        }
        DisposableEffect(repository) {
            onDispose { repository?.close() }
        }

        // 完整身份变化也覆盖同一账号槽位更换用户名/服务器；设置路由同样清理。
        // 顺序很重要 —— 先清 MediaCache/图片缓存、丢掉旧 AccessToken，再重建仓库，最后才发请求，
        // 否则旧账号的照片会在新账号的界面上闪现。这不是显示问题，是隐私问题：
        // 家人切到自己账号后，不能看到上一个账号的缓存缩略图和星标。
        val activeAccountId = nas.activeAccountId
        var previousIdentityKey by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(identityKey) {
            val switched = (previousIdentityKey != null && previousIdentityKey != identityKey) ||
                (MediaCache.ownerAccountId.isNotBlank() && MediaCache.ownerIdentityKey != identityKey)
            previousIdentityKey = identityKey
            // 首次进入只加载该账号的星标，不做破坏性清理（冷启动没有旧数据可清）
            if (switched) {
                MediaCache.clear()
                NasImageLoader.clearCaches(AppContextHolder.context)
                // token 是全局单例，仓库 close() 不会带走它：不清的话新账号登录前
                // 图片请求会带着旧账号的 token 打到 NAS 上
                FnOsTransport.token = null
                viewer = null
                player = null
                screensaverVisible = false
            }
            FavoritesStore.load(activeAccountId)
        }

        LaunchedEffect(identityKey, repository, refreshToken, route) {
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
                // 相册媒体改为进入详情时按需加载（见 LibraryScreen.onLoadAlbumMedia）。
                // 启动时只补拉「客厅展示」授权的那几个相册，供屏保使用；
                // 智能分类、人物、屏保池、统计并行请求，首屏不再串行等几十个大相册。
                var smartAlbumsError: String? = null
                val approvedAlbumIds = SettingsStore.displayAlbumIds
                val optional = coroutineScope {
                    val screensaverDeferred = async {
                        runCatching { repository.getScreensaverPhotos() }.getOrDefault(emptyList())
                    }
                    val peopleDeferred = async {
                        // 人物列表：NAS 没开 AI 相册时接口会失败或返回空，不能让它拖垮整个媒体库
                        runCatching { repository.getPeople() }.getOrDefault(emptyList())
                    }
                    val smartDeferred = async {
                        try {
                            repository.getSmartAlbums()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            smartAlbumsError = "智能分类暂时无法读取 · 请检查飞牛相册的智能识别状态后重试"
                            emptyList()
                        }
                    }
                    val statsDeferred = async {
                        runCatching { repository.stats() }.getOrNull()
                    }
                    val approvedDeferred = async {
                        val ids = approvedAlbumIds.intersect(albums.map { it.id }.toSet())
                        ids.map { id ->
                            async {
                                id to runCatching { repository.getMedia(id) }.getOrDefault(emptyList())
                            }
                        }.awaitAll().toMap()
                    }
                    OptionalLibraryData(
                        screensaverPhotos = screensaverDeferred.await(),
                        people = peopleDeferred.await(),
                        smartAlbums = smartDeferred.await(),
                        stats = statsDeferred.await(),
                        approvedMedia = approvedDeferred.await(),
                    )
                }

                // Some repository helpers wrap failures: explicitly propagate cancellation and
                // reject a late result from an identity that changed while requests were in flight.
                currentCoroutineContext().ensureActive()
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect

                MediaCache.update(
                    recent = recent,
                    albums = albums,
                    screensaverPhotos = optional.screensaverPhotos,
                    photoCount = optional.stats?.photoCount ?: recent.count { it.type == MediaType.PHOTO },
                    videoCount = optional.stats?.videoCount ?: recent.count { it.type == MediaType.VIDEO },
                    people = optional.people,
                    ownerAccountId = nas.activeAccountId,
                    ownerIdentityKey = identityKey,
                )
                SettingsStore.updateNas { it.copy(connected = true) }
                loadState = LibraryLoad.Ready(
                    library = LibraryState(
                        recent = recent,
                        albums = albums,
                        ownerAccountId = nas.activeAccountId,
                        mediaByAlbum = optional.approvedMedia,
                        connectionName = nas.name.ifBlank { "Home-NAS" },
                        isOnline = true,
                        accountName = nas.activeAccount?.displayName.orEmpty(),
                        accountCount = nas.accounts.size.coerceAtLeast(1),
                        people = optional.people,
                        peopleLoaded = true,
                        smartAlbums = optional.smartAlbums,
                        smartAlbumsError = smartAlbumsError,
                    ),
                    screensaverPhotos = optional.screensaverPhotos,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
                SettingsStore.updateNas { it.copy(connected = false) }
                loadState = LibraryLoad.Error(t.message ?: "无法连接 NAS")
            }
        }

        // 用户新授权客厅展示相册后，补拉缺失相册的媒体，屏保不必等整库重载。
        val approvedDisplayIds = SettingsStore.displayAlbumIds
        LaunchedEffect(approvedDisplayIds, repository, identityKey, loadState) {
            val repo = repository ?: return@LaunchedEffect
            val ready = loadState as? LibraryLoad.Ready ?: return@LaunchedEffect
            val missing = approvedDisplayIds - ready.library.mediaByAlbum.keys
            if (missing.isEmpty()) return@LaunchedEffect
            val fetched = coroutineScope {
                missing.map { id ->
                    async {
                        id to runCatching { repo.getMedia(id) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }.toMap()
            currentCoroutineContext().ensureActive()
            if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
            val current = loadState as? LibraryLoad.Ready ?: return@LaunchedEffect
            loadState = current.copy(
                library = current.library.copy(mediaByAlbum = current.library.mediaByAlbum + fetched),
            )
        }

        // 客厅照片严格来自当前账号明确授权的相册；空、失效、切账号均不回退。
        val approvedIds = SettingsStore.displayAlbumIds
        val readyLibrary = (loadState as? LibraryLoad.Ready)?.library
        val scopedSaverPhotos = remember(readyLibrary, approvedIds, identityKey, screensaverSettings.albumId, screensaverSettings.photoSource) {
            if (readyLibrary == null || readyLibrary.ownerAccountId != nas.activeAccountId) emptyList()
            else {
                val ids = if (screensaverSettings.photoSource == ScreensaverSettings.PhotoSource.ALBUM)
                    setOf(screensaverSettings.albumId).intersect(approvedIds) else approvedIds
                displayPhotos(readyLibrary.albums, readyLibrary.mediaByAlbum, ids)
            }
        }
        var peopleIds by remember(identityKey, repository) { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(repository, refreshToken, approvedIds, screensaverSettings.photoSource, scopedSaverPhotos) {
            peopleIds = emptySet()
            if (screensaverSettings.photoSource == ScreensaverSettings.PhotoSource.PEOPLE && scopedSaverPhotos.isNotEmpty()) {
                val ids = runCatching { repository?.getPeoplePhotos().orEmpty().map { it.id }.toSet() }
                    .getOrDefault(emptySet())
                currentCoroutineContext().ensureActive()
                if (SettingsStore.nas.mediaIdentityKey() == identityKey) peopleIds = ids
            }
        }
        val photoPool = if (screensaverSettings.photoSource == ScreensaverSettings.PhotoSource.PEOPLE)
            scopedSaverPhotos.filter { it.id in peopleIds } else scopedSaverPhotos
        val saverPhotos = if (screensaverSettings.mode == ScreensaverSettings.Mode.PHOTO_ONLY ||
            screensaverSettings.background == ScreensaverSettings.BackgroundStyle.PHOTO) photoPool else emptyList()

        // 任何设置变更立即落盘
        LaunchedEffect(screensaverSettings, playbackSettings, nas) {
            SettingsStore.persist()
        }

        // 首次连接页搜到 NAS：先填地址和端口，再进设置补账号密码
        val selectDiscoveredNas: (LanNasDevice) -> Unit = { device ->
            SettingsStore.updateNas { it.copy(host = device.host, port = device.port) }
            route = AppRoute.Settings
        }

        // 显式预览不改变自动屏保开关，退出计时重新起算
        val enterScreensaver = {
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
        LaunchedEffect(screensaverSettings, saverPhotos, loadState) {
            while (true) {
                delay(1000)
                val idleSec = screensaverSettings.idleTimeoutSec
                if (!screensaverSettings.enabled || idleSec <= 0) continue
                if (screensaverVisible) continue
                // 软件输入法打字、语音输入不产生按键事件，会被误判空闲；
                // 输入层持有豁免期内跳过（见 ScreenIdleGuard）
                if (ScreenIdleGuard.isHeld()) continue
                val idle = (System.currentTimeMillis() - lastInteractionMs) / 1000
                val eligible = route != AppRoute.ScreensaverPreview &&
                    player == null &&
                    viewer == null &&
                    loadState is LibraryLoad.Ready
                val photoMode = screensaverSettings.mode == ScreensaverSettings.Mode.PHOTO_ONLY ||
                    screensaverSettings.background == ScreensaverSettings.BackgroundStyle.PHOTO
                if (eligible && idle >= idleSec && (!photoMode || saverPhotos.isNotEmpty())) screensaverVisible = true
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
                            startSlideshow = v.startSlideshow,
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
                            is LibraryLoad.Ready -> libraryStateHolder.SaveableStateProvider(identityKey) {
                                LibraryScreen(
                                    state = state.library,
                                    onOpenPhoto = { items, index, title ->
                                        viewer = ViewerRequest(items, index, title)
                                    },
                                    onPlaySlideshow = { items, index, title ->
                                        viewer = ViewerRequest(items, index, title, startSlideshow = true)
                                    },
                                    onOpenVideo = { items, index, title ->
                                        player = PlayerRequest(items, index, title)
                                    },
                                    // 人物照片不在相册里，只能走人脸接口：把仓库的这个能力注入进去。
                                    // 返回 null = 这次请求失败了（网络/鉴权/AI 服务没响应），
                                    // 不能折叠成 emptyList —— 否则详情页会把「失败」说成「这个人没有照片」。
                                    // repository 为 null（凭据还没配好）同样算失败，让用户看到可重试的提示。
                                    onLoadPersonPhotos = { personId ->
                                        repository?.let { repo ->
                                            runCatching { repo.getPersonPhotos(personId) }.getOrNull()
                                        }
                                    },
                                    onLoadSmartAlbumMedia = { categoryId ->
                                        try {
                                            repository?.getSmartAlbumMedia(categoryId)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
                                    },
                                    onLoadAlbumMedia = { albumId ->
                                        try {
                                            repository?.getMedia(albumId)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
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

/** 与媒体库主数据无关、可并行拉取的附属数据 */
private data class OptionalLibraryData(
    val screensaverPhotos: List<MediaItem>,
    val people: List<Person>,
    val smartAlbums: List<Album>,
    val stats: com.nasphoto.tv.data.fn.FnOsStats?,
    val approvedMedia: Map<String, List<MediaItem>>,
)

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

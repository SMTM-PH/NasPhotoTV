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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nasphoto.tv.data.AppUpdateController
import com.nasphoto.tv.data.displayPhotos
import com.nasphoto.tv.data.mediaIdentityKey
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.FnOsMediaRepository
import com.nasphoto.tv.data.MediaCache
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.fn.FnOsStats
import com.nasphoto.tv.data.fn.FnOsTransport
import com.nasphoto.tv.data.fn.FnOsTwoFactorChallenge
import com.nasphoto.tv.data.fn.FnOsTwoFactorRequired
import com.nasphoto.tv.data.fn.LanNasDevice
import com.nasphoto.tv.data.model.NasConnection
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.ui.connection.ConnectionMode
import com.nasphoto.tv.ui.connection.ConnectionScreen
import com.nasphoto.tv.ui.connection.NasSetupScreen
import com.nasphoto.tv.ui.library.LibraryScreen
import com.nasphoto.tv.ui.library.folderAlbum
import com.nasphoto.tv.ui.common.NasImageLoader
import com.nasphoto.tv.ui.common.ScreenIdleGuard
import com.nasphoto.tv.ui.screensaver.ScreensaverScreen
import com.nasphoto.tv.ui.settings.SettingsScreen
import com.nasphoto.tv.ui.settings.TextEntryOverlay
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
import kotlinx.coroutines.launch

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
        var setupDraft by remember { mutableStateOf<NasConnection?>(null) }
        var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
        var player by remember { mutableStateOf<PlayerRequest?>(null) }

        val screensaverSettings = SettingsStore.screensaver
        val playbackSettings = SettingsStore.playback
        val updateSettings = SettingsStore.update
        val nas = SettingsStore.nas
        val identityKey = remember(
            nas.host, nas.port, nas.scheme, nas.basePath,
            nas.activeAccountId, nas.username, nas.password, nas.accessCode,
        ) {
            nas.mediaIdentityKey()
        }

        var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var screensaverVisible by remember { mutableStateOf(false) }
        var refreshToken by remember { mutableIntStateOf(0) }
        val uiScope = rememberCoroutineScope()
        // Reset during composition, before effects run: the previous identity is never Ready for a new one.
        var loadState by remember(identityKey) { mutableStateOf<LibraryLoad>(LibraryLoad.Loading) }
        // 账号开启两步验证时，首屏登录只走到「半程」，这里保存等待输入动态码的状态
        var startupTwoFactor by remember(identityKey) {
            mutableStateOf<StartupTwoFactor?>(null)
        }
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
            nas.scheme,
            nas.basePath,
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

        var libraryStats by remember(identityKey) { mutableStateOf<FnOsStats?>(null) }
        var libraryOpened by remember(identityKey) { mutableStateOf(false) }
        val canLoadLibrary = libraryOpened || route == AppRoute.Library
        LaunchedEffect(identityKey, repository, refreshToken, canLoadLibrary) {
            if (!canLoadLibrary) return@LaunchedEffect
            libraryOpened = true
            if (repository == null) {
                MediaCache.clear()
                SettingsStore.updateNas { it.copy(connected = false) }
                loadState = LibraryLoad.NeedsSetup
                return@LaunchedEffect
            }
            if (loadState !is LibraryLoad.Ready) loadState = LibraryLoad.Loading

            fun publish(update: (LibraryState) -> LibraryState) {
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return
                val current = (loadState as? LibraryLoad.Ready)?.library ?: LibraryState(
                    connectionName = nas.name.ifBlank { "Home-NAS" },
                    accountName = nas.activeAccount?.displayName.orEmpty(),
                    accountCount = nas.accounts.size.coerceAtLeast(1),
                    ownerAccountId = nas.activeAccountId,
                    peopleLoaded = false,
                    foldersLoaded = false,
                    timelineLoading = true,
                    albumsLoaded = false,
                    smartAlbumsLoaded = false,
                )
                loadState = LibraryLoad.Ready(update(current))
            }

            try {
                // 首屏只等一页媒体。完整时间线与各分类独立更新。
                var fullResult: List<MediaItem>? = null
                val firstPage = try {
                    repository.getRecent(48)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalStateException) {
                    // 首批数据格式异常时，尝试按完整时间线分段读取。
                    repository.getRecent(FULL_LIBRARY_LIMIT).also { fullResult = it }
                }
                currentCoroutineContext().ensureActive()
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
                SettingsStore.updateNas { it.copy(connected = true) }
                startupTwoFactor = null
                publish { it.copy(
                    recent = if (it.timelineLoading) firstPage else it.recent,
                    isOnline = true,
                    timelineLoading = true,
                    timelineError = null,
                ) }

                coroutineScope {
                    launch {
                        val stats = libraryRequest { repository.stats() }
                        currentCoroutineContext().ensureActive()
                        if (SettingsStore.nas.mediaIdentityKey() == identityKey) libraryStats = stats
                    }
                    launch {
                        val albums = libraryRequest { repository.getAlbums() }
                        currentCoroutineContext().ensureActive()
                        publish { it.copy(albums = albums ?: it.albums, albumsLoaded = true,
                            albumsError = if (albums == null) "相册读取失败 · 可在设置中刷新媒体库" else null) }
                    }
                    launch {
                        val recent = fullResult ?: libraryRequest { repository.getRecent(FULL_LIBRARY_LIMIT) }
                        currentCoroutineContext().ensureActive()
                        publish { it.copy(
                            recent = recent ?: it.recent,
                            timelineLoading = false,
                            timelineError = if (recent == null) "完整图库读取失败 · 可在设置中刷新媒体库" else null,
                        ) }
                    }
                    launch {
                        val people = libraryRequest { repository.getPeople() }.orEmpty()
                        currentCoroutineContext().ensureActive()
                        publish { it.copy(people = people, peopleLoaded = true) }
                    }
                    launch {
                        val smart = libraryRequest { repository.getSmartAlbums() }
                        currentCoroutineContext().ensureActive()
                        publish { it.copy(
                            smartAlbums = smart.orEmpty(),
                            smartAlbumsLoaded = true,
                            smartAlbumsError = if (smart == null) "智能分类暂时无法读取 · 请刷新后重试" else null,
                        ) }
                    }
                    launch {
                        val folders = libraryRequest { repository.getFolders().map(::folderAlbum) }
                        currentCoroutineContext().ensureActive()
                        publish { it.copy(
                            folders = folders.orEmpty(),
                            foldersLoaded = true,
                            foldersError = if (folders == null) "请检查飞牛相册的目录权限和网络连接，在设置中刷新后重试" else null,
                        ) }
                    }
                }
            } catch (e: FnOsTwoFactorRequired) {
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
                // 密码已通过，只差动态码：WebSocket 由仓库里的 FnOsApi 继续持有
                startupTwoFactor = StartupTwoFactor(challenge = e.challenge)
                loadState = LibraryLoad.Loading
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
                SettingsStore.updateNas { it.copy(connected = false) }
                loadState = LibraryLoad.Error(t.message ?: "无法连接 NAS")
            }
        }

        val approvedDisplayIds = SettingsStore.displayAlbumIds
        val currentLibrary = (loadState as? LibraryLoad.Ready)?.library
        val recentCounts = remember(currentLibrary?.recent) {
            val items = currentLibrary?.recent.orEmpty()
            val photos = items.count { it.type == MediaType.PHOTO }
            photos to (items.size - photos)
        }
        LaunchedEffect(identityKey, currentLibrary, libraryStats) {
            val library = currentLibrary ?: return@LaunchedEffect
            if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
            MediaCache.update(
                recent = library.recent,
                albums = library.albums,
                screensaverPhotos = emptyList(),
                photoCount = libraryStats?.photoCount ?: recentCounts.first,
                videoCount = libraryStats?.videoCount ?: recentCounts.second,
                people = library.people,
                ownerAccountId = nas.activeAccountId,
                ownerIdentityKey = identityKey,
            )
        }
        val displayAlbumIds = currentLibrary?.albums?.map { it.id }?.toSet().orEmpty()
        LaunchedEffect(approvedDisplayIds, displayAlbumIds, repository, refreshToken) {
            val repo = repository ?: return@LaunchedEffect
            val fetched = coroutineScope {
                approvedDisplayIds.intersect(displayAlbumIds).map { id ->
                    async { id to libraryRequest { repo.getMedia(id) }.orEmpty() }
                }.awaitAll().toMap()
            }
            currentCoroutineContext().ensureActive()
            if (SettingsStore.nas.mediaIdentityKey() != identityKey) return@LaunchedEffect
            val current = loadState as? LibraryLoad.Ready ?: return@LaunchedEffect
            loadState = current.copy(library = current.library.copy(mediaByAlbum = fetched))
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

        // 任何设置变更立即落盘（含在线更新偏好）
        LaunchedEffect(screensaverSettings, playbackSettings, updateSettings, nas) {
            SettingsStore.persist()
        }

        // 在线更新：启动时静默检查，每天最多一次，失败不提示、不下载、不打断看图。
        // 用户可在「设置 → 关于 → 启动时自动检查」关掉。
        LaunchedEffect(Unit) {
            AppContextHolder.context?.let { ctx ->
                runCatching { AppUpdateController.silentCheck(ctx) }
            }
        }

        // 首次连接页搜到 NAS：先填地址和端口，再进设置补账号密码
        val selectDiscoveredNas: (LanNasDevice) -> Unit = { device ->
            setupDraft = nas.copy(host = device.host, port = device.port, scheme = "https", basePath = "")
            route = AppRoute.Setup
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

                        r is AppRoute.Setup -> NasSetupScreen(
                            connection = setupDraft ?: nas,
                            onBack = { setupDraft = null; route = AppRoute.Library },
                            onConnect = { connection ->
                                SettingsStore.updateNas { connection }
                                setupDraft = null
                                refreshToken += 1
                                route = AppRoute.Library
                            },
                        )

                        r is AppRoute.Settings -> SettingsScreen(
                            store = SettingsStore,
                            onClose = { route = AppRoute.Library },
                            onPreviewScreensaver = enterScreensaver,
                            onOpenLibrary = { route = AppRoute.Library },
                            onRefreshLibrary = { refreshToken += 1; route = AppRoute.Library },
                            modifier = Modifier.fillMaxSize(),
                            screensaverPhotos = saverPhotos,
                        )

                        else -> Box(Modifier.fillMaxSize()) {
                            when (val state = loadState) {
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
                                            if (albumId.startsWith("folder:")) {
                                                repository?.getFolderMedia(albumId.removePrefix("folder:"))
                                            } else {
                                                repository?.getMedia(albumId)
                                            }
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
                                host = nas.displayAddress,
                                onOpenSettings = { route = AppRoute.Settings },
                                onManualSetup = {
                                    setupDraft = nas
                                    route = AppRoute.Setup
                                },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )

                            is LibraryLoad.Loading -> ConnectionScreen(
                                mode = ConnectionMode.Loading,
                                host = nas.displayAddress,
                                onOpenSettings = { route = AppRoute.Settings },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )

                            is LibraryLoad.Error -> ConnectionScreen(
                                mode = ConnectionMode.Error(state.message),
                                host = nas.displayAddress,
                                onOpenSettings = { route = AppRoute.Settings },
                                onRetry = { refreshToken += 1 },
                                onSelectNas = selectDiscoveredNas,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // 两步验证覆盖层：整页 Loading 之上弹数字键盘，不改路由
                        startupTwoFactor?.let { prompt ->
                            // key = 错误信息：验证码错误后重建输入层，用户不用先删掉 6 位旧数字
                            key(prompt.error) {
                                TextEntryOverlay(
                                    title = "两步验证",
                                    subtitle = buildString {
                                        append("该账号已开启两步验证，请输入验证器 App 中的 6 位动态码")
                                        append("；验证成功后本机会被信任")
                                        if (prompt.busy) append(" · 正在验证…")
                                        prompt.error?.let {
                                            append(" · ")
                                            append(it)
                                        }
                                    },
                                    masked = false,
                                    initial = "",
                                    numericOnly = true,
                                    maxLength = 6,
                                    onCancel = {
                                        startupTwoFactor = null
                                        repository?.cancelTwoFactor()
                                        loadState = LibraryLoad.Error("已取消两步验证，可以重新连接")
                                    },
                                    onDone = { code ->
                                        val repo = repository
                                        if (repo != null && !prompt.busy) {
                                            startupTwoFactor = StartupTwoFactor(prompt.challenge, busy = true)
                                            uiScope.launch {
                                                try {
                                                    repo.verifyTwoFactor(code)
                                                    startupTwoFactor = null
                                                    refreshToken += 1
                                                } catch (e: Exception) {
                                                    if (repo.hasPendingTwoFactor()) {
                                                        startupTwoFactor = startupTwoFactor?.copy(
                                                            busy = false,
                                                            error = e.message ?: "验证失败",
                                                        )
                                                    } else {
                                                        // 半登录会话已失效：收起输入层，回到可重试的错误页
                                                        startupTwoFactor = null
                                                        loadState = LibraryLoad.Error(
                                                            e.message ?: "两步验证失败，请重新连接",
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
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

/** 图库必须覆盖完整时间范围，不能按开发机的照片数量截断旧年份。 */
private const val FULL_LIBRARY_LIMIT = Int.MAX_VALUE

private suspend fun <T> libraryRequest(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** 首屏两步验证提示：challenge 决定文案，busy/error 跟随验证请求更新 */
private data class StartupTwoFactor(
    val challenge: FnOsTwoFactorChallenge,
    val busy: Boolean = false,
    val error: String? = null,
)

/** 媒体库的加载状态：所有内容都来自 NAS，没有就绪数据时显示对应引导页 */
private sealed interface LibraryLoad {
    data object NeedsSetup : LibraryLoad
    data object Loading : LibraryLoad
    data class Error(val message: String) : LibraryLoad
    data class Ready(val library: LibraryState) : LibraryLoad
}

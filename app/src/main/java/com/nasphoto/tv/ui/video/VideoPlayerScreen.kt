/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.ui.video

import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nasphoto.tv.BuildConfig
import com.nasphoto.tv.R
import com.nasphoto.tv.data.fn.FnOsTransport
import com.nasphoto.tv.data.model.MediaItem
import com.nasphoto.tv.data.model.MediaType
import com.nasphoto.tv.data.model.PlaybackSettings
import com.nasphoto.tv.data.model.PortraitFit
import com.nasphoto.tv.data.model.isPortraitVideo
import com.nasphoto.tv.ui.common.MediaImage
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay

/**
 * ================= 视频播放器（Media3 + NAS 鉴权串流） =================
 *
 * 遥控器：ENTER 播放/暂停 · ← → 快退/快进 10 秒 · ↑↓ 呼出/隐藏控制条 · BACK 退出。
 * 播放结束按 [PlaybackSettings.autoPlayNext] 自动进入下一条；[PlaybackSettings.loopMode] 单条循环。
 */
@Composable
fun VideoPlayerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    contextTitle: String,
    settings: PlaybackSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只保留视频：调用方传进来的是「当天/当相册」的混排列表（图片 + 视频），
    // 自动连播时下一条很可能是图片。实测这样会把一张 PNG 截图丢给 ExoPlayer，
    // 得到 0:00 / 0:00 的永久黑屏 —— 用户看到的是「播完这个视频就卡死」。
    val videos = remember(items) { items.filter { it.type == MediaType.VIDEO } }
    if (videos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    // 原列表里的下标要映射到过滤后的列表，否则从第 N 张图进入会定位错素材
    val startIndex = remember(items, initialIndex, videos) {
        val target = items.getOrNull(initialIndex)
        val mapped = target?.let { t -> videos.indexOfFirst { it.id == t.id } } ?: -1
        (if (mapped >= 0) mapped else initialIndex).coerceIn(0, videos.lastIndex)
    }

    var index by remember { mutableIntStateOf(startIndex) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    /** 非空表示当前素材播放失败，舞台层需要显示可见的错误提示 */
    var playError by remember { mutableStateOf<String?>(null) }
    /** 当前生效的竖屏适配方式（会话内可切换，默认取设置） */
    var portraitFit by remember { mutableStateOf(settings.portraitFit) }
    /**
     * 手动旋转（默认取设置，播放中 MENU 可临时覆盖当前这一条）。
     * 个别素材的方向元数据写错 / 缺失时，用户自己转 90°/180°/270° 就能看正。
     */
    var manualRotation by remember { mutableStateOf(settings.rotation) }
    /**
     * 解码器回报「尚未应用的旋转」（0/90/180/270）。
     * MediaCodecVideoRenderer 通常会自己把 90/270 折算进宽高并把这里置 0；
     * 少数走其它渲染路径的素材会留下非零值，此时要由 UI 补旋一次，否则画面是躺着的。
     */
    var autoRotationDegrees by remember { mutableIntStateOf(0) }
    /** 最终生效角度 = 手动覆盖 + 解码器未应用的旋转 */
    val effectiveDegrees = (manualRotation.degrees + autoRotationDegrees) % 360
    /**
     * 解码器回报的真实画面尺寸（宽, 高，已含旋转）。
     *
     * NAS 的 width/height 并不可靠：有的素材元数据缺失宽高（实测混排列表里
     * 出现过宽高为 0 的条目），只按 `MediaItem.resolution` 判断会漏掉竖屏视频，
     * 于是它们仍然左右黑边。解码器给出的尺寸是权威值，作为 [isPortraitVideo] 的兜底。
     */
    var decodedSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val context = LocalContext.current
    /**
     * 设备解码能力快照（进入播放器时查一次）。飞牛 API 只给原片、没有转码流可用，
     * 所以播放失败时用这份能力 + 视频规格生成针对性诊断，而不是笼统地说「解不了」。
     */
    val deviceProfile = remember { VideoCapabilityProbe.query(context) }
    val focusRequester = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        controlsInteraction += 1
    }

    val current = videos.getOrNull(index)
    val portrait = isPortraitPlayback(current, decodedSize)

    // 缓冲预算跟着素材码率走：4K 60fps 原片 120–150 Mbps，用固定 48 MB 只能缓冲 3 秒，
    // 播完就要重新等，表现成周期性卡顿。详见 PlaybackBufferPlanner 的注释。
    val bufferPlan = remember(current?.id, current?.sizeBytes, current?.durationMs) {
        PlaybackBufferPlanner.plan(
            PlaybackBufferPlanner.estimateBitrateBps(
                sizeBytes = current?.sizeBytes ?: 0L,
                durationMs = current?.durationMs ?: 0L,
            ),
        )
    }

    // 缓冲参数在 DefaultLoadControl 里是 final，运行时改不了；所以按计划重建播放器。
    // 同一档码率（如连续几条 4K60）算出的计划相同，不会来回重建。
    val player = remember(bufferPlan) {
        // 只开「解码失败自动回退」。注意不要用 forceEnableMediaCodecAsynchronousQueueing()：
        // 本机（MTK Android 9）的 JMediaCodec 在异步回调里会直接 SIGABRT
        // （android_media_MediaCodec.cpp:874 "Should not be here"），实测必崩。
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        bufferPlan.minBufferMs,
                        bufferPlan.maxBufferMs,
                        bufferPlan.bufferForPlaybackMs,
                        bufferPlan.bufferForPlaybackAfterRebufferMs,
                    )
                    .setTargetBufferBytes(bufferPlan.targetBufferBytes)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(FnOsTransport.mediaDataSourceFactory(context)),
            )
            .build()
            .apply {
                repeatMode = if (settings.loopMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Debug 包把解码器与丢帧写进 logcat，方便在真机上判断「卡顿」是网络、
    // 缓冲还是解码能力不足；Release 包不带这条监听。
    DisposableEffect(player) {
        val listener = if (BuildConfig.DEBUG) {
            object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    Log.d(TAG, "decoder=$decoderName init=${initializationDurationMs}ms")
                }

                override fun onDroppedVideoFrames(
                    eventTime: AnalyticsListener.EventTime,
                    droppedFrames: Int,
                    elapsedMs: Long,
                ) {
                    Log.d(TAG, "droppedFrames=$droppedFrames elapsed=${elapsedMs}ms")
                }
            }
        } else {
            null
        }
        listener?.let(player::addAnalyticsListener)
        onDispose { listener?.let(player::removeAnalyticsListener) }
    }

    DisposableEffect(player, index) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    if (settings.autoPlayNext && index < videos.lastIndex) {
                        index += 1
                    } else {
                        isPlaying = false
                        controlsVisible = true
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            /** 解码器给出的真实尺寸（含旋转），用作竖屏判定的兜底 */
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // 解码器没把旋转应用到像素上时由 UI 补旋；宽高也按显示方向换算，
                // 否则竖屏判定与实际画面不一致（用户反馈的「方向时好时坏」）。
                val unapplied = readUnappliedRotation(videoSize)
                autoRotationDegrees = normalizeUnappliedRotation(unapplied)
                decodedSize = displayVideoSize(videoSize.width, videoSize.height, unapplied)
            }

            /**
             * 解码/网络失败时必须给出可见反馈：4K 60fps HEVC 10bit 这类
             * 超出设备解码能力的素材会让 ExoPlayer 抛 MediaCodecVideoRenderer
             * error，若不处理用户只能看到一块永久黑屏。
             */
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val fallback = describePlayerError(error)
                // 解码类失败时，用设备能力 + 视频规格生成针对性诊断（见 VideoCapabilityProbe）
                val assessment = videos.getOrNull(index)?.let {
                    VideoCapabilityProbe.assess(it, deviceProfile)
                }
                playError = VideoCapabilityProbe.describeFailure(
                    errorCode = error.errorCode,
                    fallbackMessage = fallback,
                    assessment = assessment ?: VideoCapabilityProbe.Assessment(false, null),
                )
                buffering = false
                isPlaying = false
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 依赖 player：缓冲计划变化会重建播放器，新实例必须重新装载当前素材
    LaunchedEffect(player, index) {
        val item = videos.getOrNull(index) ?: return@LaunchedEffect
        playError = null
        decodedSize = null
        // 换片必须把上一条的旋转状态清掉，否则「临时转了这一条」会误伤后面所有视频。
        autoRotationDegrees = 0
        manualRotation = settings.rotation
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.url))
        player.prepare()
        player.play()
        buffering = true
        showControls()
    }

    // 只由用户操作重置计时；进度变化不能取消自动隐藏。
    LaunchedEffect(controlsVisible, isPlaying, controlsInteraction) {
        if (controlsVisible && isPlaying) {
            delay(3200)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onClose()
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                    -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showControls()
                        true
                    }

                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        showControls()
                        true
                    }

                    Key.DirectionRight -> {
                        player.seekTo(player.currentPosition + 10_000L)
                        showControls()
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        if (controlsVisible) controlsVisible = false else showControls()
                        true
                    }

                    // 手动旋转：MENU 是电视遥控器上最通用的空闲键，逐档 0→90→180→270。
                    // INFO / 红键只是别名，方便带信息键或彩键的遥控器。
                    Key.Menu, Key.Info, Key.ProgramRed -> {
                        manualRotation = manualRotation.next()
                        showControls()
                        true
                    }

                    // 复位到设置里的默认值：转错了不用连按三下绕回来。
                    Key.ProgramGreen -> {
                        manualRotation = settings.rotation
                        showControls()
                        true
                    }

                    // 竖屏素材在 16:9 电视上只能三选一：左右留黑 / 用封面填两侧 / 裁切铺满。
                    // 彩键黄键（没有彩键的遥控器仍可在「设置 → 视频播放」里改）。
                    Key.ProgramYellow, Key.ZoomIn -> {
                        if (portrait) {
                            portraitFit = when (portraitFit) {
                                PortraitFit.AUTO -> PortraitFit.FILL
                                PortraitFit.FILL -> PortraitFit.FIT
                                PortraitFit.FIT -> PortraitFit.AUTO
                            }
                            showControls()
                        }
                        true
                    }

                    // 遥控器没有独立的「下一个」键时，播放失败后用户会被困在
                    // 同一个视频里；媒体键 NEXT/PREVIOUS 直接切相邻素材。
                    Key.MediaNext, Key.MediaSkipForward -> {
                        if (index < videos.lastIndex) {
                            index += 1
                            showControls()
                        }
                        true
                    }

                    Key.MediaPrevious, Key.MediaSkipBackward -> {
                        if (index > 0) {
                            index -= 1
                            showControls()
                        }
                        true
                    }

                    else -> false
                }
            },
    ) {
        // 竖屏素材：默认用「封面放大 + 压暗」铺满左右留黑，避免 2160×3840 的素材
        // 在 1080p 电视上只占中间 607px、两侧各 656px 死黑带来的「不支持竖屏」观感。
        if (portrait && portraitFit == PortraitFit.AUTO) {
            val cover = current?.thumbnailUrl?.ifBlank { current?.url }.orEmpty()
            if (cover.isNotBlank()) {
                MediaImage(
                    url = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 1920,
                )
                // 压到很暗：只作为画面外的环境色，不跟主体抢注意力
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                )
            }
        }

        // 旋转 90/270 度时，整块画面的外接矩形会转过来：先把 PlayerView 缩到
        // 「转过来仍能完整放进屏幕」的比例，再旋转，既不裁切也不拉伸。
        // 16:9 屏上这个系数是 0.5625 —— 旋转后画面正好占满高度、两侧留黑。
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fitScale = rotationFitScale(maxWidth.value, maxHeight.value, effectiveDegrees)
            AndroidView(
                factory = { ctx ->
                    // TextureView 版 PlayerView：SurfaceView 的内容不受父级 View 旋转影响，
                    // 手动旋转会在部分盒子上「按了没反应」。详见 layout/player_view_rotatable.xml。
                    (LayoutInflater.from(ctx)
                        .inflate(R.layout.player_view_rotatable, null) as PlayerView).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = false
                        isClickable = false
                        this.player = player
                    }
                },
                modifier = Modifier
                    .fillMaxSize(fitScale)
                    .graphicsLayer { rotationZ = effectiveDegrees.toFloat() },
                update = { view ->
                    view.player = player
                    // FILL：按高度铺满并裁掉左右（竖屏素材会切掉上下）；FIT/AUTO：完整画面
                    val mode = if (portrait && portraitFit == PortraitFit.FILL) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    if (view.resizeMode != mode) view.resizeMode = mode
                },
            )
        }

        val err = playError
        if (err != null) {
            // 解码/网络失败：用这段视频自己的封面铺满做底，再叠一张信息卡。
            // 旧版这里是纯黑底 + 一行小字，用户只能对着黑屏猜；封面让「是哪条视频」
            // 一目了然，信息卡给出可操作的原因与下一步。
            val failedItem = videos.getOrNull(index)
            if (failedItem != null && failedItem.thumbnailUrl.isNotBlank()) {
                MediaImage(
                    url = failedItem.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 1920,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f)),
                )
            }
            PlaybackFailureCard(
                title = failedItem?.title.orEmpty(),
                spec = failedItem?.resolution.orEmpty(),
                reason = err,
                canGoNext = index < videos.lastIndex,
                position = index + 1,
                total = videos.size,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = TvColor.Accent,
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                            ),
                        )
                        .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = videos[index].title,
                            fontSize = TvType.Section,
                            fontWeight = FontWeight.SemiBold,
                            color = TvColor.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = contextTitle,
                            fontSize = TvType.Tiny,
                            color = TvColor.TextTertiary,
                        )
                    }
                    Column(
                        modifier = Modifier.padding(end = TvDimen.SpaceM),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (effectiveDegrees != 0) {
                                "已旋转 ${effectiveDegrees}° · MENU 继续 / 绿键复位"
                            } else {
                                "MENU 旋转画面"
                            },
                            fontSize = TvType.Tiny,
                            color = TvColor.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(TvDimen.RadiusS))
                                .background(TvColor.AccentDim)
                                .padding(horizontal = TvDimen.SpaceXs, vertical = 2.dp),
                        )
                        if (portrait) {
                            Text(
                                text = "竖屏 · ${portraitFit.label} · 黄键切换",
                                fontSize = TvType.Tiny,
                                color = TvColor.Accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(TvDimen.RadiusS))
                                    .background(TvColor.AccentDim)
                                    .padding(horizontal = TvDimen.SpaceXs, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "${index + 1} / ${videos.size}",
                        fontSize = TvType.Caption,
                        color = TvColor.TextSecondary,
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceM),
                ) {
                    PlaybackProgress(player = player, isPlaying = isPlaying)
                }
            }
        }
    }
}

/**
 * 播放失败时的信息卡：叠在视频封面之上，给出「哪条视频 / 什么规格 / 为什么 / 怎么办」。
 *
 * 不是诊断工具，是给坐在沙发上的用户看的：一句话原因 + 一个明确的下一步动作。
 */
@Composable
private fun PlaybackFailureCard(
    title: String,
    spec: String,
    reason: String,
    canGoNext: Boolean,
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 900.dp)
            .padding(horizontal = TvDimen.ScreenPadding)
            .clip(RoundedCornerShape(TvDimen.RadiusL))
            .background(TvColor.CinemaPanel)
            .padding(horizontal = TvDimen.SpaceXl, vertical = TvDimen.SpaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = TvColor.Warn,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(TvDimen.SpaceM))
        Text(
            text = "这个视频无法在当前设备播放",
            color = TvColor.TextPrimary,
            fontSize = TvType.Section,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TvDimen.SpaceS))
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = TvColor.TextSecondary,
                fontSize = TvType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (spec.isNotBlank()) {
            Text(
                text = "$spec · 原片直连（飞牛未提供转码流）",
                color = TvColor.TextTertiary,
                fontSize = TvType.Tiny,
            )
        }
        Spacer(Modifier.height(TvDimen.SpaceM))
        Text(
            text = reason,
            color = TvColor.Warn,
            fontSize = TvType.Body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TvDimen.SpaceL))
        Text(
            text = if (canGoNext) {
                "按遥控器「下一曲」跳到下一个（第 $position / $total 个）"
            } else {
                "已经是最后一个（第 $position / $total 个），按 BACK 返回"
            },
            color = TvColor.TextSecondary,
            fontSize = TvType.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

/** 进度只在控制层内更新，隐藏后移出组合，不再使整个播放页面每 400ms 重组。 */
@Composable
private fun PlaybackProgress(player: Player, isPlaying: Boolean) {
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            delay(400)
        }
    }

    Column {
        ProgressTrack(positionMs = positionMs, durationMs = durationMs)
        Spacer(Modifier.height(TvDimen.SpaceS))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TvColor.TextPrimary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(TvDimen.SpaceS))
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                fontSize = TvType.Caption,
                color = TvColor.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "ENTER 播放/暂停 · ←→ 快进 10 秒 · BACK 返回",
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
            )
        }
    }
}

@Composable
private fun ProgressTrack(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(TvColor.Divider, RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(TvColor.Accent, RoundedCornerShape(3.dp)),
        )
    }
}

private const val TAG = "NasPhotoTV.Video"

/**
 * media3 1.5 起该字段标记为 deprecated（正常渲染路径恒为 0）。
 * 仍保留读取：少数渲染路径 / 老版本解码器会留下非零值，正是「方向不一致」的来源。
 */
@Suppress("DEPRECATION")
private fun readUnappliedRotation(videoSize: VideoSize): Int = videoSize.unappliedRotationDegrees

/**
 * 把解码器回报的「未应用旋转」归一化到 0/90/180/270。
 * 非法值（负数、任意角度）一律当 0，避免脏元数据把画面转歪。
 */
internal fun normalizeUnappliedRotation(degrees: Int): Int {
    val r = ((degrees % 360) + 360) % 360
    return if (r == 90 || r == 180 || r == 270) r else 0
}

/**
 * 解码器给的 coded 宽高 + 未应用旋转 → 真正显示方向上的 (宽, 高)。
 * 90/270 度需要交换宽高，否则竖屏判定用的是「躺着」的画面尺寸。
 */
/**
 * 旋转 90/270 度时，PlayerView 要缩到多少才能「转过来仍完整放进屏幕」。
 * 16:9 屏上是 0.5625：旋转后画面正好占满高度、两侧留黑，不裁切也不拉伸。
 */
internal fun rotationFitScale(screenWidth: Float, screenHeight: Float, degrees: Int): Float {
    if (screenWidth <= 0f || screenHeight <= 0f) return 1f
    val r = ((degrees % 360) + 360) % 360
    return if (r == 90 || r == 270) {
        minOf(screenWidth, screenHeight) / maxOf(screenWidth, screenHeight)
    } else {
        1f
    }
}

internal fun displayVideoSize(width: Int, height: Int, unappliedRotationDegrees: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val r = normalizeUnappliedRotation(unappliedRotationDegrees)
    return if (r == 90 || r == 270) height to width else width to height
}

/**
 * 这段素材是否按竖屏处理。
 *
 * **解码器回报的尺寸优先**：它是真正渲染出来的画面，且 media3 已经把
 * `rotationDegrees` 折算进宽高（90/270 度会交换宽高），所以它比 NAS 元数据更权威。
 * 实测有素材在 NAS 里 width/height 为 0，只按元数据判断会漏掉整批竖屏视频，
 * 它们仍然左右黑边 —— 用户看到的依旧是「不支持竖屏」。
 *
 * 解码尺寸还没回报时（起播瞬间）先用 NAS 元数据兜住，避免先按横屏渲染一帧
 * 再跳变。两个来源都没有时按横屏处理：横屏素材占绝大多数，宁可漏判，
 * 也不要把一条普通视频错当成竖屏去做背景填充。
 */
internal fun isPortraitPlayback(item: MediaItem?, decoded: Pair<Int, Int>?): Boolean {
    // 没有条目 = 没有在播任何东西，方向无从谈起
    if (item == null) return false
    if (decoded != null) {
        val (w, h) = decoded
        if (w > 0 && h > 0) return h > w
    }
    return item.isPortraitVideo()
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * 把 ExoPlayer 的 PlaybackException 翻成电视上看得懂的一句话。
 *
 * 原型阶段素材直接来自相机/无人机：4K 60fps HEVC 10bit 这类格式
 * 在电视盒子与模拟器上常被判为 NO_EXCEEDS_CAPABILITIES，必须让用户
 * 知道是「设备解不了」而不是「NAS 挂了」。
 */
private fun describePlayerError(error: androidx.media3.common.PlaybackException): String =
    when (error.errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> "设备解码能力不足（可能是 4K / 10bit / 高帧率素材）"

        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> "这台电视不支持该视频格式"

        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "网络连接失败，检查电视与 NAS 是否在同一局域网"

        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "NAS 上没有找到这个视频文件"

        androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "读取视频数据失败"

        else -> error.errorCodeName
    }

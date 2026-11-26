package com.nasphoto.tv.ui.transfer

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nasphoto.tv.data.PhonePhotoEndpoint
import com.nasphoto.tv.data.PhonePhotoFiles
import com.nasphoto.tv.data.PhoneNasBackupState
import com.nasphoto.tv.data.PhonePhotoNasBackup
import com.nasphoto.tv.data.PhonePhotoUpload
import com.nasphoto.tv.ui.common.NasImageLoader
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PhonePhotoScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    nasBackup: PhonePhotoNasBackup? = null,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var photos by remember(context) { mutableStateOf(emptyList<File>()) }
    var uploadVersion by remember { mutableIntStateOf(0) }
    var endpoint by remember { mutableStateOf<PhonePhotoEndpoint?>(null) }
    var receivingReady by remember { mutableStateOf(false) }
    var viewingIndex by remember { mutableStateOf<Int?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val nasStatus = nasBackup?.state?.collectAsStateWithLifecycle()?.value
    val receiver = remember(context, nasBackup) {
        PhonePhotoUpload(context, nasBackup?.ownerKey) {
            nasBackup?.requestSave()
            scope.launch { uploadVersion++ }
        }
    }
    val backFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val displayFocus = remember { FocusRequester() }

    LaunchedEffect(context, uploadVersion) {
        photos = withContext(Dispatchers.IO) { PhonePhotoFiles.photos(context) }
    }

    fun stopReceiving() {
        receiver.stop()
        endpoint = null
        receivingReady = false
    }

    fun closeOrReturn() {
        when {
            confirmingClear -> if (!clearing) confirmingClear = false
            viewingIndex != null -> viewingIndex = null
            !closing -> {
                closing = true
                stopReceiving()
                onClose()
            }
        }
    }

    val receiving = !closing && viewingIndex == null && !confirmingClear
    DisposableEffect(lifecycle, receiving, receiver) {
        if (!receiving) return@DisposableEffect onDispose { }
        fun startReceiving() {
            if (!receivingReady && !closing && viewingIndex == null && !confirmingClear) {
                endpoint = receiver.start()
                receivingReady = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startReceiving()
                Lifecycle.Event.ON_PAUSE -> stopReceiving()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startReceiving()
        onDispose {
            lifecycle.removeObserver(observer)
            stopReceiving()
        }
    }

    val displaying = viewingIndex != null
    val hasPhotos = photos.isNotEmpty()
    LaunchedEffect(displaying, confirmingClear, hasPhotos) {
        withFrameMillis { }
        val requester = when {
            confirmingClear -> cancelFocus
            displaying -> displayFocus
            hasPhotos -> playFocus
            else -> backFocus
        }
        runCatching { requester.requestFocus() }
    }

    BackHandler { closeOrReturn() }
    val surface = modifier.fillMaxSize().background(TvColor.Bg)
    val index = viewingIndex
    when {
        index != null && hasPhotos -> PhotoDisplay(
            photos = photos,
            index = index.coerceIn(0, photos.lastIndex),
            modifier = surface,
            returnFocus = displayFocus,
            onMove = { delta -> viewingIndex = (index + delta + photos.size) % photos.size },
            onClose = ::closeOrReturn,
        )

        confirmingClear -> Box(surface, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("清空电视上的接收照片？", color = TvColor.TextPrimary, fontSize = TvType.Section)
                Spacer(Modifier.height(TvDimen.SpaceS))
                Text(
                    if ((nasStatus?.pendingCount ?: 0) > 0) {
                        "将移除本机接收的 ${photos.size} 张照片，其中 ${nasStatus?.pendingCount} 张尚待保存到飞牛。"
                    } else {
                        "将移除本机接收的 ${photos.size} 张照片。"
                    },
                    color = TvColor.TextSecondary,
                    fontSize = TvType.Caption,
                )
                Spacer(Modifier.height(TvDimen.SpaceL))
                Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM)) {
                    TransferAction("取消", cancelFocus, onClick = ::closeOrReturn)
                    TransferAction("清空", onClick = {
                        if (!clearing) {
                            clearing = true
                            scope.launch {
                                photos = withContext(Dispatchers.IO) {
                                    PhonePhotoFiles.clear(context)
                                    PhonePhotoFiles.photos(context)
                                }
                                nasBackup?.requestSave()
                                clearing = false
                                confirmingClear = false
                            }
                        }
                    })
                }
            }
        }

        else -> PhotoReception(
            endpoint = endpoint,
            ready = receivingReady,
            photos = photos,
            modifier = surface,
            backFocus = backFocus,
            playFocus = playFocus,
            nasStatus = nasStatus,
            onRetry = { nasBackup?.requestSave() },
            onClose = ::closeOrReturn,
            onDisplay = {
                stopReceiving()
                viewingIndex = 0
            },
            onClear = {
                stopReceiving()
                confirmingClear = true
            },
        )
    }
}

@Composable
private fun PhotoReception(
    endpoint: PhonePhotoEndpoint?,
    ready: Boolean,
    photos: List<File>,
    modifier: Modifier,
    backFocus: FocusRequester,
    playFocus: FocusRequester,
    nasStatus: PhoneNasBackupState?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDisplay: () -> Unit,
    onClear: () -> Unit,
) {
    val shell = RoundedCornerShape(TvDimen.RadiusM)
    Column(
        modifier.padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceL),
        verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceM),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("手机传图", color = TvColor.TextPrimary, fontSize = TvType.Section, fontWeight = FontWeight.SemiBold)
                Text("手机和电视在同一网络时，可扫码选择照片。", color = TvColor.TextSecondary, fontSize = TvType.Caption)
            }
            TransferAction("返回", backFocus, onClick = onClose)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM)) {
            Column(
                Modifier.fillMaxHeight().weight(0.9f).background(TvColor.Stage, shell)
                    .border(1.dp, TvColor.Divider, shell).padding(TvDimen.SpaceM),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val code = remember(endpoint) { endpoint?.let { qrBitmap(it.url) } }
                if (endpoint != null) {
                    if (code != null) {
                        Image(code, "手机上传二维码", Modifier.size(170.dp), filterQuality = FilterQuality.None)
                        Spacer(Modifier.height(TvDimen.SpaceS))
                    }
                    Text("用手机相机扫码", color = TvColor.TextPrimary, fontSize = TvType.Caption)
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(endpoint.url, color = TvColor.TextSecondary, fontSize = TvType.Tiny, fontFamily = FontFamily.Monospace)
                } else {
                    Text(if (ready) "暂时无法接收" else "正在准备上传地址…", color = TvColor.TextPrimary, fontSize = TvType.Body)
                    if (ready) {
                        Spacer(Modifier.height(TvDimen.SpaceXs))
                        Text("请将电视连入局域网，再重新打开手机传图。", color = TvColor.TextSecondary, fontSize = TvType.Caption)
                    }
                }
            }
            Column(
                Modifier.fillMaxHeight().weight(1.3f).background(TvColor.Stage, shell)
                    .border(1.dp, TvColor.Divider, shell).padding(TvDimen.SpaceM),
                verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
            ) {
                Text("本机接收 · ${photos.size} 张", color = TvColor.TextPrimary, fontSize = TvType.Body, fontWeight = FontWeight.Medium)
                if (nasStatus != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val status = when {
                            nasStatus.retryRequired -> "飞牛保存失败 · 待保存 ${nasStatus.pendingCount} 张"
                            nasStatus.saving -> "正在保存到飞牛 · 剩余 ${nasStatus.pendingCount} 张"
                            nasStatus.verificationCount > 0 -> "等待核对飞牛保存 · ${nasStatus.verificationCount} 张"
                            nasStatus.savedCount > 0 -> "已保存到飞牛 · ${nasStatus.savedCount} 张"
                            else -> "接收后自动保存原图到飞牛 NAS"
                        }
                        Text(status, modifier = Modifier.weight(1f),
                            color = if (nasStatus.retryRequired) TvColor.Warn else TvColor.TextSecondary,
                            fontSize = TvType.Caption)
                        if (!nasStatus.saving && (nasStatus.retryRequired || nasStatus.verificationCount > 0)) {
                            TransferAction(if (nasStatus.retryRequired) "重试" else "核对", onClick = onRetry)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clip(shell).background(TvColor.Bg), contentAlignment = Alignment.Center) {
                    val recent = photos.firstOrNull()
                    if (recent != null) {
                        AsyncImage(recent, "最近接收的照片", modifier = Modifier.fillMaxSize(),
                            imageLoader = NasImageLoader.get(LocalContext.current), contentScale = ContentScale.Fit)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(30.dp), tint = TvColor.TextMuted)
                            Spacer(Modifier.height(TvDimen.SpaceXs))
                            Text("等待手机上传照片", color = TvColor.TextSecondary, fontSize = TvType.Caption)
                        }
                    }
                }
                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
                        TransferAction("开始展示", playFocus, onClick = onDisplay)
                        TransferAction("清空照片", onClick = onClear)
                    }
                }
            }
        }
        Text(
            if (nasStatus == null) "照片保存在电视本机。连接飞牛 NAS 后，新上传的照片可自动保存到 NAS。"
            else "电视保留本机展示副本。离开页面后，当前上传链接会失效。",
            color = TvColor.TextSecondary,
            fontSize = TvType.Caption,
        )
    }
}

@Composable
private fun PhotoDisplay(
    photos: List<File>,
    index: Int,
    modifier: Modifier,
    returnFocus: FocusRequester,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier.onPreviewKeyEvent { event ->
            val direction = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown && photos.size > 1) onMove(direction)
            true
        },
    ) {
        Crossfade(photos[index], Modifier.fillMaxSize(), tween(280), label = "phonePhoto") { photo ->
            AsyncImage(photo, "手机照片", modifier = Modifier.fillMaxSize(),
                imageLoader = NasImageLoader.get(LocalContext.current), contentScale = ContentScale.Fit)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TvColor.Bg.copy(alpha = 0.82f))
                .padding(horizontal = TvDimen.ScreenPadding, vertical = TvDimen.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (photos.size > 1) "${index + 1} / ${photos.size}    ·    左右切换" else "1 / 1",
                Modifier.weight(1f), color = TvColor.TextPrimary, fontSize = TvType.Caption,
            )
            TransferAction("返回接收", returnFocus, onClick = onClose)
        }
    }
}

@Composable
private fun TransferAction(label: String, requester: FocusRequester? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TvDimen.RadiusS)
    TvFocusBox(focusRequester = requester, focusedScale = 1.02f, cornerRadiusDp = 10, onConfirm = onClick) { focused ->
        Text(label, color = if (focused) TvColor.TextPrimary else TvColor.TextSecondary, fontSize = TvType.Caption,
            modifier = Modifier.background(if (focused) TvColor.AccentDim else TvColor.SurfaceSoft, shape)
                .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceS))
    }
}

private fun qrBitmap(url: String): ImageBitmap? = runCatching {
    val code = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 256, 256)
    val pixels = IntArray(code.width * code.height) { index ->
        if (code[index % code.width, index / code.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, code.width, code.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

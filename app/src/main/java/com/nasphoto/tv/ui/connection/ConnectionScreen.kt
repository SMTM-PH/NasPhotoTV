package com.nasphoto.tv.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.fn.LanNasDevice
import com.nasphoto.tv.data.fn.LanNasDiscovery
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 媒体库入口的三种非就绪状态 */
sealed interface ConnectionMode {
    data object NeedsSetup : ConnectionMode
    data object Loading : ConnectionMode
    data class Error(val message: String) : ConnectionMode
}

/**
 * 首次使用 / 正在连接 / 连接失败时的入口页。
 * 不展示任何假数据：要么去设置里填账号，要么重试。
 */
@Composable
fun ConnectionScreen(
    mode: ConnectionMode,
    host: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onSelectNas: (LanNasDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryFocus = remember { FocusRequester() }
    val firstDeviceFocus = remember { FocusRequester() }
    var scanNonce by remember { mutableIntStateOf(0) }
    var scan by remember { mutableStateOf<NasScan>(NasScan.Scanning) }
    var userNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        delay(100)
        runCatching { primaryFocus.requestFocus() }
    }

    // 仅首次连接（还没填地址）时自动搜索局域网；进设置页会取消本轮扫描
    LaunchedEffect(mode, scanNonce) {
        if (mode != ConnectionMode.NeedsSetup) return@LaunchedEffect
        userNavigated = false
        scan = NasScan.Scanning
        val devices = try {
            LanNasDiscovery.discover()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        }
        scan = if (devices.isEmpty()) NasScan.None else NasScan.Found(devices)
    }

    // 搜到设备后把焦点放到第一台，用户按确认键就能选；已经动过焦点就不抢
    LaunchedEffect(scan) {
        if (scan !is NasScan.Found) return@LaunchedEffect
        delay(80)
        if (!userNavigated) runCatching { firstDeviceFocus.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(TvColor.Bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(TvColor.Accent.copy(alpha = 0.10f), TvColor.Bg),
                    radius = 1400f,
                ),
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) userNavigated = true
                false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(TvDimen.RadiusXL))
                .background(TvColor.BgElevated)
                .padding(horizontal = 56.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NAS Photo TV",
                fontSize = TvType.Display,
                fontWeight = FontWeight.Bold,
                color = TvColor.TextPrimary,
            )
            Spacer(Modifier.height(TvDimen.SpaceS))

            when (mode) {
                ConnectionMode.Loading -> {
                    Text(
                        text = if (host.isBlank()) "正在连接飞牛 NAS…" else "正在连接 $host…",
                        fontSize = TvType.Body,
                        color = TvColor.TextSecondary,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceL))
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = TvColor.Accent,
                        strokeWidth = 3.dp,
                    )
                }

                ConnectionMode.NeedsSetup -> {
                    Text(
                        text = "还没有连接飞牛 NAS",
                        fontSize = TvType.Section,
                        color = TvColor.TextPrimary,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(
                        text = "保持电视和 NAS 在同一局域网，下面会自动搜索；也可以手动填写地址。",
                        fontSize = TvType.Caption,
                        color = TvColor.TextTertiary,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceM))

                    LanDiscoverySection(
                        scan = scan,
                        firstDeviceFocus = firstDeviceFocus,
                        onSelectNas = onSelectNas,
                    )

                    Spacer(Modifier.height(TvDimen.SpaceL))
                    Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM)) {
                        if (scan !is NasScan.Scanning) {
                            ActionButton(
                                label = "重新搜索",
                                primary = false,
                                onClick = { scanNonce += 1 },
                            )
                        }
                        ActionButton(
                            label = "打开设置",
                            primary = scan is NasScan.None,
                            focusRequester = primaryFocus,
                            onClick = onOpenSettings,
                        )
                    }
                }

                is ConnectionMode.Error -> {
                    Text(
                        text = "连接失败",
                        fontSize = TvType.Section,
                        color = TvColor.Danger,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceXs))
                    Text(
                        text = mode.message,
                        fontSize = TvType.Caption,
                        color = TvColor.TextSecondary,
                    )
                    Text(
                        text = if (host.isBlank()) "" else "NAS：$host",
                        fontSize = TvType.Tiny,
                        color = TvColor.TextTertiary,
                    )
                    Spacer(Modifier.height(TvDimen.SpaceL))
                    Row(horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceM)) {
                        ActionButton(
                            label = "重试",
                            primary = true,
                            focusRequester = primaryFocus,
                            onClick = onRetry,
                        )
                        ActionButton(label = "打开设置", primary = false, onClick = onOpenSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 18,
        onConfirm = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) TvColor.Accent else TvColor.SurfaceHigh)
            .padding(horizontal = 36.dp, vertical = 16.dp),
    ) {
        Text(
            text = label,
            fontSize = TvType.Body,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) TvColor.Bg else TvColor.TextPrimary,
        )
    }
}

/** 首次连接的自动发现状态 */
private sealed interface NasScan {
    data object Scanning : NasScan
    data object None : NasScan
    data class Found(val devices: List<LanNasDevice>) : NasScan
}

@Composable
private fun LanDiscoverySection(
    scan: NasScan,
    firstDeviceFocus: FocusRequester,
    onSelectNas: (LanNasDevice) -> Unit,
) {
    when (scan) {
        NasScan.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = TvColor.Accent,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.width(TvDimen.SpaceS))
            Text(
                text = "正在搜索局域网内的飞牛 NAS…",
                fontSize = TvType.Caption,
                color = TvColor.TextSecondary,
            )
        }

        NasScan.None -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "没有自动发现设备",
                fontSize = TvType.Body,
                color = TvColor.TextSecondary,
            )
            Spacer(Modifier.height(TvDimen.SpaceXs))
            Text(
                text = "确认电视和 NAS 在同一网络，或打开设置手动填写地址。",
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
            )
        }

        is NasScan.Found -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "发现 ${scan.devices.size} 台设备",
                    fontSize = TvType.Caption,
                    color = TvColor.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "选择后自动填入地址",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextTertiary,
                )
            }
            scan.devices.forEachIndexed { index, device ->
                NasDeviceRow(
                    device = device,
                    focusRequester = if (index == 0) firstDeviceFocus else null,
                    onClick = { onSelectNas(device) },
                )
            }
        }
    }
}

@Composable
private fun NasDeviceRow(
    device: LanNasDevice,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    TvFocusBox(
        focusRequester = focusRequester,
        cornerRadiusDp = 14,
        onConfirm = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TvColor.SurfaceHigh)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TvColor.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = TvColor.Accent,
                )
            }
            Spacer(Modifier.width(TvDimen.SpaceM))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (device.fnOsCertificate) "飞牛 NAS" else "局域网设备",
                    fontSize = TvType.CardTitle,
                    fontWeight = FontWeight.Medium,
                    color = TvColor.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${device.host}:${device.port}",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextTertiary,
                )
            }
            Text(
                text = "选择",
                fontSize = TvType.Tiny,
                color = if (focused) TvColor.Accent else TvColor.TextTertiary,
            )
        }
    }
}

package com.nasphoto.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay

/**
 * ================= 电视键盘输入层（密码 / 访问码） =================
 *
 * 为什么不用系统输入法：电视端默认没有软键盘，遥控器也没有字母键。
 * 这里给一个自绘 QWERTY 键盘：
 *  - 方向键在键位间移动，ENTER 输入；BACK 取消；
 *  - 「完成」把值交回调用方；
 *  - 同时接受外接键盘（含 adb `input text`）的可打印字符，方便调试与实体键盘用户。
 *
 * 密码显示为圆点；访问码明文显示（方便核对）。
 */
@Composable
fun TextEntryOverlay(
    title: String,
    subtitle: String,
    masked: Boolean,
    initial: String,
    onCancel: () -> Unit,
    onDone: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial.take(MaxLength)) }
    var caps by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstKey.requestFocus() }
    }

    val append: (String) -> Unit = { text ->
        if (value.length < MaxLength) value += text
    }
    val backspace: () -> Unit = { value = value.dropLast(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onCancel()
                        true
                    }

                    // 外接键盘：退格与可打印字符直接进输入框；方向键 / ENTER 留给焦点系统
                    Key.Backspace -> {
                        backspace()
                        true
                    }

                    else -> {
                        val ch = event.utf16CodePoint.toChar()
                        if (ch.isLetterOrDigit() || ch in AllowedSymbols) {
                            append(ch.toString())
                            true
                        } else {
                            false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(880.dp)
                .clip(RoundedCornerShape(TvDimen.RadiusXL))
                .background(TvColor.BgElevated)
                .border(1.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusXL))
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = TvType.Section,
                    fontWeight = FontWeight.SemiBold,
                    color = TvColor.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (masked) "已输入 ${value.length} 位" else "BACK 取消 · ENTER 输入",
                    fontSize = TvType.Tiny,
                    color = TvColor.TextTertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, fontSize = TvType.Tiny, color = TvColor.TextTertiary)

            Spacer(Modifier.height(TvDimen.SpaceM))

            // 输入框：密码显示圆点，空值时给出引导文案
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(TvDimen.RadiusM))
                    .background(TvColor.Bg)
                    .border(1.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusM))
                    .padding(horizontal = TvDimen.SpaceM),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text("使用下方键盘输入…", fontSize = 20.sp, color = TvColor.TextTertiary)
                } else {
                    Text(
                        text = if (masked) "•".repeat(value.length) else value,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = TvColor.TextPrimary,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(TvDimen.SpaceM))

            KeyboardRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) { append(it) }
            KeyboardRow("qwertyuiop".map { it.toString() }, caps = caps) { append(it) }
            KeyboardRow("asdfghjkl".map { it.toString() } + "@", caps = caps) { append(it) }
            KeyboardRow("zxcvbnm".map { it.toString() } + listOf(".", "_", "-"), caps = caps) { append(it) }

            Spacer(Modifier.height(TvDimen.SpaceXs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeyChip("完成", width = 140.dp, focusRequester = firstKey) { onDone(value) }
                KeyChip("删除", width = 112.dp) { backspace() }
                KeyChip(if (caps) "小写" else "大写", width = 112.dp) { caps = !caps }
                KeyChip("空格", width = 180.dp) { append(" ") }
                KeyChip("清空", width = 112.dp) { value = "" }
            }
        }
    }
}

@Composable
private fun KeyboardRow(chars: List<String>, caps: Boolean = false, onPress: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = TvDimen.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
    ) {
        chars.forEach { ch ->
            val shown = if (caps) ch.uppercase() else ch
            KeyChip(shown) { onPress(shown) }
        }
    }
}

/**
 * 一个键位：可聚焦、ENTER 输入；鼠标 / 触摸可直接点。
 * 用 [pointerInput] 而不是 clickable，避免再叠加一层焦点语义。
 */
@Composable
private fun KeyChip(
    label: String,
    width: Dp = 68.dp,
    focusRequester: FocusRequester? = null,
    onPress: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEventConfirm { onPress() }
            .size(width = width, height = 44.dp)
            .clip(shape)
            .background(if (focused) TvColor.Accent else TvColor.SurfaceHigh)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.92f) else TvColor.Divider,
                shape = shape,
            )
            .pointerInput(label) { detectTapGestures { onPress() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            color = if (focused) TvColor.Bg else TvColor.TextPrimary,
            maxLines = 1,
        )
    }
}

/**
 * ENTER / DPAD_CENTER 触发（KeyUp 不重复触发）。
 * 用 onPreview 只为了赶在焦点系统的「ENTER → 点击」映射之前；方向键返回 false，
 * 照常交给焦点搜索在键位之间移动。
 */
private fun Modifier.onKeyEventConfirm(onConfirm: () -> Unit): Modifier =
    this.onPreviewKeyEvent { event ->
        val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (confirm && event.type == KeyEventType.KeyDown) {
            onConfirm()
            true
        } else {
            false
        }
    }

private const val MaxLength = 64

private val AllowedSymbols = " ._-@#/:".toSet()

@file:Suppress("KDocUnresolvedReference")

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nasphoto.tv.ui.common.ScreenIdleGuard
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * ================= 电视键盘输入层（地址 / 账号 / 密码 / 访问码） =================
 *
 * 两条输入通道并存：
 *  1. 自绘 QWERTY 键盘（兜底）：电视端可能没装任何输入法，遥控器也没字母键。
 *     方向键移动光标，ENTER 输入；BACK 取消；「完成」把值交回调用方。
 *  2. 系统输入法（用户反馈 2）：「系统输入法」键位把焦点交给输入框本体，
 *     唤起 TV 自带 IME —— 特殊字符、联想、剪贴板全由系统处理。
 *     没装 IME 的电视上这个键位只是把焦点移进输入框，不影响自绘键盘。
 *
 * 【为什么不用 Compose 焦点系统做键位导航】（用户反馈 1「键盘概率自动退出」）：
 * 半透明遮罩下面的设置列表也是可聚焦的。此前键位是各自 focusable 的 Compose 节点，
 * 从数字行按 UP、或行首按 LEFT 时，方向搜索会逃出浮层落到底层列表上，
 * 用户下一次按 BACK 就把键盘层关掉——看起来就是「打字打着打着键盘自己退出」。
 * 连 `focusProperties { exit = Cancel }` 都拦不住（实机复测仍逃逸）。
 * 现在改为：整个浮层只有根节点一个焦点，键位高亮由**自管虚拟光标**驱动，
 * 方向键在根节点 preview 里全部消费——焦点物理上不可能离开浮层。
 *
 * 【屏保豁免】IME 打字不产生按键事件，60 秒空闲检测会触发屏保盖掉键盘；
 * 组合期间由 [ScreenIdleGuard] 持续续期（见该类注释）。
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
    /** 纯数字键盘（两步验证动态码）：只画 0-9 / 删除 / 完成，不显示字母与符号 */
    numericOnly: Boolean = false,
    maxLength: Int = MaxLength,
) {
    var value by remember { mutableStateOf(initial.take(maxLength)) }
    var caps by remember { mutableStateOf(false) }
    var showSymbols by remember { mutableStateOf(false) }
    var imeFieldFocused by remember { mutableStateOf(false) }
    var cursorRow by remember { mutableIntStateOf(0) }
    var cursorCol by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val imeField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val append: (String) -> Unit = { text ->
        val cleaned = if (numericOnly) text.filter { it.isDigit() } else text
        if (cleaned.isNotEmpty()) value = (value + cleaned).take(maxLength)
    }
    val backspace: () -> Unit = { value = value.dropLast(1) }

    // 键位网格：符号模式换一组键位；行数据按当前状态重建
    val rows: List<List<KeyDef>> = remember(
        numericOnly, showSymbols, caps, append, backspace, onDone, value, maxLength,
    ) {
        if (numericOnly) {
            listOf(
                listOf("1", "2", "3").map { KeyDef(it, width = 232.dp) { append(it) } },
                listOf("4", "5", "6").map { KeyDef(it, width = 232.dp) { append(it) } },
                listOf("7", "8", "9").map { KeyDef(it, width = 232.dp) { append(it) } },
                listOf(
                    KeyDef("删除", width = 232.dp) { backspace() },
                    KeyDef("0", width = 232.dp) { append("0") },
                    KeyDef("完成", width = 232.dp) { onDone(value) },
                ),
            )
        } else if (showSymbols) {
            listOf(
                listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")").map { KeyDef(it) { append(it) } },
                listOf("[", "]", "{", "}", "-", "_", "=", "+", "\\", "|").map { KeyDef(it) { append(it) } },
                listOf(";", ":", "'", "\"", ",", ".", "/", "?", "~", "`").map { KeyDef(it) { append(it) } },
                listOf(
                    KeyDef("字母") { showSymbols = false },
                    KeyDef("完成") { onDone(value) },
                    KeyDef("删除") { backspace() },
                ),
            )
        } else {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyDef(it) { append(it) } },
                "qwertyuiop".map { ch ->
                    val c = if (caps) ch.uppercase() else ch.toString()
                    KeyDef(c) { append(c) }
                },
                ("asdfghjkl".map { it.toString() } + "@").map {
                    val c = if (caps) it.uppercase() else it
                    KeyDef(c) { append(c) }
                },
                ("zxcvbnm".map { it.toString() } + listOf(".", "_", "-")).map {
                    val c = if (caps) it.uppercase() else it
                    KeyDef(c) { append(c) }
                },
                listOf(
                    KeyDef("完成") { onDone(value) },
                    KeyDef("删除") { backspace() },
                    KeyDef(if (caps) "小写" else "大写") { caps = !caps },
                    KeyDef("空格", width = 140.dp) { append(" ") },
                    KeyDef("清空") { value = "" },
                ),
            )
        }
    }
    // 底部第二行（符号开关 / 系统输入法）独立于网格之外，单独一排也纳入光标导航。
    // 纯数字模式没有字母/符号，也不需要系统输入法。
    val bottomRow = remember(numericOnly, showSymbols, value) {
        if (numericOnly) {
            emptyList()
        } else {
            listOf(
                KeyDef(if (showSymbols) "字母" else "符号", width = 112.dp) { showSymbols = !showSymbols },
                KeyDef("系统输入法", width = 180.dp) {
                    // 焦点交给输入框本体 → 系统弹 IME；没装 IME 的电视上无副作用
                    runCatching { imeField.requestFocus() }
                },
            )
        }
    }
    val allRows: List<List<KeyDef>> = if (bottomRow.isEmpty()) rows else rows + listOf(bottomRow)

    // 键位行数/列数随符号模式变化，光标越界时收紧
    LaunchedEffect(allRows.size) {
        if (cursorRow > allRows.lastIndex) cursorRow = allRows.lastIndex
        if (cursorCol > (allRows.getOrNull(cursorRow)?.lastIndex ?: 0)) {
            cursorCol = allRows[cursorRow].lastIndex
        }
    }

    // 初始焦点要重试：浮层刚进组合时还没挂上布局树，首次 requestFocus 会抛
    LaunchedEffect(Unit) {
        repeat(4) {
            if (runCatching { rootFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }

    // 屏保豁免：组合期间每 10 秒续期 30 秒，销毁后最迟 30 秒自动失效
    LaunchedEffect(Unit) {
        while (isActive) {
            ScreenIdleGuard.hold(30)
            delay(10_000)
        }
    }
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    fun moveCursor(dr: Int, dc: Int) {
        val r = (cursorRow + dr).coerceIn(0, allRows.lastIndex)
        cursorRow = r
        cursorCol = cursorCol.coerceIn(0, allRows[r].lastIndex) + dc
        cursorCol = cursorCol.coerceIn(0, allRows[r].lastIndex)
    }

    // 必须用 Dialog 承载，而不是同层 Box：
    // 下层页面（设置列表 / 媒体库导航）自己的 focusRequester 会在组合后抢回焦点，
    // 遥控器按键就跑到遮罩底下的页面去了。Dialog 是独立窗口，底层收不到按键事件。
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                // 根节点是浮层内唯一焦点：方向键在这里全部消费，永远不会交给
                // 遮罩底下的页面 —— 焦点逃逸在机制上被消除
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Back || event.key == Key.Escape ->
                            if (imeFieldFocused) {
                                // 第一层 BACK：退出系统输入法模式（顺带收起 IME），不关浮层
                                keyboard?.hide()
                                runCatching { rootFocus.requestFocus() }
                                true
                            } else {
                                onCancel()
                                true
                            }

                        imeFieldFocused -> when (event.key) {
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                            -> {
                                // 任意导航键 = 回到自绘键盘光标模式
                                keyboard?.hide()
                                runCatching { rootFocus.requestFocus() }
                                true
                            }

                            // 可打印字符 / 退格：放行给输入框（root 是 preview 隧道的上层，
                            // 返回 false 才会继续传到 BasicTextField）
                            else -> false
                        }

                        else -> when (event.key) {
                            Key.DirectionUp -> {
                                moveCursor(-1, 0); true
                            }

                            Key.DirectionDown -> {
                                moveCursor(1, 0); true
                            }

                            Key.DirectionLeft -> {
                                moveCursor(0, -1); true
                            }

                            Key.DirectionRight -> {
                                moveCursor(0, 1); true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                allRows.getOrNull(cursorRow)?.getOrNull(cursorCol)?.press?.invoke()
                                true
                            }

                            Key.Backspace -> {
                                backspace()
                                true
                            }

                            else -> {
                                // 外接键盘 / adb input text：可打印字符直接入值
                                val ch = event.utf16CodePoint.toChar()
                                val printable = ch.isLetterOrDigit() || ch in AllowedSymbols
                                if (printable) {
                                    append(ch.toString())
                                    true
                                } else {
                                    false
                                }
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
                        text = when {
                            numericOnly -> "${value.length}/$maxLength 位"
                            masked -> "已输入 ${value.length} 位"
                            else -> "BACK 取消 · ENTER 输入"
                        },
                        fontSize = TvType.Tiny,
                        color = TvColor.TextTertiary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(text = subtitle, fontSize = TvType.Tiny, color = TvColor.TextTertiary)

                Spacer(Modifier.height(TvDimen.SpaceM))

                // 输入框本体：自绘模式显示值；「系统输入法」模式变成真文本框接 IME
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(TvDimen.RadiusM))
                        .background(if (imeFieldFocused) TvColor.Accent.copy(alpha = 0.10f) else TvColor.Bg)
                        .border(
                            width = if (imeFieldFocused) 2.dp else 1.dp,
                            color = if (imeFieldFocused) TvColor.Accent else TvColor.Divider,
                            shape = RoundedCornerShape(TvDimen.RadiusM),
                        )
                        .padding(horizontal = TvDimen.SpaceM),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 文本框常驻组合：「系统输入法」键位要先把焦点交给它，它必须先存在；
                    // 未激活时文字透明、由上面的显示层盖住，视觉上仍是普通展示框
                    BasicTextField(
                        value = value,
                        onValueChange = { raw ->
                            value = raw.take(maxLength).filter { c ->
                                c != '\n' && (!numericOnly || c.isDigit())
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 20.sp,
                            color = if (imeFieldFocused) TvColor.TextPrimary else Color.Transparent,
                        ),
                        cursorBrush = SolidColor(TvColor.Accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when {
                                numericOnly -> KeyboardType.Number
                                masked -> KeyboardType.Password
                                else -> KeyboardType.Text
                            },
                        ),
                        visualTransformation = if (masked) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(imeField)
                            .onFocusChanged {
                                imeFieldFocused = it.isFocused
                                if (it.isFocused) keyboard?.show()
                            },
                    )
                    if (!imeFieldFocused) {
                        when {
                            value.isEmpty() -> {
                                Text(
                                    if (numericOnly) {
                                        "输入 6 位动态验证码 · 用遥控器 ↑↓←→ 选数字，ENTER 确认"
                                    } else {
                                        "按下方键位输入 · 「系统输入法」键唤起系统键盘"
                                    },
                                    fontSize = 19.sp,
                                    color = TvColor.TextTertiary,
                                )
                            }

                            else -> {
                                Text(
                                    text = if (masked) "•".repeat(value.length) else value,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TvColor.TextPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(TvDimen.SpaceM))

                rows.forEachIndexed { r, defs ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = TvDimen.SpaceXs),
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
                    ) {
                        defs.forEachIndexed { c, key ->
                            VirtualKeyChip(
                                label = key.label,
                                width = key.width,
                                selected = r == cursorRow && c == cursorCol,
                                onPress = key.press,
                                onSelect = {
                                    cursorRow = r
                                    cursorCol = c
                                },
                            )
                        }
                    }
                }

                if (bottomRow.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(TvDimen.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        bottomRow.forEachIndexed { c, key ->
                            VirtualKeyChip(
                                label = key.label,
                                width = key.width,
                                selected = allRows.lastIndex == cursorRow && c == cursorCol,
                                onPress = key.press,
                                onSelect = {
                                    cursorRow = allRows.lastIndex
                                    cursorCol = c
                                },
                            )
                        }
                        if (imeFieldFocused) {
                            Text(
                                text = "系统输入法输入中 · 任意方向键 / BACK 返回键位模式",
                                fontSize = TvType.Tiny,
                                color = TvColor.TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自绘键盘的键位：**不可聚焦**（焦点在浮层根节点上），高亮由虚拟光标驱动；
 * 鼠标 / 触摸点击 = 选中并触发。
 */
@Composable
private fun VirtualKeyChip(
    label: String,
    width: Dp,
    selected: Boolean,
    onPress: () -> Unit,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = 44.dp)
            .clip(shape)
            .background(if (selected) TvColor.Accent else TvColor.SurfaceHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.92f) else TvColor.Divider,
                shape = shape,
            )
            .pointerInput(label) {
                detectTapGestures {
                    onSelect()
                    onPress()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) TvColor.Bg else TvColor.TextPrimary,
            maxLines = 1,
        )
    }
}

private const val MaxLength = 64

/** 外接键盘 / adb input text 直接放行的符号集：覆盖 ASCII 可打印符号全集 */
private val AllowedSymbols = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toSet()

/** 键位定义：等宽字符键缺省 68dp，动作键单独给宽度 */
private data class KeyDef(val label: String, val width: Dp = 68.dp, val press: () -> Unit)

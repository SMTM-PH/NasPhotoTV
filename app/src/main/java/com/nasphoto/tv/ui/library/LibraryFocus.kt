package com.nasphoto.tv.ui.library

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen

/**
 * ================= 媒体库页焦点/按键工具（library-dev 维护） =================
 *
 * 没有直接复用 ui/common/FocusCard.kt：那里的 focusRequester 只在 initialFocus=true 时挂载，
 * 而这里的行/网格需要「任意索引都能被程序化聚焦」。视觉行为与公共规范一致：
 * 放大 TvDimen.FocusScale + 白色 3dp 描边 + 青色辉光阴影。
 */

/** 区块 id：顶栏 Tab / 主内容。Tab 下移时通过区块请求让页面决定落点 */
const val BLOCK_TOP = 0
const val BLOCK_CONTENT = 1

/**
 * 焦点落点请求：
 *  - 精确落点 `request(requester)`：跨行时直接打到"同列"的卡片
 *  - 区块落点 `requestBlock(BLOCK_CONTENT, requester)`：由持有该区块的页面决定第一落点
 * 带序号、只消费一次，避免重组时反复抢焦点。
 */
class PendingFocus {
    var token: Int by mutableStateOf(0)
        private set
    var direct: FocusRequester? by mutableStateOf(null)
        private set
    var block: Pair<Int, FocusRequester?>? by mutableStateOf(null)
        private set

    fun request(requester: FocusRequester?) {
        direct = requester
        block = null
        token += 1
    }

    /** 让持有 [blockId] 的页面自己决定落点（不需要精确 requester 时用） */
    fun requestBlock(blockId: Int) {
        direct = null
        block = blockId to null
        token += 1
    }

    /** 同时指定区块与精确落点（跨区块但已知目标卡片） */
    fun requestBlock(blockId: Int, requester: FocusRequester?) {
        direct = null
        block = blockId to requester
        token += 1
    }

    fun consume(which: Int) {
        if (token == which) {
            direct = null
            block = null
        }
    }
}

/**
 * 消费焦点请求（每个页面只调一次，避免两个 LaunchedEffect 抢同一个 token）：
 *  - `request(requester)`：精确落到某张卡；
 *  - `requestBlock(BLOCK_CONTENT)`：落到本区块的第一落点（由 [blockFallback] 给出，必须是 lambda：
 *    FocusRequester 在组合期才创建，且空列表时返回 null 表示"没有可聚焦项"）。
 * 其它区块 id 的请求直接丢弃（例如首页内容的请求落到 Tab 页上）。
 */
@Composable
fun ConsumeBlockRequest(pending: PendingFocus, blockId: Int, blockFallback: () -> FocusRequester?) {
    val which = pending.token
    LaunchedEffect(which) {
        if (which == 0) return@LaunchedEffect
        val direct = pending.direct
        val block = pending.block
        val target = when {
            direct != null -> direct
            block != null && block.first == blockId -> block.second ?: blockFallback()
            else -> null
        }
        target?.let { runCatching { it.requestFocus() } }
        pending.consume(which)
    }
}

/**
 * 电视端可聚焦卡片：聚焦时放大 + 白色描边 + 青色辉光阴影。
 * 父容器必须留出放大溢出空间（1.08 → 每边多 4%），否则缩放会被裁剪。
 *
 * @param onConfirm ENTER/DPAD_CENTER 回调；传 null 表示调用方自己处理按键
 * @param onUp/onDown 显式上下导航（contract 5：跨行）。null = 这一侧没有目标，放行给 Compose 做空间查找。
 *        内部挂在 [Modifier.confirmOnKey] 的**同一个** key 监听里，保证 ENTER 与方向键只有一个处理者。
 */
@Composable
fun TvFocusBox(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    cornerRadiusDp: Int = 16,
    focusedScale: Float = TvDimen.FocusScale,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    onConfirm: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(160),
        label = "tvFocusScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180),
        label = "tvFocusGlow",
    )
    val shape: Shape = RoundedCornerShape(cornerRadiusDp.dp)
    val interaction = remember { MutableInteractionSource() }

    // 描边画在自身图层上（不是子节点）：不参与测量，不会被 caller 的 padding 裁进内容里。
    // 顺序：graphicsLayer 在外，缩放时描边跟着一起放大。
    val boxMod: Modifier = Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (glow > 0f) {
                drawRoundRect(
                    color = TvColor.FocusRing.copy(alpha = glow),
                    cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                    style = Stroke(width = TvDimen.FocusRingWidth.toPx()),
                )
            }
        }
        .then(
            if (focused) {
                Modifier.shadow(
                    TvDimen.FocusElevation,
                    shape,
                    ambientColor = TvColor.FocusRingGlow,
                    spotColor = TvColor.FocusRingGlow,
                )
            } else {
                Modifier
            },
        )
        .then(modifier)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged {
            if (focused != it.isFocused) {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
        }
        .focusable(enabled = enabled, interactionSource = interaction)
        .tvKeys(onConfirm = onConfirm, onUp = onUp, onDown = onDown, enabled = enabled)
        .then(if (onConfirm != null) Modifier.onClickFallback(onConfirm) else Modifier)

    Box(boxMod) {
        content(focused)
    }
}

/**
 * 横向行自动滚动（契约 5：聚焦项必须可见）。
 * 非 @Composable 的挂起函数，在页面的 LaunchedEffect(focusedIndex) 里调用；
 * focus 回调里只记录 index（不滚动），由 LaunchedEffect 在布局后执行。
 * 规则：滚到 (索引-1)，左对齐且右侧露出一张，做"还有更多"的预告。
 */
suspend fun scrollRowTo(state: LazyListState, focusedIndex: Int) {
    if (focusedIndex < 0) return
    if (state.layoutInfo.viewportSize.width <= 0) return
    if (state.firstVisibleItemIndex == (focusedIndex - 1).coerceAtLeast(0) && state.firstVisibleItemScrollOffset == 0) return
    runCatching { state.animateScrollToItem((focusedIndex - 1).coerceAtLeast(0)) }
}

/**
 * 网格自动滚动：只在聚焦行"完全不可见"时滚动（滚到上一行做参照），
 * 已在可视区时不动，避免上下移动时画面跳动。
 */
suspend fun scrollGridTo(state: LazyGridState, focusedIndex: Int, columns: Int) {
    if (focusedIndex < 0) return
    val vp = state.layoutInfo.viewportSize.height
    if (vp <= 0) return
    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusedIndex } ?: return
    val fullyVisible = info.offset.y >= 0 && info.offset.y + info.size.height <= vp
    if (fullyVisible) return
    val row = focusedIndex / columns.coerceAtLeast(1)
    runCatching { state.animateScrollToItem(((row - 1).coerceAtLeast(0)) * columns) }
}

/** 网格内容边距：四周都留出焦点放大空间，边缘卡片不会被裁 */
val GridContentPadding = PaddingValues(
    start = TvDimen.ScreenPadding,
    end = TvDimen.ScreenPadding,
    top = 16.dp,
    bottom = 32.dp,
)

/** 横向行内容边距：左右对齐屏幕 padding，上下留出放大空间 */
val RowPadding = PaddingValues(
    start = TvDimen.ScreenPadding,
    end = TvDimen.ScreenPadding,
    top = 14.dp,
    bottom = 14.dp,
)

/** 整页背景：深空黑 + 顶部极淡的星蓝辉光，避免大片死黑 */
@Composable
fun LibraryBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(TvColor.Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(TvColor.AccentAlt.copy(alpha = 0.10f), TvColor.Bg.copy(alpha = 0f)),
                        center = Offset(size.width * 0.18f, -size.height * 0.10f),
                        radius = size.width * 0.85f,
                    ),
                )
            },
    )
}

/**
 * ================= 按键处理的唯一入口（务必阅读，别再往 preview 里放锁焦逻辑） =================
 *
 * 【为什么不用 onPreviewKeyEvent】Compose 1.7（foundation）的焦点系统在 `focusable` 节点上装了
 * KeyInputModifierNode，并且按 **capability 固定顺序** 执行同一节点上的几个 key 处理器：
 *   1) onPreviewKeyEvent（本节点）
 *   2) 「OnClick 映射」（StandardKeyMapping：未消费的 DirectionCenter/Enter 的 KeyUp → OnClick，被消费 ⇒ 返回“已处理”）
 *   3) onKeyEvent（本节点）
 *   4) 子节点递归
 *   5) 1D 方向移动
 * 也就是说：本节点 preview 返回 **false** 时，第 2 步会先吃掉 KeyUp；preview 返回 **true** 时，
 * 它会绕过同节点的 onKeyEvent 与「向上/向下」空间移动逻辑，同时挡住所有祖先的 key 处理。
 * 实测后果（1920x1080 模拟器）：
 *   - Hero 上按 UP 永远不动（preview 只映射了 DOWN，UP 放行 → KeyUp 被 OnClick 吃掉 → 左上没有节点）；
 *   - 顶栏 Tab/图标按钮按 ENTER 会触发两次导航（preview 回调一次 + 祖先收到映射后的 OnClick 再回调一次），
 *     一次 ENTER 先跳设置、再被外层重置回媒体库 → “ENTER 无效”；
 *   - 混用 preview + onKeyEvent 时两个处理器对同一次按键各自判定，出现焦点两个节点之间来回闪。
 *
 * 【现在的约定】所有按键只用 `onKeyEvent`（internal 回调，不会被 OnClick 映射抢走）：
 *   - ENTER：只在 KeyDown 触发一次，KeyDown/KeyUp/重复键全部消费 → 一定能到达本节点，
 *     且不会再冒泡到祖先（不会二次触发、也不会一次 ENTER 打开两页）；
 *   - UP/DOWN：有显式目标就执行并消费；没有目标就放行，交给 Compose 做空间查找（跨行同列）；
 *   - LEFT/RIGHT 永远放行，由 Lazy 行/网格或 Compose 焦点系统处理。
 */
@Composable
private fun Modifier.tvKeys(
    onConfirm: (() -> Unit)?,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    enabled: Boolean,
): Modifier {
    if (!enabled || (onConfirm == null && onUp == null && onDown == null)) return this
    // 回调放进 State 并逐帧更新：动画帧期间旧节点也不会调用到过期的 lambda
    val confirm by rememberUpdatedState(onConfirm)
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    return this.onKeyEvent { e ->
        if (e.isConfirmKey()) {
            if (e.type == KeyEventType.KeyDown) confirm?.invoke()
            true
        } else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && up != null) {
            up?.invoke()
            true
        } else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && down != null) {
            down?.invoke()
            true
        } else {
            false
        }
    }
}

/**
 * 兜底：Compose StandardKeyMapping 仍可能把确认键映射成 OnClick（鼠标/无障碍/手势触发）。
 * 这里只在「没有对应 key 事件」时生效，避免同一次按键触发两遍。
 *
 * 注意：必须挂在本节点的 [Modifier.tvKeys] **之后**。同一节点上的处理器顺序是
 * onKeyEvent → 子节点 → onClick（见 FocusOwnerImpl），所以 key 路径先执行；两者时间戳相同 ⇒ 判定为已处理。
 */
@Composable
private fun Modifier.onClickFallback(onConfirm: () -> Unit): Modifier {
    var lastKeyMs by remember { mutableLongStateOf(0L) }
    val latest by rememberUpdatedState(onConfirm)
    val now = { SystemClock.uptimeMillis() }
    return this
        .onKeyEvent { e ->
            if (e.isConfirmKey()) lastKeyMs = now()
            false   // 不消费，交给前面的 tvKeys 逻辑处理
        }
        .onClick {
            if (now() - lastKeyMs > OnClickDedupeWindowMs) latest()
        }
}

private const val OnClickDedupeWindowMs = 80L

/**
 * 装饰性浮层「不参与指针处理」标记。
 *
 * Hero 的压暗渐变 / 卡片底部的聚焦标题层都盖在图片之上，它们只负责画，不该抢走指针事件：
 * 一旦某个子节点成为指针目标，父级 [TvFocusBox] 的点击兜底就可能收不到事件。
 * 这里只登记一个**空的** pointerInput 节点（不注册手势、不消费事件），
 * 明确声明"这棵子树对指针透明"，让事件照常冒泡回父级。
 */
fun Modifier.blockPointer(): Modifier = this.pointerInput(Unit) { /* 有意为空：只声明不处理 */ }

/**
 * 点击兜底：把 Compose 标准的「确认键 → OnClick」映射接到 [onClickFallback] 的去重逻辑上。
 *
 * 用 [clickable] 而不是自造手势，是为了保留无障碍语义与鼠标/触摸路径；
 * 关掉水波纹与高亮（indication = null）—— 电视端焦点态由 [TvFocusBox] 的描边 + 缩放表达，
 * 再叠一层 ripple 会在按键时闪出多余的白色方块。
 */
@Composable
private fun Modifier.onClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/** 显式上下键（保留给不经过 [TvFocusBox] 的自定义节点；推荐直接用 TvFocusBox(onUp=, onDown=)） */
fun Modifier.verticalNav(onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null): Modifier = this.onKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown) {
        when (e.key) {
            Key.DirectionUp -> if (onUp != null) {
                onUp(); true
            } else false

            Key.DirectionDown -> if (onDown != null) {
                onDown(); true
            } else false

            else -> false
        }
    } else {
        false
    }
}

internal fun androidx.compose.ui.input.key.KeyEvent.isConfirmKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

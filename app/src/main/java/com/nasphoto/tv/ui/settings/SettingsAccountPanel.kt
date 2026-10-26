package com.nasphoto.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.model.NasAccount
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

/**
 * 右栏 · 账号切换。
 *
 * 用户反馈的原始诉求是「相册能多个用户登录就好了，能在设置里面可以切换用户」。
 * 这里的取舍：
 *  - 同一台 NAS 共用 host/port（左栏「地址」「端口」只有一份），账号只带凭据；
 *  - 每个账号各存一套 username/password/accessCode，切换时由外壳重建仓库并重登，
 *    所以切过去不会串号；
 *  - 「当前账号」这一行在左栏按 ← → 就能循环切换，不用进这个面板 —— 面板负责
 *    「看清有哪几个账号、各自填了什么、增删改」。
 */
/** 右栏最多列出几个账号卡片；再多折成「+N 个账号」，保证一屏看得全 */
private const val ACCOUNT_LIST_MAX = 4

@Composable
fun SettingsAccountPanel(store: SettingsStore, modifier: Modifier = Modifier) {
    val nas = store.nas
    val accounts = nas.accounts

    // 根节点吃满右栏高度：下面账号列表用 weight 拿剩余高度，说明文字固定在底部，
    // 这样无论 1 个账号还是 5 个账号都不会把卡片挤扁（右栏是本页唯一能滚动的区域）。
    Column(modifier.fillMaxSize()) {
        PanelTitle(
            "账号切换",
            if (accounts.size > 1) {
                "这台 NAS 上保存了 ${accounts.size} 个账号 · 左栏「当前账号」按 ← → 直接切换"
            } else {
                "同一台 NAS 可以登录多个飞牛账号，各自看到各自的照片"
            },
        )

        Spacer(Modifier.height(TvDimen.SpaceM))

        if (accounts.isEmpty()) {
            EmptyAccountsHint()
        } else {
            // 【这里踩过两次坑，别再"优化"回去】
            // 1) 用普通 Column 且不约束：卡片高度按内容抢空间，实测 2 个账号时第 2 张被压成一条。
            // 2) 改成 weight(1f) + verticalScroll：卡片高度对了，但账号卡片是不可聚焦的展示元素，
            //    遥控器只有方向键/ENTER —— 没有任何办法滚这个列表，第 2 个账号永远看不到。
            // 现在：卡片做紧凑（[AccountRow] 内 avatar 40dp、上下 padding 收紧），
            //      按剩余高度决定显示几条，放不下的折成「+N 个账号」一行。
            //      TV 上"看得见 + 一眼数得清"比"能滚动"重要，滚不动等于藏起来。
            Column(verticalArrangement = Arrangement.spacedBy(TvDimen.SpaceS)) {
                val visible = accounts.take(ACCOUNT_LIST_MAX)
                visible.forEach { account ->
                    AccountRow(
                        account = account,
                        active = account.id == (nas.activeAccount?.id ?: ""),
                        canRemove = accounts.size > 1,
                    )
                }
                val rest = accounts.size - visible.size
                if (rest > 0) {
                    Text(
                        text = "另有 $rest 个账号未展开 · 按左栏「当前账号」← → 逐个切换",
                        fontSize = TvType.Tiny,
                        color = TvColor.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = TvDimen.SpaceS),
                    )
                }
            }
        }

        Spacer(Modifier.height(TvDimen.SpaceM))

        // 切换后的行为说明 —— 用户最担心的就是"切了之后照片是不是别人的"
        Text(
            text = "切换账号后会重新登录并刷新媒体库；相册缓存与图片缓存会一起清空，避免把上一个账号的照片显示给下一个人。",
            fontSize = TvType.Tiny,
            color = TvColor.TextTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一个账号一行：首字头像 + 备注名/用户名 + 状态（使用中 / 凭据不全 / 可切换） */
@Composable
private fun AccountRow(account: NasAccount, active: Boolean, canRemove: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (active) TvColor.Accent.copy(alpha = 0.55f) else TvColor.Divider,
        animationSpec = tween(180),
        label = "accountBorder",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TvDimen.RadiusL))
            .background(if (active) TvColor.Accent.copy(alpha = 0.08f) else TvColor.BgElevated)
            .border(1.5.dp, borderColor, RoundedCornerShape(TvDimen.RadiusL))
            .padding(horizontal = TvDimen.SpaceM, vertical = TvDimen.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initial = account.initial, active = active)

        Spacer(Modifier.width(TvDimen.SpaceM))

        Column(Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                fontSize = TvType.CardTitle,
                fontWeight = FontWeight.Medium,
                color = TvColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(if (account.label.isNotBlank()) account.username.ifBlank { "未填用户名" } else "飞牛账号")
                    if (account.accessCode.isNotBlank()) append(" · 已设访问码")
                },
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(TvDimen.SpaceS))

        when {
            active -> StatusCapsule(text = "使用中", color = TvColor.Accent)
            !account.isConfigured -> StatusCapsule(text = "待填凭据", color = TvColor.Warn)
            else -> StatusCapsule(
                text = if (canRemove) "可切换" else "备用",
                color = TvColor.TextSecondary,
            )
        }
    }
}

@Composable
private fun Avatar(initial: String, active: Boolean) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (active) TvColor.Accent.copy(alpha = 0.18f) else TvColor.SurfaceHigh.copy(alpha = 0.55f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = TvType.CardTitle,
            fontWeight = FontWeight.SemiBold,
            color = if (active) TvColor.Accent else TvColor.TextSecondary,
        )
    }
}

@Composable
private fun StatusCapsule(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = TvDimen.SpaceS, vertical = 4.dp),
    ) {
        Text(text = text, fontSize = TvType.Tiny, fontWeight = FontWeight.Medium, color = color)
    }
}

/** 还没保存任何账号时的引导（第一次开机就是这个状态） */
@Composable
private fun EmptyAccountsHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TvDimen.RadiusL))
            .background(TvColor.BgElevated)
            .border(1.5.dp, TvColor.Divider, RoundedCornerShape(TvDimen.RadiusL))
            .padding(TvDimen.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(TvColor.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PersonAdd, null, Modifier.size(24.dp), tint = TvColor.Accent)
        }
        Spacer(Modifier.width(TvDimen.SpaceM))
        Column(Modifier.weight(1f)) {
            Text(
                text = "还没有保存账号",
                fontSize = TvType.CardTitle,
                fontWeight = FontWeight.Medium,
                color = TvColor.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "在上面填「账号」「密码」，或在左栏选「添加账号」。填完按「测试连接」验证。",
                fontSize = TvType.Tiny,
                color = TvColor.TextTertiary,
            )
        }
    }
}

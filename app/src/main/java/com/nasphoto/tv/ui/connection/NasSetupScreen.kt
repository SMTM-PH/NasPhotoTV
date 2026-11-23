package com.nasphoto.tv.ui.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasphoto.tv.data.fn.NasAddressParser
import com.nasphoto.tv.data.model.NasConnection
import com.nasphoto.tv.ui.library.TvFocusBox
import com.nasphoto.tv.ui.settings.TextEntryOverlay
import com.nasphoto.tv.ui.theme.TvColor
import com.nasphoto.tv.ui.theme.TvDimen
import com.nasphoto.tv.ui.theme.TvType

private enum class SetupField(val title: String, val hint: String, val masked: Boolean = false) {
    ADDRESS("NAS 地址", "IP、域名或完整 URL，可包含端口和子路径"),
    ACCOUNT("账号", "飞牛相册账号"),
    PASSWORD("密码", "飞牛相册密码", true),
    PORT("端口", "默认使用地址中的端口；局域网默认 5667"),
    ACCESS_CODE("访问码", "启用飞牛访问码时填写", true),
}

@Composable
internal fun NasSetupScreen(
    connection: NasConnection,
    onBack: () -> Unit,
    onConnect: (NasConnection) -> Unit,
) {
    var address by remember { mutableStateOf(connection.host.takeIf { it.isNotBlank() }?.let { connection.displayAddress }.orEmpty()) }
    var account by remember { mutableStateOf(connection.username) }
    var password by remember { mutableStateOf(connection.password) }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf(connection.accessCode) }
    var advanced by remember { mutableStateOf(code.isNotBlank()) }
    var editing by remember { mutableStateOf<SetupField?>(null) }
    var focusedField by remember { mutableStateOf(if (address.isBlank()) SetupField.ADDRESS else SetupField.ACCOUNT) }
    var error by remember { mutableStateOf<String?>(null) }
    val fields = remember { SetupField.entries.associateWith { FocusRequester() } }
    val connectRequester = remember { FocusRequester() }

    BackHandler(enabled = editing == null, onBack = onBack)
    LaunchedEffect(editing, error) {
        if (editing == null) runCatching { fields.getValue(focusedField).requestFocus() }
    }

    fun connect() {
        val parsed = NasAddressParser.parse(address)
        val portOverride = port.toIntOrNull()
        val invalidField = when {
            address.isBlank() || !parsed.valid -> SetupField.ADDRESS
            account.isBlank() -> SetupField.ACCOUNT
            password.isBlank() -> SetupField.PASSWORD
            port.isNotBlank() && (portOverride == null || portOverride !in 1..65535) -> SetupField.PORT
            else -> null
        }
        if (invalidField != null) {
            error = when (invalidField) {
                SetupField.ADDRESS -> parsed.error ?: "请输入有效的 NAS 地址"
                SetupField.ACCOUNT -> "请输入账号"
                SetupField.PASSWORD -> "请输入密码"
                SetupField.PORT -> "端口须为 1–65535"
                SetupField.ACCESS_CODE -> null
            }
            if (invalidField == SetupField.PORT) advanced = true
            focusedField = invalidField
            return
        }
        onConnect(connection.copy(
            host = parsed.host,
            scheme = parsed.scheme,
            port = portOverride ?: parsed.port ?: when {
                "://" !in address -> connection.port
                parsed.scheme == "http" -> 80
                else -> 443
            },
            basePath = parsed.basePath,
            connected = false,
        ).updateActiveAccount { it.copy(username = account.trim(), password = password, accessCode = code) })
    }

    fun value(field: SetupField): String = when (field) {
        SetupField.ADDRESS -> address
        SetupField.ACCOUNT -> account
        SetupField.PASSWORD -> password
        SetupField.PORT -> port
        SetupField.ACCESS_CODE -> code
    }

    Box(Modifier.fillMaxSize().background(TvColor.Bg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(560.dp).padding(horizontal = TvDimen.SpaceM),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("连接飞牛 NAS", fontSize = TvType.Section, color = TvColor.TextPrimary)
            Text(error ?: "输入账号和密码后，按「连接」验证并打开相册。", fontSize = TvType.Caption,
                color = if (error == null) TvColor.TextSecondary else TvColor.Warn)
            val visibleFields = if (advanced) SetupField.entries else SetupField.entries.take(3)
            for (field in visibleFields) {
                val current = value(field)
                SetupAction(
                    label = field.title + "  ·  " + when {
                        current.isBlank() -> if (field == SetupField.PORT) "自动" else "未填写"
                        field.masked -> "已填写"
                        else -> current
                    },
                    modifier = Modifier.fillMaxWidth().focusProperties {
                        if (field == visibleFields.last()) down = connectRequester
                    },
                    focusRequester = fields.getValue(field),
                    onClick = {
                        focusedField = field
                        editing = field
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupAction("连接", focusRequester = connectRequester, onClick = ::connect)
                SetupAction(if (advanced) "收起高级选项" else "高级选项", onClick = { advanced = !advanced })
                SetupAction("返回", onClick = onBack)
            }
        }
        editing?.let { field ->
            TextEntryOverlay(
                title = field.title,
                subtitle = field.hint,
                masked = field.masked,
                initial = value(field),
                numericOnly = field == SetupField.PORT,
                emptyHint = if (field == SetupField.PORT) "留空使用地址中的端口或默认端口" else null,
                maxLength = when (field) {
                    SetupField.ADDRESS -> 256
                    SetupField.PORT -> 5
                    else -> 64
                },
                onCancel = { editing = null },
                onDone = { input ->
                    when (field) {
                        SetupField.ADDRESS -> address = input.trim()
                        SetupField.ACCOUNT -> account = input.trim()
                        SetupField.PASSWORD -> password = input
                        SetupField.PORT -> port = input
                        SetupField.ACCESS_CODE -> code = input
                    }
                    error = null
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun SetupAction(
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    TvFocusBox(
        modifier = modifier.background(TvColor.Surface, RoundedCornerShape(8.dp)),
        focusRequester = focusRequester,
        onConfirm = onClick,
        cornerRadiusDp = 8,
        focusedScale = 1.02f,
    ) { focused ->
        Text(
            label,
            color = if (focused) TvColor.Accent else TvColor.TextPrimary,
            fontSize = TvType.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

package com.nasphoto.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nasphoto.tv.ui.theme.ProvideTvDesignDensity

/** 设置弹窗有自己的窗口，需要按窗口尺寸重新计算电视设计密度。 */
@Composable
internal fun SettingsDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        ProvideTvDesignDensity(content = content)
    }
}

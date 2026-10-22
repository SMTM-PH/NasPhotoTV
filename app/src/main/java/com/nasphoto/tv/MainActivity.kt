package com.nasphoto.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nasphoto.tv.data.FavoritesStore
import com.nasphoto.tv.data.SettingsStore
import com.nasphoto.tv.data.AppContextHolder
import com.nasphoto.tv.ui.NasPhotoTvApp

/**
 * 电视端单 Activity。
 * - 沉浸式全屏（隐藏状态栏/导航栏）
 * - 长按屏 | 保持屏幕常亮（电视端通常不需要，但保证原型演示时不被系统休眠打断）
 * - 全局按键监听：系统中记录最后一次交互时间，供"空闲自动进入屏保"使用
 */
class MainActivity : ComponentActivity() {

    @Volatile
    var lastUserInteractionMs: Long = System.currentTimeMillis()
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        SettingsStore.restore()
        FavoritesStore.restore()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            NasPhotoTvApp()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastUserInteractionMs = System.currentTimeMillis()
        }
        return super.dispatchKeyEvent(event)
    }
}

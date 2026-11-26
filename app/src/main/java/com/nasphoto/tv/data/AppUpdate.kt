/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.nasphoto.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * ================= 在线更新（1.2.7） =================
 *
 * 更新源默认走 GitHub Releases 的 latest API；可在设置里换成任意返回同结构 JSON 的备用地址
 * （镜像 / 反代 / 自建），下载时还可以叠加一个镜像前缀，避开电视端访问 github.com 不稳的问题。
 *
 * 安全约束：
 *  - 用**独立** OkHttp client，绝不复用 FnOsTransport.client —— 那个会注入 NAS 的
 *    AccessToken 和自签证书信任，拿去请求外部服务器会把凭据发出去；
 *  - Release 页同时提供 debug / release 两个包，签名不同、不能互相覆盖安装。
 *    运行时读自身签名指纹，只下载与当前安装签名一致的那个包；
 *  - 全程「手动确认」：检查 → 确认下载 → 确认安装，任何一步都不自动执行。
 */

object AppUpdateConfig {
    const val DEFAULT_API = "https://api.github.com/repos/SMTM-PH/NasPhotoTV/releases/latest"
    /** 1.2.4 起正式签名 release 包的证书指纹（与 docs/releases/v1.2.4.md 一致）。 */
    const val RELEASE_CERT_SHA256 = "e63c6b9dfb8870d29b3afc3b6e9657cef6799cdfe3688410b9de4d70e89a525a"
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
}

data class UpdateAsset(val name: String, val url: String, val size: Long)

data class UpdateInfo(
    val version: String,
    val notes: String,
    val assets: List<UpdateAsset>,
    val htmlUrl: String,
)

/** 设置项：空字符串 = 用默认 GitHub；lastCheckMs 用于启动静默检查的每日节流。 */
data class UpdateSettings(
    val autoCheck: Boolean = true,
    val apiUrl: String = "",
    val mirror: String = "",
    val lastCheckMs: Long = 0L,
)

internal fun effectiveApiUrl(custom: String): String =
    custom.trim().ifBlank { AppUpdateConfig.DEFAULT_API }

/**
 * 下载镜像前缀：填了就把原始 asset URL 拼在后面（ghproxy 之类形如
 * `https://mirror.example/https://github.com/...`）；没填则直连原地址。
 */
internal fun mirroredUrl(mirror: String, original: String): String {
    val m = mirror.trim().trimEnd('/')
    if (m.isBlank() || !original.startsWith("http")) return original
    return "$m/$original"
}

/** 语义化版本比较：忽略前导 v，逐段比较数字；非数字段按 0 处理。 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '_')
        .map { it.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(current)
    if (a.isEmpty() || a.all { it == 0 }) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** 兼容 GitHub Releases 的 latest 响应；备用地址只要返回同样字段即可。 */
internal fun parseRelease(json: String): UpdateInfo? = runCatching {
    val o = JSONObject(json)
    val version = o.optString("tag_name").ifBlank { o.optString("name") }.trim()
    if (version.isBlank()) return null
    val assets = buildList {
        val arr = o.optJSONArray("assets")
        if (arr != null) for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val name = a.optString("name")
            val url = a.optString("browser_download_url").ifBlank { a.optString("url") }
            if (name.isNotBlank() && url.startsWith("http")) {
                add(UpdateAsset(name, url, a.optLong("size", 0L)))
            }
        }
    }
    UpdateInfo(
        version = version,
        notes = o.optString("body"),
        assets = assets,
        htmlUrl = o.optString("html_url"),
    )
}.getOrNull()

/** 按当前安装包的签名挑对应附件：正式签名 → release 包，否则 → debug 包。 */
internal fun pickAsset(info: UpdateInfo, isReleaseSignature: Boolean): UpdateAsset? {
    val wantRelease = isReleaseSignature
    return info.assets.firstOrNull {
        val name = it.name.lowercase()
        name.endsWith(".apk") && name.contains("release") == wantRelease
    } ?: info.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

/** 读取自身 APK 签名 SHA-256，判断是否与正式 release 证书一致。 */
internal fun isReleaseSignature(context: Context): Boolean = runCatching {
    val pm = context.packageManager
    val signers = if (Build.VERSION.SDK_INT >= 28) {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val sig = signers?.firstOrNull() ?: return false
    val hex = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
        .joinToString("") { "%02x".format(it) }
    hex.equals(AppUpdateConfig.RELEASE_CERT_SHA256, ignoreCase = true)
}.getOrDefault(false)

/**
 * 全局更新状态。设置页左栏的值胶囊和右栏状态卡片都读这里，
 * 启动时静默检查也只写这里，不会弹窗打断看图。
 */
object AppUpdateState {
    var checking by mutableStateOf(false)
    var downloading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var info by mutableStateOf<UpdateInfo?>(null)
    var upToDate by mutableStateOf(false)
    var downloaded by mutableStateOf<File?>(null)
    var error by mutableStateOf<String?>(null)

    /** 当前主要动作按钮的文字（状态机：检查 → 下载 → 安装）。 */
    val actionLabel: String
        get() = when {
            checking -> "检查中…"
            downloading -> "下载中 $progress%"
            downloaded != null -> "安装 ${info?.version.orEmpty()}"
            info != null -> "下载 ${info?.version.orEmpty()}"
            upToDate -> "已是最新 · 再检查"
            error != null -> "重试检查"
            else -> "检查更新"
        }

    val statusText: String
        get() = when {
            checking -> "正在查询最新版本…"
            downloading -> "正在下载更新包 $progress%"
            downloaded != null -> "更新包已下载，按确认调起系统安装"
            info != null -> "发现新版本 ${info?.version}，当前 ${BuildConfig.VERSION_NAME}"
            upToDate -> "已是最新版本 ${BuildConfig.VERSION_NAME}"
            error != null -> "检查失败：${error}"
            else -> "尚未检查"
        }
}

/** 与 NAS 连接完全隔离的网络客户端。 */
private val updateClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

object AppUpdateController {

    /** 启动时的静默检查：失败也不打扰用户，只记录时间避免当天重复请求。 */
    suspend fun silentCheck(context: Context) {
        val now = System.currentTimeMillis()
        val s = SettingsStore.update
        if (!s.autoCheck || now - s.lastCheckMs < AppUpdateConfig.CHECK_INTERVAL_MS) return
        runCatching { performCheck(context, silent = true) }
        SettingsStore.updateSettings { it.copy(lastCheckMs = System.currentTimeMillis()) }
    }

    /** 手动检查（设置页按确认）：错误会显示在右栏。 */
    suspend fun check(context: Context) {
        if (AppUpdateState.checking || AppUpdateState.downloading) return
        performCheck(context, silent = false)
        SettingsStore.updateSettings { it.copy(lastCheckMs = System.currentTimeMillis()) }
    }

    private suspend fun performCheck(context: Context, silent: Boolean) {
        AppUpdateState.checking = true
        if (!silent) AppUpdateState.error = null
        try {
            val url = effectiveApiUrl(SettingsStore.update.apiUrl)
            val json = fetchJson(url)
            val parsed = parseRelease(json) ?: throw IOException("更新源返回的格式无法识别")
            AppUpdateState.upToDate = false
            AppUpdateState.info = null
            AppUpdateState.downloaded = null
            if (isNewerVersion(parsed.version, BuildConfig.VERSION_NAME)) {
                AppUpdateState.info = parsed
            } else {
                AppUpdateState.upToDate = true
            }
        } catch (t: Throwable) {
            if (!silent) AppUpdateState.error = t.message?.take(120) ?: "网络不可用"
        } finally {
            AppUpdateState.checking = false
        }
    }

    /**
     * 「检查更新」行的确认动作：按当前状态走一步，绝不连跳。
     * 已下载 → 调系统安装；发现新版 → 下载；否则 → 检查。
     */
    suspend fun onConfirm(context: Context) {
        when {
            AppUpdateState.checking || AppUpdateState.downloading -> return
            AppUpdateState.downloaded != null -> install(context, AppUpdateState.downloaded!!)
            AppUpdateState.info != null -> download(context, AppUpdateState.info!!)
            else -> check(context)
        }
    }

    private suspend fun download(context: Context, info: UpdateInfo) {
        val asset = pickAsset(info, isReleaseSignature(context))
            ?: throw IOException("该版本没有匹配当前签名的 APK")
        val url = mirroredUrl(SettingsStore.update.mirror, asset.url)
        AppUpdateState.downloading = true
        AppUpdateState.progress = 0
        AppUpdateState.error = null
        try {
            val file = downloadApk(context, asset.name, url) { p -> AppUpdateState.progress = p }
            AppUpdateState.downloaded = file
        } catch (t: Throwable) {
            AppUpdateState.error = t.message?.take(120) ?: "下载失败"
        } finally {
            AppUpdateState.downloading = false
        }
    }

    /** 调起系统安装界面（用户还需在系统界面上按一次确认）。 */
    fun install(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { AppUpdateState.error = "无法调起安装界面：${it.message}" }
    }

    private suspend fun fetchJson(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NasPhotoTV/${BuildConfig.VERSION_NAME}")
            .build()
        updateClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
    }

    private suspend fun downloadApk(
        context: Context,
        name: String,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // 清掉旧包，避免装到上一次下载的版本
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, name.substringAfterLast('/'))
        val req = Request.Builder().url(url).header("User-Agent", "NasPhotoTV/${BuildConfig.VERSION_NAME}").build()
        updateClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("空响应")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var written = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        if (total > 0) onProgress(((written * 100) / total).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
        if (target.length() < 1024 * 1024) throw IOException("下载的文件异常小，已丢弃")
        onProgress(100)
        target
    }
}

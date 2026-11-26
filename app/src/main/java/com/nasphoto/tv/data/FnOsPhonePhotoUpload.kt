package com.nasphoto.tv.data

import com.nasphoto.tv.data.fn.FnOsApi
import com.nasphoto.tv.data.fn.FnOsException
import com.nasphoto.tv.data.fn.FnOsPhotoImportUncertain
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Uses the fnOS Photos file view's existing default scan folder and two-stage upload. */
internal class FnOsPhonePhotoUpload(private val api: FnOsApi) {
    suspend fun upload(file: File, onImportStarting: suspend () -> Unit) {
        val mime = requirePhotoMime(file)
        val folder = defaultFolder(requireWrite = true)
        val originalName = storedName(file)
        // A deterministic staging name lets a transfer retry overwrite only its own pending file.
        val stagedName = ".$originalName"
        api.uploadPhotoFile(file, "${folder.trimEnd('/')}/$stagedName", originalName, mime)

        val uploaded = JSONObject()
            .put("file_create_time", uploadTime.format(Instant.ofEpochMilli(file.lastModified())))
            .put("original_name", originalName)
            .put("file_name", stagedName)
        val notice = JSONObject().put("folder_path", folder).put("file_list", JSONArray().put(uploaded))
        onImportStarting()
        val result = api.postUploadNotice(UPLOAD_NOTICE, notice)
        val entry = result.optJSONArray("list")?.optJSONObject(0)
            ?: throw FnOsPhotoImportUncertain()
        val refused = when (val error = entry.opt("error")) {
            null, JSONObject.NULL -> false
            is Boolean -> error
            is Number -> error.toDouble() != 0.0
            is String -> error.isNotBlank()
            else -> true
        }
        if (refused) {
            throw FnOsException("飞牛已接收照片文件，但拒绝将其加入相册")
        }
    }

    /** A positive result proves import; a missing row can also mean that indexing has not caught up. */
    suspend fun isImported(file: File): Boolean {
        requirePhotoMime(file)
        val folder = defaultFolder(requireWrite = false)
        val name = storedName(file)
        var offset = 0
        repeat(MAX_VERIFICATION_PAGES) {
            val page = api.getJson(
                FILES,
                listOf("folderPath" to folder, "offset" to offset.toString(), "limit" to "100", "orderBy" to "0", "desc" to "true"),
            )
            val files = page.optJSONArray("list") ?: return false
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index) ?: continue
                if (item.optString("fileName") == name || item.optString("name") == name ||
                    item.optString("filePath").substringAfterLast('/') == name) return true
            }
            offset += files.length()
            if (files.length() == 0 || page.optInt("total", -1) in 0..offset) return false
        }
        return false
    }

    private suspend fun defaultFolder(requireWrite: Boolean): String {
        val folders = api.getJson(FOLDERS).optJSONArray("list")
        for (index in 0 until (folders?.length() ?: 0)) {
            val folder = folders?.optJSONObject(index) ?: continue
            if (!folder.optBoolean("isDefault") && folder.optInt("isDefault") != 1) continue
            if (requireWrite && folder.has("hasWriteAccess") && !folder.optBoolean("hasWriteAccess") &&
                folder.optInt("hasWriteAccess") != 1) {
                throw FnOsException("飞牛相册的默认文件夹没有写入权限")
            }
            val path = folder.optString("folderPath")
            if (path.startsWith('/') && path != "/" && path.none(Char::isISOControl) &&
                path.split('/').none { it == "." || it == ".." }) return path
        }
        throw FnOsException("请先在飞牛相册里设置默认存储文件夹")
    }

    private fun storedName(file: File): String = "NASPhotoTV_${file.name}"

    private fun requirePhotoMime(file: File): String {
        if (!file.isFile || file.length() == 0L || !uploadName.matches(file.name)) {
            throw FnOsException("待备份的照片文件不可用")
        }
        return when (file.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> throw FnOsException("该照片格式无法上传至飞牛")
        }
    }

    private companion object {
        const val FOLDERS = "/p/api/v1/photo/folder/list"
        const val FILES = "/p/api/v1/folder_view/getFileList"
        const val UPLOAD_NOTICE = "/p/api/v1/folder_view/upload/notice"
        const val MAX_VERIFICATION_PAGES = 10
        val uploadName = Regex("[0-9a-fA-F-]{36}\\.[A-Za-z]+")
        val uploadTime = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)
    }
}

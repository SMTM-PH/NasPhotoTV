package com.nasphoto.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID

internal data class PhonePhotoEndpoint(val url: String)

private val phonePhotoExtensions = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/heic" to "heic",
    "image/heif" to "heif",
)

internal object PhonePhotoFiles {
    private const val PHOTO_COUNT_LIMIT = 60
    private const val STORAGE_LIMIT = 160L * 1024 * 1024
    private val nasOwnerDirectory = Regex("[0-9a-f]{64}")

    fun directory(context: Context): File = File(context.noBackupFilesDir, "phone_photos").apply { mkdirs() }

    fun photos(context: Context): List<File> {
        val root = directory(context)
        val accountPhotos = root.listFiles().orEmpty().filter { it.isDirectory && nasOwnerDirectory.matches(it.name) }
            .flatMap(::photoFiles)
        return (photoFiles(root) + accountPhotos)
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
    }

    fun nasPhotos(context: Context, ownerKey: String): List<File> = photoFiles(nasDirectory(context, ownerKey))

    private fun photoFiles(folder: File): List<File> = folder.listFiles().orEmpty()
        .filter { it.isFile && it.extension in phonePhotoExtensions.values && it.length() > 0 }

    private fun nasDirectory(context: Context, ownerKey: String): File {
        require(nasOwnerDirectory.matches(ownerKey))
        return File(directory(context), ownerKey)
    }

    @Synchronized
    fun save(context: Context, temporary: File, extension: String, nasOwnerKey: String?): Boolean {
        val retained = photos(context)
        if (retained.size >= PHOTO_COUNT_LIMIT || retained.sumOf(File::length) + temporary.length() > STORAGE_LIMIT) {
            return false
        }
        val folder = nasOwnerKey?.let { nasDirectory(context, it) } ?: directory(context)
        if (!folder.isDirectory && !folder.mkdirs()) return false
        return temporary.renameTo(File(folder, "${UUID.randomUUID()}.$extension"))
    }

    @Synchronized
    fun clear(context: Context) = photos(context).forEach {
        if (it.delete()) {
            nasReceipt(it).delete()
            nasImportMarker(it).delete()
        }
    }

    fun nasReceipt(photo: File): File = File(photo.parentFile, "${photo.name}.nas-saved")
    fun nasImportMarker(photo: File): File = File(photo.parentFile, "${photo.name}.nas-importing")
}

/** A short-lived, local receiver. No personal photo or upload address is logged. */
internal class PhonePhotoUpload(
    private val context: Context,
    private val nasOwnerKey: String?,
    private val onUploaded: () -> Unit,
) {
    private val lock = Any()
    private var listener: ServerSocket? = null
    private var activeClient: Socket? = null

    fun start(): PhonePhotoEndpoint? = synchronized(lock) {
        val address = SlideshowMusic.lanAddresses().firstOrNull() ?: return null
        stopLocked()
        val server = ServerSocket()
        try {
            server.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 4)
        } catch (_: IOException) {
            server.close()
            return null
        }
        val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val path = "/photo/$token/"
        listener = server
        Thread({ accept(server, path) }, "phone-photo-upload").apply {
            isDaemon = true
            start()
        }
        PhonePhotoEndpoint("http://$address:${server.localPort}$path")
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        val server = listener
        listener = null
        runCatching { server?.close() }
        runCatching { activeClient?.close() }
        activeClient = null
    }

    private fun accept(server: ServerSocket, path: String) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: return
            client.use {
                if (!client.inetAddress.isSiteLocalAddress && !client.inetAddress.isLoopbackAddress) return@use
                synchronized(lock) {
                    if (listener !== server) return
                    activeClient = client
                }
                try {
                    client.soTimeout = 5_000
                    handle(server, client, path)
                } catch (_: IOException) {
                    // The sender can safely retry a request interrupted before publishing its image.
                } finally {
                    synchronized(lock) { if (activeClient === client) activeClient = null }
                }
            }
        }
    }

    private fun handle(server: ServerSocket, client: Socket, path: String) {
        val input = BufferedInputStream(client.getInputStream())
        val deadline = SystemClock.elapsedRealtime() + 60_000
        val request = readLine(input, deadline).split(' ')
        if (request.size != 3) return
        val (method, target) = request
        val headers = readHeaders(input, deadline)
        when {
            method == "GET" && target == path -> respond(
                client, 200, uploadPage(nasOwnerKey != null), "text/html",
            )
            method == "POST" && target == "${path}upload" -> {
                val (status, message) = receive(server, input, headers, deadline)
                respond(client, status, message)
            }
            else -> respond(client, 404, "上传链接已失效，请重新扫码")
        }
    }

    private fun receive(
        server: ServerSocket,
        input: BufferedInputStream,
        headers: Map<String, String>,
        deadline: Long,
    ): Pair<Int, String> {
        if (headers["sec-fetch-site"] == "cross-site" ||
            headers["origin"]?.let { it != "http://${headers["host"]}" } == true
        ) return 403 to "请从电视二维码打开上传页"
        val length = headers["content-length"]?.toLongOrNull() ?: 0
        if (length <= 0 || headers.containsKey("transfer-encoding")) return 400 to "请选择一张照片"
        if (length > MAX_UPLOAD_BYTES) return 413 to "单张照片不能超过 20 MB"
        val directory = PhonePhotoFiles.directory(context)
        val raw = File.createTempFile("source-", ".tmp", directory)
        try {
            val output = File.createTempFile("image-", ".tmp", directory)
            try {
                raw.outputStream().use { file ->
                    val buffer = ByteArray(32 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        checkDeadline(deadline)
                        val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                        if (count <= 0) return 400 to "照片未传完，请重试"
                        file.write(buffer, 0, count)
                        remaining -= count
                    }
                }
                val extension = preparePhoto(raw, output)
                    ?: return 415 to "电视无法读取这张图，请改用 JPEG、PNG 或 WebP"
                val prepared = if (nasOwnerKey != null) raw else output
                val saved = synchronized(lock) {
                    if (listener !== server) return 404 to "上传链接已失效，请重新扫码"
                    PhonePhotoFiles.save(context, prepared, extension, nasOwnerKey)
                }
                if (!saved) return 507 to "电视本机空间不足，请从电视清理已接收的照片"
                onUploaded()
                return 200 to "已接收"
            } finally {
                output.delete()
            }
        } finally {
            raw.delete()
        }
    }

    private fun preparePhoto(raw: File, output: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(raw.path, bounds)
        val extension = phonePhotoExtensions[bounds.outMimeType] ?: return null
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > 120_000_000L) return null

        var sample = 1
        while (maxOf(width, height) / sample > 2560 || (width / sample).toLong() * (height / sample) > 4_500_000) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(raw.path, options) ?: return null
        try {
            if (nasOwnerKey != null) return extension
            val oriented = applyOrientation(decoded, raw)
            try {
                val normalized = output.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                return "jpg".takeIf { normalized }
            } finally {
                if (oriented !== decoded) oriented.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun applyOrientation(bitmap: Bitmap, raw: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(raw.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readHeaders(input: BufferedInputStream, deadline: Long): Map<String, String> = buildMap {
        repeat(32) {
            val line = readLine(input, deadline)
            if (line.isEmpty()) return@buildMap
            val name = line.substringBefore(':').trim().lowercase()
            if (name == "content-length" && containsKey(name)) throw IOException("Duplicate content length")
            put(name, line.substringAfter(':', "").trim())
        }
        throw IOException("Too many request headers")
    }

    private fun readLine(input: BufferedInputStream, deadline: Long): String {
        val line = StringBuilder()
        repeat(2048) {
            checkDeadline(deadline)
            val char = input.read()
            if (char < 0) throw IOException("Unexpected end of request")
            if (char == '\n'.code) return line.toString().removeSuffix("\r")
            line.append(char.toChar())
        }
        throw IOException("Request line too long")
    }

    private fun checkDeadline(deadline: Long) {
        if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Request timed out")
    }

    private fun respond(client: Socket, status: Int, message: String, type: String = "text/plain") {
        val body = message.toByteArray(Charsets.UTF_8)
        val label = if (status == 200) "OK" else "Request Failed"
        val head = "HTTP/1.1 $status $label\r\n" +
            "Content-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "connect-src 'self'; base-uri 'none'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray())
            write(body)
            flush()
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
    }
}

private fun uploadPage(saveToNas: Boolean) = """
<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>传照片到电视</title>
<style>
:root{color-scheme:dark;font-family:system-ui,-apple-system,"PingFang SC",sans-serif}
body{margin:0;background:#09121a;color:#edf5fb;padding:28px 20px}
main{max-width:420px;margin:auto;background:#122432;border:1px solid #254051;border-radius:20px;padding:22px}
h1{margin:0;font-size:24px}p{line-height:1.65;color:#bbcad5}
input{display:block;box-sizing:border-box;width:100%;color:inherit;padding:12px 6px}
button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:10px;background:#a6e4ff;color:#102733;font:inherit;font-weight:650}
button:disabled{opacity:.65}#status{font-size:14px;white-space:pre-line;margin-bottom:0}
</style></head><body><main>
<h1>传照片到电视</h1>
<p>可多选，每张不超过 20 MB。${if (saveToNas) "电视接收后会将原图保存到飞牛 NAS。" else "照片保存在电视本机。"}上传后，用电视遥控器选择「开始展示」。</p>
<input id="photos" type="file" aria-label="选择手机照片" accept="image/*" multiple><button id="send" type="button">上传照片</button>
<p id="status" role="status" aria-live="polite"></p>
</main><script>
const chooser = document.getElementById('photos');
const button = document.getElementById('send');
const status = document.getElementById('status');
button.addEventListener('click', async () => {
    const files = [...chooser.files];
    if (!files.length) { status.textContent = '请先选择照片'; return; }
    button.disabled = chooser.disabled = true;
    let saved = 0;
    const failures = [];
    for (const file of files) {
        status.textContent = '正在上传 ' + (saved + failures.length + 1) + ' / ' + files.length;
        try {
            if (file.size > 20 * 1024 * 1024) throw new Error('超过 20 MB');
            const response = await fetch('./upload', {method:'POST',headers:{'Content-Type':'application/octet-stream'},body:file});
            if (!response.ok) throw new Error(await response.text());
            saved++;
        } catch (e) { failures.push(file.name + '：' + e.message); }
    }
    status.textContent = (saved ? '已接收 ' + saved + ' 张，请在电视上选择「开始展示」。' : '没有照片上传成功。') +
        (failures.length ? '\n' + failures.join('\n') : '');
    if (!failures.length) chooser.value = '';
    button.disabled = chooser.disabled = false;
});
</script></body></html>
""".trimIndent()

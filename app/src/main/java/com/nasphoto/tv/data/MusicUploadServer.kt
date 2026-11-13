package com.nasphoto.tv.data

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * 幻灯片背景音乐：本机存储 + 手机网页上传。
 *
 * 电视上没有文件选择器，所以 App 内起一个极简 HTTP 上传页：
 * 手机连同一局域网 → 浏览器打开电视上显示的地址 → 选一个音频文件上传。
 * 上传的音频存在 App 私有目录（filesDir/slideshow_music），取最新一个作为背景音乐循环播放。
 */
object SlideshowMusic {

    private const val DIR = "slideshow_music"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** 已上传的音频，按修改时间倒序（最新在前）。 */
    fun tracks(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun latest(context: Context): File? = tracks(context).firstOrNull()

    fun delete(context: Context, file: File) {
        runCatching { if (file.parentFile == dir(context)) file.delete() }
    }

    /** 去掉路径分隔符与危险字符，避免写到目录外。 */
    internal fun sanitizeName(raw: String): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
            .trim().take(120)
        return name.ifBlank { "music" }
    }

    /** 本机所有局域网 IPv4 地址（手机要能访问）。 */
    fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
            .distinct()
    }.getOrDefault(emptyList())
}

/**
 * 只做「上传」这一件事的迷你 HTTP 服务。
 *
 * - `GET  /u/<token>/`        上传页面
 * - `POST /u/<token>/upload?name=xxx` 原始二进制请求体 = 文件内容（不用解析 multipart）
 * - `GET  /u/<token>/list`    已上传文件名（JSON）
 *
 * token 是每次启动随机生成，避免同一局域网里别人猜到地址乱传文件。
 */
class MusicUploadServer(private val context: Context) {

    companion object { private const val TAG = "MusicUpload" }

    private val token: String = ByteArray(6).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var thread: Thread? = null

    var port: Int = 0
        private set

    val urls: List<String> get() = SlideshowMusic.lanAddresses().map { "http://$it:$port/u/$token/" }

    /** 启动；端口被占用时向后顺延最多 10 个。 */
    fun start(preferredPort: Int = 8899): Boolean {
        if (socket != null) return true
        for (p in preferredPort until preferredPort + 10) {
            try {
                val server = ServerSocket(p, 50, InetAddress.getByName("0.0.0.0"))
                socket = server
                port = p
                thread = Thread({ acceptLoop(server) }, "music-upload").apply {
                    isDaemon = true; start()
                }
                Log.i(TAG, "listening on $p urls=$urls")
                return true
            } catch (_: Throwable) { /* 换下一个端口 */ }
        }
        return false
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        thread = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            runCatching { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 20_000
            val input = BufferedInputStream(c.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")

            // 头部：只关心 content-length
            var contentLength = -1L
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toLongOrNull() ?: -1L
                }
            }

            val prefix = "/u/$token"
            val out = c.getOutputStream()
            when {
                method == "GET" && (path == "$prefix/" || path == prefix) ->
                    respond(out, 200, "text/html; charset=utf-8", pageHtml().toByteArray())

                method == "GET" && path == "$prefix/list" ->
                    respond(out, 200, "application/json; charset=utf-8", listJson().toByteArray())

                method == "POST" && path == "$prefix/upload" -> {
                    val rawName = query.split('&')
                        .firstOrNull { it.startsWith("name=") }
                        ?.substringAfter("name=")
                        ?.let { URLDecoder.decode(it, "UTF-8") }
                        .orEmpty()
                    if (contentLength <= 0) { respond(out, 400, "text/plain", "empty".toByteArray()); return }
                    if (contentLength > 300L * 1024 * 1024) {
                        respond(out, 413, "text/plain", "too large".toByteArray()); return
                    }
                    val name = SlideshowMusic.sanitizeName(rawName.ifBlank { "music.mp3" })
                    val target0 = File(SlideshowMusic.dir(context), name)
                    val tmp = File(target0.parentFile, "$name.uploading")
                    tmp.outputStream().use { fos -> copyLimited(input, fos, contentLength) }
                    runCatching { if (target0.exists()) target0.delete() }
                    tmp.renameTo(target0)
                    Log.i(TAG, "uploaded ${target0.name} ${target0.length()} bytes")
                    respond(out, 200, "text/plain; charset=utf-8", "OK".toByteArray())
                }

                else -> respond(out, 404, "text/plain", "not found".toByteArray())
            }
        }
    }

    private fun copyLimited(input: BufferedInputStream, out: OutputStream, length: Long) {
        val buf = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
        out.flush()
    }

    private fun respond(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $code ${if (code == 200) "OK" else "ERR"}\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun listJson(): String {
        val names = SlideshowMusic.tracks(context).map { it.name }
        return names.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) break
        }
        return sb.toString()
    }

    private fun pageHtml(): String = """
<!doctype html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>NAS Photo TV · 上传背景音乐</title>
<style>
body{font-family:system-ui,-apple-system,"PingFang SC",sans-serif;margin:0;padding:24px;background:#0b0d10;color:#e8eaed}
h1{font-size:20px;margin:0 0 8px}p{color:#9aa0a6;font-size:14px;line-height:1.6}
.box{margin-top:18px;border:1px solid #2a2f36;border-radius:14px;padding:18px;background:#12151a}
input[type=file]{width:100%;padding:14px;border:1px dashed #3c444e;border-radius:10px;background:#0e1116;color:#e8eaed}
button{margin-top:14px;width:100%;padding:14px;border:0;border-radius:10px;background:#3ddc97;color:#062018;font-size:16px;font-weight:600}
#st{margin-top:12px;font-size:14px}ul{padding-left:18px;color:#c6cbd1;font-size:13px}
.ok{color:#3ddc97}.err{color:#ff7a7a}
</style></head><body>
<h1>上传背景音乐</h1>
<p>选择手机里的一个音频文件（mp3 / m4a / aac / flac / wav），上传后会自动成为电视幻灯片背景音乐（循环播放）。</p>
<div class="box">
  <input id="f" type="file" accept="audio/*">
  <button onclick="up()">上传</button>
  <div id="st"></div>
  <div style="margin-top:14px;color:#9aa0a6;font-size:13px" id="cur"></div>
</div>
<script>
async function refresh(){
  try{const r=await fetch('./list');const a=await r.json();
    document.getElementById('cur').innerHTML = a.length ? '已有：<ul>'+a.map(x=>'<li>'+x+'</li>').join('')+'</ul>' : '还没有上传过音乐';
  }catch(e){}
}
async function up(){
  const f=document.getElementById('f').files[0]; const st=document.getElementById('st');
  if(!f){st.className='err';st.textContent='请先选择文件';return;}
  st.className='';st.textContent='上传中… '+(f.size/1048576).toFixed(1)+'MB';
  try{
    const r=await fetch('./upload?name='+encodeURIComponent(f.name),{method:'POST',body:f,headers:{'Content-Type':'application/octet-stream'}});
    if(!r.ok) throw new Error(await r.text());
    st.className='ok';st.textContent='上传成功：'+f.name;refresh();
  }catch(e){st.className='err';st.textContent='失败：'+e.message;}
}
refresh();
</script></body></html>
""".trimIndent()
}

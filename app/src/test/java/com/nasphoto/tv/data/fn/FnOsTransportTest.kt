package com.nasphoto.tv.data.fn

import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FnOsTransportTest {
    private var previousToken: String? = null

    @Before
    fun saveToken() {
        previousToken = FnOsTransport.token
    }

    @After
    fun restoreToken() {
        FnOsTransport.token = previousToken
    }

    /**
     * One response stays open beyond the API's actual 30-second deadline. Both clients receive
     * bytes every second, so an idle read timeout cannot explain the different outcomes.
     */
    @Test(timeout = 45_000)
    fun videoResponseCanOutliveApiCallDeadline() {
        val expected = ByteArray(33) { it.toByte() }
        LocalHttpServer { socket, _ ->
            val output = socket.getOutputStream()
            output.write(responseHeaders(expected.size))
            expected.forEachIndexed { index, byte ->
                if (index > 0) TimeUnit.SECONDS.sleep(1)
                output.write(byte.toInt())
                output.flush()
            }
        }.use { server ->
            val request = Request.Builder().url(server.url).build()
            val apiCall = FnOsTransport.client.newCall(request)
            val mediaCall = FnOsTransport.mediaClient.newCall(request)
            val readers = Executors.newFixedThreadPool(2)
            try {
                val apiResult = readers.submit<IOException?> {
                    try {
                        apiCall.execute().use { it.body!!.bytes() }
                        null
                    } catch (error: IOException) {
                        error
                    }
                }
                val mediaResult = readers.submit<ByteArray> {
                    mediaCall.execute().use { it.body!!.bytes() }
                }

                assertArrayEquals(expected, mediaResult.get(40, TimeUnit.SECONDS))
                val apiFailure = apiResult.get(1, TimeUnit.SECONDS)
                assertNotNull("The API deadline must still apply to non-media requests", apiFailure)
                assertTrue(
                    "Expected the API's complete-call timeout, got $apiFailure",
                    apiFailure is InterruptedIOException,
                )
                server.assertHealthy()
            } finally {
                apiCall.cancel()
                mediaCall.cancel()
                readers.shutdownNow()
                assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test(timeout = 5_000)
    fun mediaRequestsKeepCookiesAndReadTheLatestToken() {
        LocalHttpServer { socket, request ->
            val cookieHeader = if (request.path == "/session") {
                "Set-Cookie: playback-session=retained; Path=/\r\n"
            } else {
                ""
            }
            socket.getOutputStream().apply {
                write(responseHeaders(2, cookieHeader))
                write("ok".toByteArray(Charsets.US_ASCII))
                flush()
            }
        }.use { server ->
            val url = Request.Builder().url(server.url).build().url
            val originalCookies = FnOsTransport.client.cookieJar.loadForRequest(url)
            try {
                FnOsTransport.token = "session-token"
                readResponse(FnOsTransport.client, "${server.url}session")
                assertEquals("session-token", server.takeRequest().headers["accesstoken"])

                // Reuse this exact client across token changes, as an existing player does.
                val mediaClient = FnOsTransport.mediaClient
                FnOsTransport.token = "first-token"
                readResponse(mediaClient, server.url)
                server.takeRequest().let { request ->
                    assertEquals("first-token", request.headers["accesstoken"])
                    assertTrue(request.headers["cookie"].orEmpty().contains("playback-session=retained"))
                }

                FnOsTransport.token = "refreshed-token"
                readResponse(mediaClient, server.url)
                assertEquals("refreshed-token", server.takeRequest().headers["accesstoken"])

                mediaClient.newCall(
                    Request.Builder().url(server.url).header("AccessToken", "explicit-token").build(),
                ).execute().use { assertEquals("ok", it.body!!.string()) }
                assertEquals("explicit-token", server.takeRequest().headers["accesstoken"])

                FnOsTransport.token = null
                readResponse(mediaClient, server.url)
                assertNull(server.takeRequest().headers["accesstoken"])
                server.assertHealthy()
            } finally {
                FnOsTransport.client.cookieJar.saveFromResponse(url, originalCookies)
            }
        }
    }

    @Test(timeout = 5_000)
    fun mediaStillTimesOutWhenTheServerStopsSendingBytes() {
        val releaseServer = CountDownLatch(1)
        LocalHttpServer { socket, _ ->
            socket.getOutputStream().apply {
                write(responseHeaders(1))
                flush()
            }
            releaseServer.await(3, TimeUnit.SECONDS)
        }.use { server ->
            // Keep the production network limits; shorten only this test's idle interval.
            assertEquals(FnOsTransport.client.connectTimeoutMillis, FnOsTransport.mediaClient.connectTimeoutMillis)
            assertEquals(FnOsTransport.client.readTimeoutMillis, FnOsTransport.mediaClient.readTimeoutMillis)
            assertTrue(FnOsTransport.mediaClient.connectTimeoutMillis > 0)
            assertTrue(FnOsTransport.mediaClient.readTimeoutMillis > 0)
            val client = FnOsTransport.mediaClient.newBuilder()
                .readTimeout(150, TimeUnit.MILLISECONDS)
                .build()
            try {
                val failure = try {
                    readResponse(client, server.url)
                    null
                } catch (error: IOException) {
                    error
                }
                assertTrue("A stalled body must hit its idle read timeout, got $failure", failure is SocketTimeoutException)
                server.assertHealthy()
            } finally {
                releaseServer.countDown()
            }
        }
    }

    private fun readResponse(client: okhttp3.OkHttpClient, url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals("ok", it.body!!.string())
        }
    }

    private fun responseHeaders(bodySize: Int, extraHeaders: String = ""): ByteArray =
        ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: $bodySize\r\n" +
            "Connection: close\r\n" +
            extraHeaders + "\r\n").toByteArray(Charsets.US_ASCII)

    private data class RecordedRequest(val path: String, val headers: Map<String, String>)

    /** Small real HTTP peer; no Android runtime, timing mocks, or additional test dependency. */
    private class LocalHttpServer(
        private val respond: (Socket, RecordedRequest) -> Unit,
    ) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val requests = LinkedBlockingQueue<RecordedRequest>()
        private val failures = ConcurrentLinkedQueue<Throwable>()
        private val workers = Executors.newCachedThreadPool { task ->
            Thread(task, "FnOsTransportTest-http").apply { isDaemon = true }
        }

        val url = "http://127.0.0.1:${server.localPort}/"

        init {
            workers.execute {
                try {
                    while (!server.isClosed) {
                        val socket = server.accept()
                        sockets.add(socket)
                        workers.execute {
                            try {
                                socket.use {
                                    it.soTimeout = 3_000
                                    val reader = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                                    val path = reader.readLine().split(' ')[1]
                                    val headers = mutableMapOf<String, String>()
                                    while (true) {
                                        val line = reader.readLine() ?: break
                                        if (line.isEmpty()) break
                                        headers[line.substringBefore(':').lowercase()] =
                                            line.substringAfter(':').trim()
                                    }
                                    val request = RecordedRequest(path, headers)
                                    requests.add(request)
                                    respond(it, request)
                                }
                            } catch (_: SocketException) {
                                // API deadline / explicit test cleanup may close a response early.
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            } catch (error: Throwable) {
                                failures.add(error)
                            } finally {
                                sockets.remove(socket)
                            }
                        }
                    }
                } catch (error: SocketException) {
                    if (!server.isClosed) failures.add(error)
                }
            }
        }

        fun takeRequest(): RecordedRequest =
            requests.poll(1, TimeUnit.SECONDS) ?: throw AssertionError("No HTTP request received")

        fun assertHealthy() {
            failures.peek()?.let { throw AssertionError("HTTP test peer failed", it) }
        }

        override fun close() {
            server.close()
            sockets.forEach { it.close() }
            workers.shutdownNow()
            assertTrue("HTTP test threads must stop", workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}

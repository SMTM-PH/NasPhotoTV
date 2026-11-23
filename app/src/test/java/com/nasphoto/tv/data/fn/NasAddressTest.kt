package com.nasphoto.tv.data.fn

import com.nasphoto.tv.data.model.NasConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NasAddressTest {

    @Test
    fun plainHostKeepsExplicitDefaults() {
        val parsed = NasAddressParser.parse("192.168.31.8", defaultPort = 5667)
        assertTrue(parsed.valid)
        assertEquals("https", parsed.scheme)
        assertEquals("192.168.31.8", parsed.host)
        assertEquals(5667, parsed.port)
        assertEquals("", parsed.basePath)
    }

    @Test
    fun fullHttpsUrlIsSplitIntoFourParts() {
        val parsed = NasAddressParser.parse("https://nas.example.com:8443/fnos/")
        assertTrue(parsed.valid)
        assertEquals("https", parsed.scheme)
        assertEquals("nas.example.com", parsed.host)
        assertEquals(8443, parsed.port)
        assertEquals("/fnos", parsed.basePath)
    }

    @Test
    fun httpRelayWithNestedPathAndUpperCaseScheme() {
        val parsed = NasAddressParser.parse("HTTP://relay.example.com/photo/nas")
        assertTrue(parsed.valid)
        assertEquals("http", parsed.scheme)
        assertEquals("relay.example.com", parsed.host)
        assertNull(parsed.port)
        assertEquals("/photo/nas", parsed.basePath)
    }

    @Test
    fun ipv6LiteralWithPort() {
        val parsed = NasAddressParser.parse("http://[::1]:5667/fnos")
        assertTrue(parsed.valid)
        assertEquals("[::1]", parsed.host)
        assertEquals(5667, parsed.port)
        assertEquals("/fnos", parsed.basePath)
    }

    @Test
    fun unsupportedSchemeAndBadPortAreRejected() {
        assertFalse(NasAddressParser.parse("ftp://nas.example.com").valid)
        assertFalse(NasAddressParser.parse("https://nas.example.com:99999").valid)
        assertFalse(NasAddressParser.parse("https://nas.example.com:99999999999999999999").valid)
        assertFalse(NasAddressParser.parse("https://").valid)
    }

    @Test
    fun normalizeBasePathHandlesAllShapes() {
        assertEquals("", NasAddressParser.normalizeBasePath("/"))
        assertEquals("", NasAddressParser.normalizeBasePath("  "))
        assertEquals("/fnos", NasAddressParser.normalizeBasePath("fnos/"))
        assertEquals("/a/b", NasAddressParser.normalizeBasePath("/a/b//"))
    }

    @Test
    fun accessUrlsCarryBasePathAndDowngradeWebsocketForHttp() {
        val https = FnOsAccess(
            host = "relay.example.com", port = 443, username = "u", password = "p",
            scheme = "https", basePath = "/fnos",
        )
        assertEquals("https://relay.example.com:443/fnos", https.baseUrl)
        assertEquals("https://relay.example.com:443/fnos/p/api/v1/album/list", https.url("/p/api/v1/album/list"))
        assertEquals("wss://relay.example.com:443/fnos/websocket?type=main", https.wsUrl)

        val http = https.copy(scheme = "http", port = 80, basePath = "")
        assertEquals("http://relay.example.com:80", http.baseUrl)
        assertEquals("ws://relay.example.com:80/websocket?type=main", http.wsUrl)
    }

    @Test
    fun legacyFullUrlStoredInHostIsMigratedByToFnOsAccess() {
        val legacy = NasConnection(host = "http://relay.example.com:8080/nas", port = 5667)
        val access = legacy.toFnOsAccess()
        assertEquals("http", access.scheme)
        assertEquals("relay.example.com", access.host)
        assertEquals(8080, access.port)
        assertEquals("/nas", access.basePath)
        assertEquals("http://relay.example.com:8080/nas/p/api/v1/user_photo/stat",
            access.url("/p/api/v1/user_photo/stat"))
    }

    @Test
    fun separateFieldsArePreservedWhenHostIsPlain() {
        val connection = NasConnection(
            host = "nas.example.com", port = 443,
            scheme = "https", basePath = "/photo",
        )
        val access = connection.toFnOsAccess()
        assertEquals("nas.example.com", access.host)
        assertEquals(443, access.port)
        assertEquals("/photo", access.basePath)
        assertEquals("https", access.scheme)
    }
}

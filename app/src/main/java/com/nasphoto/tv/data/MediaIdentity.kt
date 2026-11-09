package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.NasConnection
import java.security.MessageDigest
import java.util.Locale

/**
 * Runtime ownership key for media snapshots. Connection status and display labels do not
 * change identity; endpoint, account and authentication changes invalidate old media.
 * The key contains a digest, never the credentials themselves, and is not persisted.
 */
fun NasConnection.mediaIdentityKey(): String {
    val fields = listOf(host.trim().lowercase(Locale.ROOT), port.toString(), activeAccountId,
        username, password, accessCode)
    val source = buildString { fields.forEach { append(it.length).append(':').append(it) } }
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}

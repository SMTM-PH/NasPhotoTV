package com.nasphoto.tv.data.fn

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 飞牛登录/签名用到的加密原语。
 *
 * 登录：32 字符 ASCII 作为 AES-256 key，随机 16 字节 IV，PKCS5 填充；
 * AES key 再用服务端下发的 X.509 RSA 公钥做 PKCS#1 加密。
 */
internal object FnOsCrypto {

    private val random = SecureRandom()
    private const val KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun md5(input: String): String = hash("MD5", input.toByteArray(Charsets.UTF_8))

    fun sha256(bytes: ByteArray): String = hash("SHA-256", bytes)

    private fun hash(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun randomAscii(length: Int): String = buildString(length) {
        repeat(length) { append(KEY_CHARS[random.nextInt(KEY_CHARS.length)]) }
    }

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)

    fun randomHex(byteCount: Int): String =
        randomBytes(byteCount).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun aesCbcEncrypt(plain: ByteArray, asciiKey: String, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(asciiKey.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv),
        )
        return cipher.doFinal(plain)
    }

    /** PKCS#1 v1.5 加密（fnOS 服务端要求 RSA/ECB/PKCS1Padding） */
    fun rsaEncrypt(pem: String, data: ByteArray): ByteArray {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val keyBytes = Base64.decode(body, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}

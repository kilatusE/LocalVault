package com.localvault.app.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * LocalVault の中核となる暗号化ユーティリティ。
 * - 鍵導出: PBKDF2WithHmacSHA256 (デフォルト 600,000 回反復 / OWASP推奨)
 * - 暗号化: AES-256-GCM (96-bit ランダム IV + 128-bit 認証タグ)
 * - メモリ保護: 使用済み鍵バッファの明示的なゼロクリア
 */
object CryptoManager {
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000
    const val KEY_SIZE_BYTES = 32 // 256-bit
    const val SALT_SIZE_BYTES = 32 // 256-bit
    const val GCM_IV_SIZE_BYTES = 12 // 96-bit
    private const val GCM_TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun encodeToString(): String {
            val ivB64 = Base64.getEncoder().encodeToString(iv)
            val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
            return "$ivB64:$ctB64"
        }

        companion object {
            fun decodeFromString(encoded: String): EncryptedPayload {
                val parts = encoded.split(":")
                require(parts.size == 2) { "Invalid encrypted payload format" }
                return EncryptedPayload(
                    iv = Base64.getDecoder().decode(parts[0]),
                    ciphertext = Base64.getDecoder().decode(parts[1])
                )
            }
        }
    }

    fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateDek(): ByteArray = generateRandomBytes(KEY_SIZE_BYTES)

    fun generateSalt(): ByteArray = generateRandomBytes(SALT_SIZE_BYTES)

    fun deriveKeyFromPassword(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_SIZE_BYTES * 8)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encryptAesGcm(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray? = null
    ): EncryptedPayload {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 requires a 32-byte key" }
        val iv = generateRandomBytes(GCM_IV_SIZE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(iv = iv, ciphertext = ciphertext)
    }

    fun decryptAesGcm(
        payload: EncryptedPayload,
        key: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 requires a 32-byte key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(payload.ciphertext)
    }

    fun encryptString(plaintext: String, key: ByteArray): String {
        val payload = encryptAesGcm(plaintext.toByteArray(StandardCharsets.UTF_8), key)
        return payload.encodeToString()
    }

    fun decryptString(encoded: String, key: ByteArray): String {
        val payload = EncryptedPayload.decodeFromString(encoded)
        val decrypted = decryptAesGcm(payload, key)
        return try {
            String(decrypted, StandardCharsets.UTF_8)
        } finally {
            zeroize(decrypted)
        }
    }

    /**
     * 使いまわし検知などの比較用 SHA-256 ダイジェスト（保存はせずメモリ内のみで使用）
     */
    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun zeroize(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    fun zeroize(chars: CharArray?) {
        chars?.fill('\u0000')
    }

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}

package com.localvault.app.security

import com.localvault.app.data.Credential
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * ローカル暗号化バックアップ（.lvault 形式）のエクスポート・インポート。
 * パスフレーズから PBKDF2-HMAC-SHA256 (600,000回) で鍵を導出し、AES-256-GCM で全体を暗号化する。
 */
object BackupCryptoManager {
    private const val FORMAT_MAGIC = "LOCALVAULT_BACKUP"
    private const val FORMAT_VERSION = 1
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 5_000_000

    fun exportEncryptedBackup(
        credentials: List<Credential>,
        passphrase: CharArray,
        iterations: Int = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
    ): String {
        val itemsArray = JSONArray()
        for (cred in credentials) {
            val obj = JSONObject().apply {
                put("title", cred.title)
                put("url", cred.url)
                put("domain", cred.domain)
                put("packageName", cred.packageName)
                put("username", cred.username)
                put("password", cred.password)
                put("notes", cred.notes)
                put("createdAt", cred.createdAt)
                put("updatedAt", cred.updatedAt)
            }
            itemsArray.put(obj)
        }

        val innerJson = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("items", itemsArray)
        }.toString()

        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKeyFromPassword(passphrase, salt, iterations)
        return try {
            val aad = "$FORMAT_MAGIC:$FORMAT_VERSION".toByteArray(StandardCharsets.UTF_8)
            val encrypted = CryptoManager.encryptAesGcm(
                plaintext = innerJson.toByteArray(StandardCharsets.UTF_8),
                key = key,
                aad = aad
            )
            JSONObject().apply {
                put("magic", FORMAT_MAGIC)
                put("version", FORMAT_VERSION)
                put("kdf", "PBKDF2WithHmacSHA256")
                put("iterations", iterations)
                put("salt", CryptoManager.toBase64(salt))
                put("iv", CryptoManager.toBase64(encrypted.iv))
                put("ciphertext", CryptoManager.toBase64(encrypted.ciphertext))
            }.toString(2)
        } finally {
            CryptoManager.zeroize(key)
        }
    }

    fun importEncryptedBackup(
        encryptedJson: String,
        passphrase: CharArray
    ): List<Credential> {
        val outer = JSONObject(encryptedJson)
        require(outer.optString("magic") == FORMAT_MAGIC) {
            "無効なバックアップファイル形式です"
        }
        val version = outer.optInt("version", -1)
        require(version == FORMAT_VERSION) {
            "未対応のバックアップバージョンです: $version"
        }
        require(passphrase.isNotEmpty()) {
            "パスフレーズが空です"
        }
        val iterations = outer.optInt("iterations", -1)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "不正な反復回数です: $iterations"
        }
        val saltB64 = outer.optString("salt", "")
        val ivB64 = outer.optString("iv", "")
        val ctB64 = outer.optString("ciphertext", "")
        require(saltB64.isNotBlank() && ivB64.isNotBlank() && ctB64.isNotBlank()) {
            "バックアップの暗号データが欠損しています"
        }
        val salt = CryptoManager.fromBase64(saltB64)
        val iv = CryptoManager.fromBase64(ivB64)
        val ciphertext = CryptoManager.fromBase64(ctB64)
        require(salt.size in 16..64) { "不正なソルト長です" }
        require(iv.size == CryptoManager.GCM_IV_SIZE_BYTES) { "不正なIV長です" }
        require(ciphertext.size > 16) { "暗号文が破損しています" }

        val key = CryptoManager.deriveKeyFromPassword(passphrase, salt, iterations)
        val plainBytes = try {
            val aad = "$FORMAT_MAGIC:$version".toByteArray(StandardCharsets.UTF_8)
            CryptoManager.decryptAesGcm(
                payload = CryptoManager.EncryptedPayload(iv = iv, ciphertext = ciphertext),
                key = key,
                aad = aad
            )
        } finally {
            CryptoManager.zeroize(key)
        }

        val innerJson = try {
            String(plainBytes, StandardCharsets.UTF_8)
        } finally {
            CryptoManager.zeroize(plainBytes)
        }

        val inner = JSONObject(innerJson)
        val items = inner.getJSONArray("items")
        val result = mutableListOf<Credential>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            result += Credential(
                id = 0L,
                title = obj.optString("title"),
                url = obj.optString("url"),
                domain = obj.optString("domain"),
                packageName = obj.optString("packageName"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                notes = obj.optString("notes"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return result
    }
}

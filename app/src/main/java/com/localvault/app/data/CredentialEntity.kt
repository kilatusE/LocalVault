package com.localvault.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQLCipher でデータベース全体が AES-256 暗号化された上で、
 * 各機密フィールドも DEK による AES-256-GCM で個別暗号化して保存するエンティティ。
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val encryptedTitle: String,
    val encryptedUrl: String,
    val encryptedDomain: String,
    val encryptedPackageName: String,
    val encryptedUsername: String,
    val encryptedPassword: String,
    val encryptedNotes: String,
    val createdAt: Long,
    val updatedAt: Long
)

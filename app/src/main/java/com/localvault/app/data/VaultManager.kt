package com.localvault.app.data

import android.content.Context
import com.localvault.app.autofill.DomainMatcher
import com.localvault.app.security.BiometricCryptoManager
import com.localvault.app.security.CryptoManager
import com.localvault.app.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * LocalVault のすべての認証・暗号化DB操作を統括するマネージャー。
 */
class VaultManager(private val appContext: Context) {
    val metaStore = VaultMetaStore(appContext)
    private val dbLock = Any()

    fun isInitialized(): Boolean = metaStore.isVaultInitialized()

    fun isUnlocked(): Boolean = VaultSessionManager.isUnlocked.value

    fun lockVault() {
        synchronized(dbLock) {
            VaultSessionManager.lock()
            VaultDatabase.closeInstance()
        }
    }

    /**
     * 初回セットアップ: DEKを生成し、マスターパスワードから導出したKEKでラップして保存する。
     */
    suspend fun initializeVault(masterPassword: CharArray) = withContext(Dispatchers.Default) {
        val dek = CryptoManager.generateDek()
        val salt = CryptoManager.generateSalt()
        val kek = CryptoManager.deriveKeyFromPassword(masterPassword, salt)
        try {
            val wrappedDek = CryptoManager.encryptAesGcm(dek, kek).encodeToString()
            metaStore.savePasswordWrappedDek(
                salt = salt,
                iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS,
                wrappedDek = wrappedDek
            )
            VaultSessionManager.unlock(dek)
            // SQLCipher DB を初回オープンして初期化確認
            VaultDatabase.getInstance(appContext, dek).credentialDao().getAll()
        } finally {
            CryptoManager.zeroize(kek)
            CryptoManager.zeroize(dek)
            CryptoManager.zeroize(masterPassword)
        }
    }

    /**
     * マスターパスワードでロック解除する。成功時は true、誤パスワード時は false。
     */
    suspend fun unlockWithMasterPassword(masterPassword: CharArray): Boolean =
        withContext(Dispatchers.Default) {
            val salt = metaStore.getPasswordSalt() ?: return@withContext false
            val wrapped = metaStore.getPasswordWrappedDek() ?: return@withContext false
            val iterations = metaStore.getPasswordIterations()
            val kek = CryptoManager.deriveKeyFromPassword(masterPassword, salt, iterations)
            try {
                val payload = CryptoManager.EncryptedPayload.decodeFromString(wrapped)
                val dek = CryptoManager.decryptAesGcm(payload, kek)
                try {
                    VaultSessionManager.unlock(dek)
                    // DBアクセス確認
                    VaultDatabase.getInstance(appContext, dek).credentialDao().getAll()
                    true
                } finally {
                    CryptoManager.zeroize(dek)
                }
            } catch (_: Exception) {
                false
            } finally {
                CryptoManager.zeroize(kek)
                CryptoManager.zeroize(masterPassword)
            }
        }

    /**
     * 生体認証で認証済みの Encrypt Cipher を使って現在の DEK をラップ保存する。
     */
    fun enableBiometricWithCipher(authenticatedCipher: Cipher): Boolean {
        val dek = VaultSessionManager.getDekOrNull() ?: return false
        return try {
            val encryptedDek = authenticatedCipher.doFinal(dek)
            val iv = authenticatedCipher.iv
            metaStore.saveBiometricWrappedDek(iv, encryptedDek)
            true
        } catch (_: Exception) {
            false
        } finally {
            CryptoManager.zeroize(dek)
        }
    }

    /**
     * 生体認証で認証済みの Decrypt Cipher を使って DEK を復号しロック解除する。
     */
    suspend fun unlockWithBiometricCipher(authenticatedCipher: Cipher): Boolean =
        withContext(Dispatchers.Default) {
            val encryptedDek = metaStore.getBiometricWrappedDek() ?: return@withContext false
            try {
                val dek = authenticatedCipher.doFinal(encryptedDek)
                try {
                    VaultSessionManager.unlock(dek)
                    VaultDatabase.getInstance(appContext, dek).credentialDao().getAll()
                    true
                } finally {
                    CryptoManager.zeroize(dek)
                }
            } catch (_: Exception) {
                false
            }
        }

    fun disableBiometric() {
        BiometricCryptoManager.deleteBiometricKey()
        metaStore.clearBiometricData()
    }

    /**
     * マスターパスワード変更（DEK はそのまま維持し、新しい KEK で DEK を再ラップする）。
     */
    suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray
    ): Boolean = withContext(Dispatchers.Default) {
        val salt = metaStore.getPasswordSalt() ?: return@withContext false
        val wrapped = metaStore.getPasswordWrappedDek() ?: return@withContext false
        val iterations = metaStore.getPasswordIterations()
        val oldKek = CryptoManager.deriveKeyFromPassword(currentPassword, salt, iterations)
        var dek: ByteArray? = null
        var newKek: ByteArray? = null
        try {
            val payload = CryptoManager.EncryptedPayload.decodeFromString(wrapped)
            dek = CryptoManager.decryptAesGcm(payload, oldKek)
            val newSalt = CryptoManager.generateSalt()
            newKek = CryptoManager.deriveKeyFromPassword(newPassword, newSalt)
            val newWrapped = CryptoManager.encryptAesGcm(dek, newKek).encodeToString()
            metaStore.savePasswordWrappedDek(
                salt = newSalt,
                iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS,
                wrappedDek = newWrapped
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            CryptoManager.zeroize(oldKek)
            CryptoManager.zeroize(newKek)
            CryptoManager.zeroize(dek)
            CryptoManager.zeroize(currentPassword)
            CryptoManager.zeroize(newPassword)
        }
    }

    fun observeCredentials(): Flow<List<Credential>> {
        val dek = VaultSessionManager.getDekOrNull() ?: return emptyFlow()
        val db = VaultDatabase.getInstance(appContext, dek)
        CryptoManager.zeroize(dek)
        return db.credentialDao().observeAll().map { entities ->
            val currentDek = VaultSessionManager.getDekOrNull() ?: return@map emptyList()
            try {
                entities.mapNotNull { entity ->
                    runCatching { decryptEntity(entity, currentDek) }.getOrNull()
                }
            } finally {
                CryptoManager.zeroize(currentDek)
            }
        }
    }

    suspend fun getAllCredentials(): List<Credential> = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.getDekOrNull() ?: return@withContext emptyList()
        try {
            val entities = VaultDatabase.getInstance(appContext, dek).credentialDao().getAll()
            entities.mapNotNull { runCatching { decryptEntity(it, dek) }.getOrNull() }
        } finally {
            CryptoManager.zeroize(dek)
        }
    }

    suspend fun getCredentialById(id: Long): Credential? = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.getDekOrNull() ?: return@withContext null
        try {
            val entity = VaultDatabase.getInstance(appContext, dek).credentialDao().getById(id)
                ?: return@withContext null
            decryptEntity(entity, dek)
        } finally {
            CryptoManager.zeroize(dek)
        }
    }

    suspend fun saveCredential(credential: Credential): Long = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.getDekOrNull()
            ?: throw IllegalStateException("Vault is locked")
        try {
            val normalizedDomain = credential.domain.ifBlank {
                DomainMatcher.extractDomain(credential.url)
            }
            val now = System.currentTimeMillis()
            val normalized = credential.copy(
                domain = normalizedDomain,
                createdAt = if (credential.id == 0L) now else credential.createdAt,
                updatedAt = now
            )
            val entity = encryptEntity(normalized, dek)
            val dao = VaultDatabase.getInstance(appContext, dek).credentialDao()
            if (credential.id == 0L) {
                dao.insert(entity)
            } else {
                dao.update(entity)
                credential.id
            }
        } finally {
            CryptoManager.zeroize(dek)
        }
    }

    suspend fun deleteCredential(id: Long) = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.getDekOrNull() ?: return@withContext
        try {
            VaultDatabase.getInstance(appContext, dek).credentialDao().deleteById(id)
        } finally {
            CryptoManager.zeroize(dek)
        }
    }

    suspend fun findMatchingCredentials(
        webDomain: String?,
        packageName: String?
    ): List<Credential> {
        val all = getAllCredentials()
        return all.filter { cred ->
            DomainMatcher.matchesTarget(
                storedUrl = cred.url,
                storedDomain = cred.domain,
                storedPackageName = cred.packageName,
                targetWebDomain = webDomain,
                targetPackageName = packageName
            )
        }
    }

    /**
     * Autofill の onSaveRequest から資格情報を新規保存または更新する。
     * @return 保存後の Credential
     */
    suspend fun upsertFromAutofill(
        webDomain: String?,
        packageName: String?,
        username: String,
        password: String
    ): Credential {
        val existing = findMatchingCredentials(webDomain, packageName)
            .firstOrNull { it.username.equals(username, ignoreCase = true) }

        val cleanDomain = webDomain?.let { DomainMatcher.extractDomain(it) }.orEmpty()
        val displayTitle = when {
            cleanDomain.isNotBlank() -> cleanDomain.substringBefore(".")
                .replaceFirstChar { it.uppercase() }
            !packageName.isNullOrBlank() -> packageName.substringAfterLast(".")
                .replaceFirstChar { it.uppercase() }
            else -> "名称未設定"
        }

        val toSave = if (existing != null) {
            existing.copy(
                password = password,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            Credential(
                title = displayTitle,
                url = if (cleanDomain.isNotBlank()) "https://$cleanDomain" else "",
                domain = cleanDomain,
                packageName = packageName.orEmpty(),
                username = username,
                password = password
            )
        }
        val id = saveCredential(toSave)
        return toSave.copy(id = id)
    }

    /**
     * バックアップからのインポート（同じドメイン/パッケージかつ同じユーザー名なら更新、新規なら追加）
     */
    suspend fun importCredentials(items: List<Credential>): Int {
        val existing = getAllCredentials()
        var importedCount = 0
        for (item in items) {
            val duplicate = existing.firstOrNull {
                it.domain.equals(item.domain, ignoreCase = true) &&
                    it.packageName.equals(item.packageName, ignoreCase = true) &&
                    it.username.equals(item.username, ignoreCase = true)
            }
            if (duplicate != null) {
                saveCredential(item.copy(id = duplicate.id))
            } else {
                saveCredential(item.copy(id = 0L))
            }
            importedCount++
        }
        return importedCount
    }

    private fun encryptEntity(cred: Credential, dek: ByteArray): CredentialEntity {
        return CredentialEntity(
            id = cred.id,
            encryptedTitle = CryptoManager.encryptString(cred.title, dek),
            encryptedUrl = CryptoManager.encryptString(cred.url, dek),
            encryptedDomain = CryptoManager.encryptString(cred.domain, dek),
            encryptedPackageName = CryptoManager.encryptString(cred.packageName, dek),
            encryptedUsername = CryptoManager.encryptString(cred.username, dek),
            encryptedPassword = CryptoManager.encryptString(cred.password, dek),
            encryptedNotes = CryptoManager.encryptString(cred.notes, dek),
            createdAt = cred.createdAt,
            updatedAt = cred.updatedAt
        )
    }

    private fun decryptEntity(entity: CredentialEntity, dek: ByteArray): Credential {
        return Credential(
            id = entity.id,
            title = CryptoManager.decryptString(entity.encryptedTitle, dek),
            url = CryptoManager.decryptString(entity.encryptedUrl, dek),
            domain = CryptoManager.decryptString(entity.encryptedDomain, dek),
            packageName = CryptoManager.decryptString(entity.encryptedPackageName, dek),
            username = CryptoManager.decryptString(entity.encryptedUsername, dek),
            password = CryptoManager.decryptString(entity.encryptedPassword, dek),
            notes = CryptoManager.decryptString(entity.encryptedNotes, dek),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

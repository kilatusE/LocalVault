package com.localvault.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore (TEE / StrongBox) と BiometricPrompt.CryptoObject を結合し、
 * 生体認証成功時のみ DEK を暗号化・復号できるようにするマネージャー。
 */
object BiometricCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "localvault_biometric_kek_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun canUseBiometrics(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun deleteBiometricKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    fun createEncryptCipher(): Cipher {
        deleteBiometricKey()
        val secretKey = getOrCreateSecretKey()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }

    /**
     * 復号用 Cipher を作成。生体情報が追加・変更されて鍵が無効化されていた場合は null を返す。
     */
    fun createDecryptCipher(iv: ByteArray): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteBiometricKey()
            null
        } catch (_: Exception) {
            null
        }
    }
}

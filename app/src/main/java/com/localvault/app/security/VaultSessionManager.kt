package com.localvault.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * メモリ上の DEK（データ暗号化鍵）を管理し、バックグラウンド移行やタイムアウト時に
 * 鍵をゼロクリアして Vault を自動ロックするセッションマネージャー。
 */
object VaultSessionManager {
    private val lock = Any()
    private var dek: ByteArray? = null
    private var backgroundTimestampMs: Long? = null

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    fun unlock(rawDek: ByteArray) {
        synchronized(lock) {
            CryptoManager.zeroize(dek)
            dek = rawDek.copyOf()
            backgroundTimestampMs = null
            _isUnlocked.value = true
        }
    }

    fun getDekOrNull(): ByteArray? {
        synchronized(lock) {
            return dek?.copyOf()
        }
    }

    fun lock() {
        synchronized(lock) {
            CryptoManager.zeroize(dek)
            dek = null
            backgroundTimestampMs = null
            _isUnlocked.value = false
        }
    }

    fun onAppBackgrounded(autoLockSeconds: Int) {
        synchronized(lock) {
            if (!_isUnlocked.value) return
            if (autoLockSeconds == 0) {
                lock()
            } else {
                backgroundTimestampMs = monotonicNowMs()
            }
        }
    }

    fun onAppForegrounded(autoLockSeconds: Int) {
        synchronized(lock) {
            if (!_isUnlocked.value) return
            if (autoLockSeconds < 0) return
            val bgTime = backgroundTimestampMs ?: return
            val elapsedSeconds = (monotonicNowMs() - bgTime) / 1000L
            if (elapsedSeconds >= autoLockSeconds) {
                lock()
            } else {
                backgroundTimestampMs = null
            }
        }
    }
}

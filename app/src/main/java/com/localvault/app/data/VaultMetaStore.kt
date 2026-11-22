package com.localvault.app.data

import android.content.Context
import android.content.SharedPreferences
import com.localvault.app.security.CryptoManager
import com.localvault.app.security.PasswordGenOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 暗号化メタデータ（ソルト・ラップ済みDEK）とアプリ設定を保持するストア。
 * 平文のマスターパスワードや平文のDEKは一切保存しない。
 */
class VaultMetaStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isIconMonochrome = MutableStateFlow(prefs.getBoolean(KEY_ICON_MONOCHROME, false))
    val isIconMonochrome: StateFlow<Boolean> = _isIconMonochrome.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _autoLockSeconds = MutableStateFlow(prefs.getInt(KEY_AUTO_LOCK_SECONDS, 60))
    val autoLockSeconds: StateFlow<Int> = _autoLockSeconds.asStateFlow()

    private val _clipboardClearSeconds = MutableStateFlow(prefs.getInt(KEY_CLIPBOARD_CLEAR_SECONDS, 30))
    val clipboardClearSeconds: StateFlow<Int> = _clipboardClearSeconds.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _passwordGenOptions = MutableStateFlow(loadPasswordGenOptions())
    val passwordGenOptions: StateFlow<PasswordGenOptions> = _passwordGenOptions.asStateFlow()

    private fun loadPasswordGenOptions(): PasswordGenOptions = PasswordGenOptions(
        length = prefs.getInt(KEY_GEN_LENGTH, 20),
        includeUppercase = prefs.getBoolean(KEY_GEN_UPPER, true),
        includeLowercase = prefs.getBoolean(KEY_GEN_LOWER, true),
        includeDigits = prefs.getBoolean(KEY_GEN_DIGITS, true),
        includeSymbols = prefs.getBoolean(KEY_GEN_SYMBOLS, true),
        excludeAmbiguous = prefs.getBoolean(KEY_GEN_EXCLUDE_AMBIGUOUS, true)
    )

    fun setPasswordGenOptions(options: PasswordGenOptions) {
        prefs.edit()
            .putInt(KEY_GEN_LENGTH, options.length)
            .putBoolean(KEY_GEN_UPPER, options.includeUppercase)
            .putBoolean(KEY_GEN_LOWER, options.includeLowercase)
            .putBoolean(KEY_GEN_DIGITS, options.includeDigits)
            .putBoolean(KEY_GEN_SYMBOLS, options.includeSymbols)
            .putBoolean(KEY_GEN_EXCLUDE_AMBIGUOUS, options.excludeAmbiguous)
            .apply()
        _passwordGenOptions.value = options
    }

    fun isVaultInitialized(): Boolean =
        prefs.contains(KEY_PASSWORD_SALT) && prefs.contains(KEY_WRAPPED_DEK_PASSWORD)

    fun savePasswordWrappedDek(salt: ByteArray, iterations: Int, wrappedDek: String) {
        prefs.edit()
            .putString(KEY_PASSWORD_SALT, CryptoManager.toBase64(salt))
            .putInt(KEY_PASSWORD_ITERATIONS, iterations)
            .putString(KEY_WRAPPED_DEK_PASSWORD, wrappedDek)
            .apply()
    }

    fun getPasswordSalt(): ByteArray? =
        prefs.getString(KEY_PASSWORD_SALT, null)?.let { CryptoManager.fromBase64(it) }

    fun getPasswordIterations(): Int =
        prefs.getInt(KEY_PASSWORD_ITERATIONS, CryptoManager.DEFAULT_PBKDF2_ITERATIONS)

    fun getPasswordWrappedDek(): String? =
        prefs.getString(KEY_WRAPPED_DEK_PASSWORD, null)

    fun saveBiometricWrappedDek(iv: ByteArray, encryptedDek: ByteArray) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .putString(KEY_BIOMETRIC_IV, CryptoManager.toBase64(iv))
            .putString(KEY_BIOMETRIC_WRAPPED_DEK, CryptoManager.toBase64(encryptedDek))
            .apply()
        _biometricEnabled.value = true
    }

    fun getBiometricIv(): ByteArray? =
        prefs.getString(KEY_BIOMETRIC_IV, null)?.let { CryptoManager.fromBase64(it) }

    fun getBiometricWrappedDek(): ByteArray? =
        prefs.getString(KEY_BIOMETRIC_WRAPPED_DEK, null)?.let { CryptoManager.fromBase64(it) }

    fun clearBiometricData() {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_BIOMETRIC_IV)
            .remove(KEY_BIOMETRIC_WRAPPED_DEK)
            .apply()
        _biometricEnabled.value = false
    }

    fun setIconMonochrome(monochrome: Boolean) {
        prefs.edit().putBoolean(KEY_ICON_MONOCHROME, monochrome).apply()
        _isIconMonochrome.value = monochrome
    }

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
        _isDarkTheme.value = dark
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_SECONDS, seconds).apply()
        _autoLockSeconds.value = seconds
    }

    fun setClipboardClearSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_CLEAR_SECONDS, seconds).apply()
        _clipboardClearSeconds.value = seconds
    }

    companion object {
        private const val PREFS_NAME = "localvault_meta_v1"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_PASSWORD_ITERATIONS = "password_iterations"
        private const val KEY_WRAPPED_DEK_PASSWORD = "wrapped_dek_password"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_IV = "biometric_iv"
        private const val KEY_BIOMETRIC_WRAPPED_DEK = "biometric_wrapped_dek"
        private const val KEY_ICON_MONOCHROME = "icon_monochrome"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        private const val KEY_CLIPBOARD_CLEAR_SECONDS = "clipboard_clear_seconds"
        private const val KEY_GEN_LENGTH = "gen_length"
        private const val KEY_GEN_UPPER = "gen_upper"
        private const val KEY_GEN_LOWER = "gen_lower"
        private const val KEY_GEN_DIGITS = "gen_digits"
        private const val KEY_GEN_SYMBOLS = "gen_symbols"
        private const val KEY_GEN_EXCLUDE_AMBIGUOUS = "gen_exclude_ambiguous"
    }
}

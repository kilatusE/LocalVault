package com.localvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.localvault.app.data.Credential
import com.localvault.app.security.BackupCryptoManager
import com.localvault.app.security.BiometricCryptoManager
import com.localvault.app.security.CryptoManager
import com.localvault.app.security.PasswordHealthAnalyzer
import com.localvault.app.security.VaultSessionManager
import com.localvault.app.ui.generator.PasswordGeneratorScreen
import com.localvault.app.ui.health.SecurityHealthScreen
import com.localvault.app.ui.settings.SettingsScreen
import com.localvault.app.ui.setup.SetupScreen
import com.localvault.app.ui.theme.LocalVaultTheme
import com.localvault.app.ui.theme.VaultTheme
import com.localvault.app.ui.unlock.UnlockScreen
import com.localvault.app.ui.vault.CredentialDetailScreen
import com.localvault.app.ui.vault.CredentialEditScreen
import com.localvault.app.ui.vault.VaultListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val label: String, val icon: ImageVector) {
    VAULT("保管庫", Icons.Rounded.Folder),
    HEALTH("診断", Icons.Rounded.HealthAndSafety),
    GENERATOR("作成", Icons.Rounded.AutoAwesome),
    SETTINGS("設定", Icons.Rounded.Settings)
}

private sealed interface SubScreen {
    data object None : SubScreen
    data class Detail(val credentialId: Long) : SubScreen
    data class Edit(val credential: Credential?, val autoGenerate: Boolean = false) : SubScreen
}

class MainActivity : FragmentActivity() {
    private var pendingBackupPassphrase: CharArray? = null
    private var isAutofillServiceActive by mutableStateOf(false)

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val passphrase = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri == null || passphrase == null) {
            CryptoManager.zeroize(passphrase)
            return@registerForActivityResult
        }
        val vaultManager = (application as LocalVaultApplication).vaultManager
        lifecycleScope.launch {
            try {
                val all = vaultManager.getAllCredentials()
                val encryptedJson = withContext(Dispatchers.Default) {
                    BackupCryptoManager.exportEncryptedBackup(all, passphrase)
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(encryptedJson.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(
                    this@MainActivity,
                    "${all.size}件のバックアップを保存しました",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "バックアップの保存に失敗しました: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                CryptoManager.zeroize(passphrase)
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val passphrase = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri == null || passphrase == null) {
            CryptoManager.zeroize(passphrase)
            return@registerForActivityResult
        }
        val vaultManager = (application as LocalVaultApplication).vaultManager
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: throw IllegalStateException("ファイルを読み込めませんでした")

                val items = withContext(Dispatchers.Default) {
                    BackupCryptoManager.importEncryptedBackup(content, passphrase)
                }
                val count = vaultManager.importCredentials(items)
                Toast.makeText(
                    this@MainActivity,
                    "${count}件のパスワードを復元しました",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "復元に失敗しました（パスワードまたはファイルをご確認ください）",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                CryptoManager.zeroize(passphrase)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // スクリーンショット・画面録画・アプリ履歴サムネイルを禁止
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as LocalVaultApplication
        val vaultManager = app.vaultManager

        setContent {
            val isDarkTheme by vaultManager.metaStore.isDarkTheme.collectAsState()

            LocalVaultTheme(darkTheme = isDarkTheme) {
                val palette = VaultTheme.colors
                SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = palette.bg.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = palette.card.toArgb()
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }

                var isInitialized by remember { mutableStateOf(vaultManager.isInitialized()) }
                val isUnlocked by VaultSessionManager.isUnlocked.collectAsState()
                val biometricEnabled by vaultManager.metaStore.biometricEnabled.collectAsState()
                val biometricAvailable = remember { BiometricCryptoManager.canUseBiometrics(this) }

                when {
                    !isInitialized -> {
                        SetupScreen(
                            onInitializeVault = { password ->
                                vaultManager.initializeVault(password)
                            },
                            onSetupComplete = {
                                isInitialized = true
                            }
                        )
                    }
                    !isUnlocked -> {
                        UnlockScreen(
                            biometricAvailableAndEnabled = biometricAvailable && biometricEnabled,
                            onRequestBiometricUnlock = { launchBiometricUnlock(vaultManager) },
                            onUnlockWithPassword = { password ->
                                vaultManager.unlockWithMasterPassword(password)
                            }
                        )
                    }
                    else -> {
                        UnlockedMainContent(
                            vaultManager = vaultManager,
                            isDarkTheme = isDarkTheme,
                            biometricAvailable = biometricAvailable,
                            isAutofillEnabled = isAutofillServiceActive,
                            onOpenAutofillSettings = { openAndroidAutofillSettings() },
                            onEnableBiometric = { launchBiometricEnrollment(vaultManager) },
                            onDisableBiometric = {
                                vaultManager.disableBiometric()
                                Toast.makeText(this, "生体認証をオフにしました", Toast.LENGTH_SHORT).show()
                            },
                            onStartExport = { passphrase ->
                                CryptoManager.zeroize(pendingBackupPassphrase)
                                pendingBackupPassphrase = passphrase
                                exportBackupLauncher.launch("LocalVault_Backup.lvault")
                            },
                            onStartImport = { passphrase ->
                                CryptoManager.zeroize(pendingBackupPassphrase)
                                pendingBackupPassphrase = passphrase
                                importBackupLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAutofillStatus()
    }

    private fun refreshAutofillStatus() {
        val afm = getSystemService(AutofillManager::class.java)
        isAutofillServiceActive = afm?.hasEnabledAutofillServices() == true
    }

    private fun openAndroidAutofillSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }.onFailure {
            Toast.makeText(
                this,
                "設定 ＞ パスワードとアカウント ＞ 自動入力サービス から LocalVault を選択してください",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchBiometricUnlock(vaultManager: com.localvault.app.data.VaultManager) {
        val iv = vaultManager.metaStore.getBiometricIv() ?: return
        val cipher = BiometricCryptoManager.createDecryptCipher(iv)
        if (cipher == null) {
            vaultManager.disableBiometric()
            Toast.makeText(
                this,
                "端末の生体情報が変更されたため、マスターパスワードでロックを解除してください",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    lifecycleScope.launch {
                        val ok = vaultManager.unlockWithBiometricCipher(authedCipher)
                        if (!ok) {
                            Toast.makeText(
                                this@MainActivity,
                                "生体認証に失敗しました。マスターパスワードをご利用ください。",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LocalVault のロック解除")
            .setSubtitle("認証して保管庫を開きます")
            .setNegativeButtonText("パスワードで解除")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun launchBiometricEnrollment(vaultManager: com.localvault.app.data.VaultManager) {
        val cipher = try {
            BiometricCryptoManager.createEncryptCipher()
        } catch (e: Exception) {
            Toast.makeText(this, "生体認証の設定に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    val ok = vaultManager.enableBiometricWithCipher(authedCipher)
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "生体認証をオンにしました" else "生体認証の設定に失敗しました",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("生体認証をオンにする")
            .setSubtitle("次回から指紋や顔認証でロックを解除できます")
            .setNegativeButtonText("キャンセル")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

@Composable
private fun UnlockedMainContent(
    vaultManager: com.localvault.app.data.VaultManager,
    isDarkTheme: Boolean,
    biometricAvailable: Boolean,
    isAutofillEnabled: Boolean,
    onOpenAutofillSettings: () -> Unit,
    onEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
    onStartExport: (CharArray) -> Unit,
    onStartImport: (CharArray) -> Unit
) {
    val colors = VaultTheme.colors
    val scope = rememberCoroutineScope()
    val credentials by remember { vaultManager.observeCredentials() }.collectAsState(initial = emptyList())
    val healthReport = remember(credentials) { PasswordHealthAnalyzer.analyze(credentials) }
    val isIconMonochrome by vaultManager.metaStore.isIconMonochrome.collectAsState()
    val biometricEnabled by vaultManager.metaStore.biometricEnabled.collectAsState()
    val autoLockSeconds by vaultManager.metaStore.autoLockSeconds.collectAsState()
    val clipboardClearSeconds by vaultManager.metaStore.clipboardClearSeconds.collectAsState()
    val passwordGenOptions by vaultManager.metaStore.passwordGenOptions.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.VAULT) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // 保管庫のトップ画面以外で「戻る」を押した場合はアプリを閉じずに前の画面へ戻る
    BackHandler(enabled = subScreen != SubScreen.None || currentTab != MainTab.VAULT) {
        when (val screen = subScreen) {
            is SubScreen.Edit -> {
                val existing = screen.credential
                subScreen = if (existing != null) SubScreen.Detail(existing.id) else SubScreen.None
            }
            is SubScreen.Detail -> {
                subScreen = SubScreen.None
            }
            SubScreen.None -> {
                currentTab = MainTab.VAULT
            }
        }
    }

    when (val screen = subScreen) {
        is SubScreen.Detail -> {
            val target = credentials.firstOrNull { it.id == screen.credentialId }
            if (target == null) {
                subScreen = SubScreen.None
            } else {
                CredentialDetailScreen(
                    credential = target,
                    reusedPeers = healthReport.getReusedPeersFor(target.id),
                    isIconMonochrome = isIconMonochrome,
                    clipboardClearSeconds = clipboardClearSeconds,
                    onBack = { subScreen = SubScreen.None },
                    onEdit = { subScreen = SubScreen.Edit(target, autoGenerate = false) },
                    onRegenerateAndEdit = { subScreen = SubScreen.Edit(target, autoGenerate = true) },
                    onDelete = {
                        scope.launch {
                            vaultManager.deleteCredential(target.id)
                            subScreen = SubScreen.None
                        }
                    }
                )
            }
        }
        is SubScreen.Edit -> {
            CredentialEditScreen(
                initialCredential = screen.credential,
                autoGenerateOnStart = screen.autoGenerate,
                genOptions = passwordGenOptions,
                onGenOptionsChange = { vaultManager.metaStore.setPasswordGenOptions(it) },
                allCredentials = credentials,
                onBack = {
                    val existing = screen.credential
                    subScreen = if (existing != null) SubScreen.Detail(existing.id) else SubScreen.None
                },
                onSave = { updated ->
                    scope.launch {
                        val savedId = vaultManager.saveCredential(updated)
                        subScreen = SubScreen.Detail(savedId)
                    }
                }
            )
        }
        SubScreen.None -> {
            Scaffold(
                containerColor = colors.bg,
                bottomBar = {
                    NavigationBar(containerColor = colors.card) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accent,
                                    selectedTextColor = colors.textPrimary,
                                    indicatorColor = colors.accentContainer,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentTab) {
                        MainTab.VAULT -> VaultListScreen(
                            credentials = credentials,
                            healthReport = healthReport,
                            isIconMonochrome = isIconMonochrome,
                            isAutofillEnabled = isAutofillEnabled,
                            onOpenAutofillSettings = onOpenAutofillSettings,
                            onOpenHealthTab = { currentTab = MainTab.HEALTH },
                            onSelectCredential = { id -> subScreen = SubScreen.Detail(id) },
                            onAddCredential = { subScreen = SubScreen.Edit(null) },
                            onLockNow = { vaultManager.lockVault() }
                        )
                        MainTab.HEALTH -> SecurityHealthScreen(
                            healthReport = healthReport,
                            isIconMonochrome = isIconMonochrome,
                            onSelectCredential = { id -> subScreen = SubScreen.Detail(id) }
                        )
                        MainTab.GENERATOR -> PasswordGeneratorScreen(
                            options = passwordGenOptions,
                            onOptionsChange = { vaultManager.metaStore.setPasswordGenOptions(it) },
                            clipboardClearSeconds = clipboardClearSeconds
                        )
                        MainTab.SETTINGS -> SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onSetDarkTheme = { vaultManager.metaStore.setDarkTheme(it) },
                            isIconMonochrome = isIconMonochrome,
                            onSetIconMonochrome = { vaultManager.metaStore.setIconMonochrome(it) },
                            biometricAvailable = biometricAvailable,
                            biometricEnabled = biometricEnabled,
                            onToggleBiometric = { enable ->
                                if (enable) onEnableBiometric() else onDisableBiometric()
                            },
                            autoLockSeconds = autoLockSeconds,
                            onSetAutoLockSeconds = { vaultManager.metaStore.setAutoLockSeconds(it) },
                            clipboardClearSeconds = clipboardClearSeconds,
                            onSetClipboardClearSeconds = { vaultManager.metaStore.setClipboardClearSeconds(it) },
                            isAutofillEnabled = isAutofillEnabled,
                            onOpenAutofillSettings = onOpenAutofillSettings,
                            onExportBackup = onStartExport,
                            onImportBackup = onStartImport,
                            onChangeMasterPassword = { cur, next ->
                                vaultManager.changeMasterPassword(cur, next)
                            }
                        )
                    }
                }
            }
        }
    }
}

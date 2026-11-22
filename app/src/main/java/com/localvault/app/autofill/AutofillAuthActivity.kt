package com.localvault.app.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.localvault.app.LocalVaultApplication
import com.localvault.app.R
import com.localvault.app.data.Credential
import com.localvault.app.security.BiometricCryptoManager
import com.localvault.app.security.PasswordHealthAnalyzer
import com.localvault.app.ui.components.SiteAvatar
import com.localvault.app.ui.theme.LocalVaultTheme
import com.localvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自動入力および未登録パスワード保存時のロック解除・候補選択モーダル Activity。
 */
class AutofillAuthActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // スクリーンショット・画面録画を禁止
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as LocalVaultApplication
        val vaultManager = app.vaultManager
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FILL
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val usernameId = getParcelableCompat<AutofillId>(EXTRA_USERNAME_ID)
        val passwordId = getParcelableCompat<AutofillId>(EXTRA_PASSWORD_ID)
        val pendingUsername = intent.getStringExtra(EXTRA_SAVE_USERNAME).orEmpty()
        val pendingPassword = intent.getStringExtra(EXTRA_SAVE_PASSWORD).orEmpty()

        setContent {
            val isDarkTheme by vaultManager.metaStore.isDarkTheme.collectAsState()
            LocalVaultTheme(darkTheme = isDarkTheme) {
                val colors = VaultTheme.colors
                val scope = rememberCoroutineScope()
                val isMonochrome by vaultManager.metaStore.isIconMonochrome.collectAsState()
                val biometricEnabled by vaultManager.metaStore.biometricEnabled.collectAsState()
                var passwordInput by remember { mutableStateOf("") }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var isBusy by remember { mutableStateOf(false) }
                var unlockedCandidates by remember { mutableStateOf<List<Credential>?>(null) }

                fun handlePostUnlock() {
                    scope.launch {
                        if (mode == MODE_SAVE) {
                            val saved = withContext(Dispatchers.IO) {
                                vaultManager.upsertFromAutofill(
                                    webDomain = webDomain,
                                    packageName = targetPackage,
                                    username = pendingUsername,
                                    password = pendingPassword
                                )
                            }
                            val all = withContext(Dispatchers.IO) { vaultManager.getAllCredentials() }
                            val duplicates = PasswordHealthAnalyzer.findDuplicatesForInput(
                                inputPassword = pendingPassword,
                                allCredentials = all,
                                excludeId = saved.id
                            )
                            val msg = if (duplicates.isNotEmpty()) {
                                val peers = duplicates.take(2).joinToString("・") { it.title }
                                "LocalVault に保存しました（⚠️ $peers とパスワードが重複しています）"
                            } else {
                                "LocalVault に安全に保存しました"
                            }
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            val matches = withContext(Dispatchers.IO) {
                                vaultManager.findMatchingCredentials(webDomain, targetPackage)
                            }
                            if (matches.size == 1) {
                                returnAutofillDataset(matches.first(), usernameId, passwordId)
                            } else {
                                val list = if (matches.isNotEmpty()) {
                                    matches
                                } else {
                                    withContext(Dispatchers.IO) { vaultManager.getAllCredentials() }
                                }
                                unlockedCandidates = list
                            }
                        }
                    }
                }

                fun triggerBiometric() {
                    if (!biometricEnabled) return
                    val iv = vaultManager.metaStore.getBiometricIv() ?: return
                    val cipher = BiometricCryptoManager.createDecryptCipher(iv) ?: return
                    val executor = ContextCompat.getMainExecutor(this@AutofillAuthActivity)
                    val prompt = BiometricPrompt(
                        this@AutofillAuthActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                val authedCipher = result.cryptoObject?.cipher ?: return
                                scope.launch {
                                    isBusy = true
                                    val ok = vaultManager.unlockWithBiometricCipher(authedCipher)
                                    isBusy = false
                                    if (ok) {
                                        handlePostUnlock()
                                    } else {
                                        errorMessage = "生体認証での復号に失敗しました。マスターパスワードを入力してください。"
                                    }
                                }
                            }
                        }
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("LocalVault のロック解除")
                        .setSubtitle(
                            if (mode == MODE_SAVE) "パスワードを暗号化保存するため認証してください"
                            else "自動入力するため認証してください"
                        )
                        .setNegativeButtonText("マスターパスワードを使う")
                        .build()
                    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                }

                LaunchedEffect(Unit) {
                    if (vaultManager.isUnlocked()) {
                        handlePostUnlock()
                    } else if (biometricEnabled) {
                        triggerBiometric()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        val candidates = unlockedCandidates
                        if (candidates == null) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (mode == MODE_SAVE) "パスワードを保存" else "LocalVault 自動入力",
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = webDomain ?: targetPackage ?: "ロックを解除してください",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        errorMessage = null
                                    },
                                    label = { Text("マスターパスワード") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        autoCorrectEnabled = false,
                                        capitalization = KeyboardCapitalization.None
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.borderStrong,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (errorMessage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = colors.danger,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (biometricEnabled) {
                                        TextButton(
                                            onClick = { triggerBiometric() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("生体認証")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isBusy = true
                                                val ok = vaultManager.unlockWithMasterPassword(
                                                    passwordInput.toCharArray()
                                                )
                                                passwordInput = ""
                                                isBusy = false
                                                if (ok) {
                                                    handlePostUnlock()
                                                } else {
                                                    errorMessage = "パスワードが正しくありません"
                                                }
                                            }
                                        },
                                        enabled = !isBusy && passwordInput.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryButtonBg,
                                            contentColor = colors.primaryButtonFg
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(
                                                color = colors.primaryButtonFg,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("解除", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                TextButton(onClick = {
                                    setResult(Activity.RESULT_CANCELED)
                                    finish()
                                }) {
                                    Text("キャンセル", color = colors.textSecondary)
                                }
                            }
                        } else {
                            // 候補選択リスト
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "自動入力する項目を選択",
                                    color = colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = webDomain ?: targetPackage ?: "",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                if (candidates.isEmpty()) {
                                    Text(
                                        text = "登録されているパスワードがありません",
                                        color = colors.textSecondary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 20.dp)
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.height(260.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(candidates, key = { it.id }) { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        returnAutofillDataset(item, usernameId, passwordId)
                                                    }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                SiteAvatar(
                                                    title = item.title,
                                                    domain = item.domain,
                                                    isMonochrome = isMonochrome,
                                                    size = 36.dp,
                                                    packageName = item.packageName
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        color = colors.textPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = item.username,
                                                        color = colors.textSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        setResult(Activity.RESULT_CANCELED)
                                        finish()
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("閉じる", color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun returnAutofillDataset(
        credential: Credential,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, credential.title)
            setTextViewText(R.id.autofill_subtitle, credential.username)
        }
        val builder = Dataset.Builder()
        usernameId?.let { id ->
            @Suppress("DEPRECATION")
            builder.setValue(id, AutofillValue.forText(credential.username), presentation)
        }
        passwordId?.let { id ->
            @Suppress("DEPRECATION")
            builder.setValue(id, AutofillValue.forText(credential.password), presentation)
        }
        val response = FillResponse.Builder()
            .addDataset(builder.build())
            .build()

        val reply = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        }
        setResult(Activity.RESULT_OK, reply)
        finish()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> getParcelableCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            intent.getParcelableExtra(key) as? T
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val MODE_FILL = "fill"
        private const val MODE_SAVE = "save"
        private const val EXTRA_WEB_DOMAIN = "web_domain"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_USERNAME_ID = "username_id"
        private const val EXTRA_PASSWORD_ID = "password_id"
        private const val EXTRA_SAVE_USERNAME = "save_username"
        private const val EXTRA_SAVE_PASSWORD = "save_password"

        fun createFillAuthIntent(
            context: Context,
            webDomain: String?,
            packageName: String?,
            usernameId: AutofillId?,
            passwordId: AutofillId?
        ): Intent = Intent(context, AutofillAuthActivity::class.java).apply {
            putExtra(EXTRA_MODE, MODE_FILL)
            putExtra(EXTRA_WEB_DOMAIN, webDomain)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_USERNAME_ID, usernameId)
            putExtra(EXTRA_PASSWORD_ID, passwordId)
        }

        fun createSaveAuthIntent(
            context: Context,
            webDomain: String?,
            packageName: String?,
            username: String,
            password: String
        ): Intent = Intent(context, AutofillAuthActivity::class.java).apply {
            putExtra(EXTRA_MODE, MODE_SAVE)
            putExtra(EXTRA_WEB_DOMAIN, webDomain)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_SAVE_USERNAME, username)
            putExtra(EXTRA_SAVE_PASSWORD, password)
        }
    }
}

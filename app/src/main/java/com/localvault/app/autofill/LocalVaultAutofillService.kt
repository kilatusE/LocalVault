package com.localvault.app.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.localvault.app.LocalVaultApplication
import com.localvault.app.R
import com.localvault.app.data.Credential
import com.localvault.app.security.PasswordHealthAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android 標準 AutofillService 実装。
 * - Chrome 等のブラウザや各アプリのログイン画面で自動入力候補（インライン＆ドロップダウン）を表示
 * - 未登録またはパスワード変更時に OS 標準の「LocalVaultに保存しますか？」ダイアログを自動表示
 */
class LocalVaultAutofillService : AutofillService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val app = application as LocalVaultApplication
        val vaultManager = app.vaultManager
        if (!vaultManager.isInitialized()) {
            callback.onSuccess(null)
            return
        }

        val latestContext = request.fillContexts.lastOrNull()
        val structure = latestContext?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = AssistStructureParser.parse(structure)
        if (!parsed.hasAuthFields) {
            callback.onSuccess(null)
            return
        }

        // 自アプリ自身の画面には自動入力を出さない
        if (parsed.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            if (cancellationSignal.isCanceled) return@launch

            val responseBuilder = FillResponse.Builder()
            val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                request.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty()
            } else {
                emptyList()
            }

            if (!vaultManager.isUnlocked()) {
                // ロック中: 認証用 Dataset を1件返す（タップで生体認証/マスターパスワード画面が起動）
                val authIntent = AutofillAuthActivity.createFillAuthIntent(
                    context = this@LocalVaultAutofillService,
                    webDomain = parsed.webDomain,
                    packageName = parsed.packageName,
                    usernameId = parsed.usernameId,
                    passwordId = parsed.passwordId
                )
                val pendingIntent = PendingIntent.getActivity(
                    this@LocalVaultAutofillService,
                    REQUEST_CODE_UNLOCK,
                    authIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                val presentation = createDropdownPresentation(
                    title = "LocalVault をロック解除",
                    subtitle = parsed.webDomain ?: parsed.packageName ?: "タップして認証"
                )
                val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpecs.isNotEmpty()) {
                    createInlinePresentation(
                        spec = inlineSpecs.first(),
                        title = "LocalVault を解除",
                        subtitle = parsed.webDomain
                    )
                } else {
                    null
                }

                val datasetBuilder = Dataset.Builder()
                parsed.usernameId?.let { id ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                        @Suppress("DEPRECATION")
                        datasetBuilder.setValue(id, null, presentation, inlinePresentation)
                    } else {
                        @Suppress("DEPRECATION")
                        datasetBuilder.setValue(id, null, presentation)
                    }
                }
                parsed.passwordId?.let { id ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                        @Suppress("DEPRECATION")
                        datasetBuilder.setValue(id, null, presentation, inlinePresentation)
                    } else {
                        @Suppress("DEPRECATION")
                        datasetBuilder.setValue(id, null, presentation)
                    }
                }
                datasetBuilder.setAuthentication(pendingIntent.intentSender)
                responseBuilder.addDataset(datasetBuilder.build())

                // ロック中でも未登録サイトでの保存プロンプトを有効化
                buildSaveInfo(parsed)?.let { responseBuilder.setSaveInfo(it) }
                callback.onSuccess(responseBuilder.build())
            } else {
                // アンロック中: マッチする資格情報を直接候補として表示
                val matches = withContext(Dispatchers.IO) {
                    vaultManager.findMatchingCredentials(parsed.webDomain, parsed.packageName)
                }

                matches.forEachIndexed { index, credential ->
                    val inlinePresentation = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        index < inlineSpecs.size
                    ) {
                        createInlinePresentation(
                            spec = inlineSpecs[index],
                            title = "LocalVault: ${credential.username.ifBlank { credential.title }}",
                            subtitle = null
                        )
                    } else {
                        null
                    }
                    responseBuilder.addDataset(
                        buildCredentialDataset(
                            credential = credential,
                            usernameId = parsed.usernameId,
                            passwordId = parsed.passwordId,
                            inlinePresentation = inlinePresentation
                        )
                    )
                }

                // 未登録サイトやパスワード更新時に OS の保存確認ダイアログを出すため SaveInfo を設定
                buildSaveInfo(parsed)?.let { responseBuilder.setSaveInfo(it) }

                if (matches.isEmpty() && buildSaveInfo(parsed) == null) {
                    callback.onSuccess(null)
                } else {
                    callback.onSuccess(responseBuilder.build())
                }
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val app = application as LocalVaultApplication
        val vaultManager = app.vaultManager
        if (!vaultManager.isInitialized()) {
            callback.onFailure("LocalVault の初期設定が完了していません")
            return
        }

        val latestContext = request.fillContexts.lastOrNull()
        val structure = latestContext?.structure
        if (structure == null) {
            callback.onFailure("保存対象の入力情報を取得できませんでした")
            return
        }

        val parsed = AssistStructureParser.parse(structure)
        val username = parsed.usernameValue.orEmpty().trim()
        val password = parsed.passwordValue.orEmpty()

        if (password.isBlank()) {
            callback.onFailure("パスワードが空のため保存をスキップしました")
            return
        }

        if (!vaultManager.isUnlocked()) {
            // ロック中の場合は認証画面を起動して、認証完了後に安全に保存する
            val saveAuthIntent = AutofillAuthActivity.createSaveAuthIntent(
                context = this,
                webDomain = parsed.webDomain,
                packageName = parsed.packageName,
                username = username,
                password = password
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(saveAuthIntent)
            callback.onSuccess()
            return
        }

        serviceScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    vaultManager.upsertFromAutofill(
                        webDomain = parsed.webDomain,
                        packageName = parsed.packageName,
                        username = username,
                        password = password
                    )
                }
                val all = withContext(Dispatchers.IO) { vaultManager.getAllCredentials() }
                val duplicates = PasswordHealthAnalyzer.findDuplicatesForInput(
                    inputPassword = password,
                    allCredentials = all,
                    excludeId = saved.id
                )

                val message = if (duplicates.isNotEmpty()) {
                    val peerNames = duplicates.take(2).joinToString("・") { it.title }
                    "LocalVault に保存しました（⚠️ ${peerNames} とパスワードが重複しています）"
                } else {
                    "LocalVault に安全に保存しました"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                callback.onSuccess()
            } catch (e: Exception) {
                callback.onFailure("保存に失敗しました: ${e.message}")
            }
        }
    }

    private fun buildSaveInfo(parsed: ParsedAutofillFields): SaveInfo? {
        val requiredIds = mutableListOf<AutofillId>()
        var saveDataType = 0

        parsed.passwordId?.let {
            requiredIds += it
            saveDataType = saveDataType or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }
        parsed.usernameId?.let {
            if (requiredIds.isEmpty()) {
                requiredIds += it
            }
            saveDataType = saveDataType or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }

        if (requiredIds.isEmpty() || saveDataType == 0) return null

        val builder = SaveInfo.Builder(saveDataType, requiredIds.toTypedArray())
        parsed.usernameId?.let { userId ->
            if (userId !in requiredIds) {
                builder.setOptionalIds(arrayOf(userId))
            }
        }
        return builder.build()
    }

    private fun buildCredentialDataset(
        credential: Credential,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        inlinePresentation: InlinePresentation?
    ): Dataset {
        val presentation = createDropdownPresentation(
            title = credential.title,
            subtitle = credential.username.ifBlank { credential.domain }
        )
        val builder = Dataset.Builder()
        usernameId?.let { id ->
            val value = AutofillValue.forText(credential.username)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, presentation, inlinePresentation)
            } else {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, presentation)
            }
        }
        passwordId?.let { id ->
            val value = AutofillValue.forText(credential.password)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, presentation, inlinePresentation)
            } else {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, presentation)
            }
        }
        return builder.build()
    }

    private fun createDropdownPresentation(title: String, subtitle: String): RemoteViews {
        return RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createInlinePresentation(
        spec: android.widget.inline.InlinePresentationSpec,
        title: String,
        subtitle: String?
    ): InlinePresentation? {
        return try {
            val versions = UiVersions.getVersions(spec.style)
            if (!versions.contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val attributionIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(),
                PendingIntent.FLAG_IMMUTABLE
            )
            val contentBuilder = InlineSuggestionUi.newContentBuilder(attributionIntent)
                .setTitle(title)
                .setStartIcon(Icon.createWithResource(this, R.drawable.ic_vault_shield))
            if (!subtitle.isNullOrBlank()) {
                contentBuilder.setSubtitle(subtitle)
            }
            InlinePresentation(contentBuilder.build().slice, spec, false)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val REQUEST_CODE_UNLOCK = 1001
    }
}

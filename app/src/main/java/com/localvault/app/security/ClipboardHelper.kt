package com.localvault.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * パスワードコピー時の安全対策:
 * - Android 13+ では EXTRA_IS_SENSITIVE を設定し、画面ポップアップやキーボード履歴への露出を防止
 * - 指定秒数（デフォルト30秒）後にクリップボードを自動的に空で上書き消去
 */
object ClipboardHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    fun copySensitive(
        context: Context,
        label: String,
        value: String,
        autoClearSeconds: Int = 30
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)

        clearJob?.cancel()
        if (autoClearSeconds > 0) {
            clearJob = scope.launch {
                delay(autoClearSeconds * 1000L)
                clearClipboard(context)
            }
        }
    }

    fun clearClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

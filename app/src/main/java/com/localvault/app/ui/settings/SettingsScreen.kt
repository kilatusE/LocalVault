package com.localvault.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.ui.components.SiteAvatar
import com.localvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onSetDarkTheme: (Boolean) -> Unit,
    isIconMonochrome: Boolean,
    onSetIconMonochrome: (Boolean) -> Unit,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    autoLockSeconds: Int,
    onSetAutoLockSeconds: (Int) -> Unit,
    clipboardClearSeconds: Int,
    onSetClipboardClearSeconds: (Int) -> Unit,
    isAutofillEnabled: Boolean,
    onOpenAutofillSettings: () -> Unit,
    onExportBackup: (CharArray) -> Unit,
    onImportBackup: (CharArray) -> Unit,
    onChangeMasterPassword: suspend (CharArray, CharArray) -> Boolean
) {
    val colors = VaultTheme.colors
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showChangePwDialog by remember { mutableStateOf(false) }

    if (showExportDialog) {
        PassphraseDialog(
            title = "バックアップを作成",
            description = "バックアップファイルを保護するためのパスワードを決めてください（8文字以上）。復元するときに必要になります。",
            confirmLabel = "保存先を選ぶ",
            onDismiss = { showExportDialog = false },
            onConfirm = { pass ->
                showExportDialog = false
                onExportBackup(pass)
            }
        )
    }

    if (showImportDialog) {
        PassphraseDialog(
            title = "バックアップから復元",
            description = "バックアップ作成時に決めたパスワードを入力して、ファイルを選択してください。",
            confirmLabel = "ファイルを選ぶ",
            onDismiss = { showImportDialog = false },
            onConfirm = { pass ->
                showImportDialog = false
                onImportBackup(pass)
            }
        )
    }

    if (showChangePwDialog) {
        ChangeMasterPasswordDialog(
            onDismiss = { showChangePwDialog = false },
            onSubmit = { current, next, onResult ->
                scope.launch {
                    val ok = onChangeMasterPassword(current, next)
                    onResult(ok)
                    if (ok) showChangePwDialog = false
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = "設定",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. 表示・外観スタイル
        SectionCard(title = "表示とデザイン") {
            // ダーク / ライト切替
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("外観モード", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("画面の明るさを切り替えられます", color = colors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = isDarkTheme,
                    onClick = { onSetDarkTheme(true) },
                    label = { Text("ダーク") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentContainer,
                        selectedLabelColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !isDarkTheme,
                    onClick = { onSetDarkTheme(false) },
                    label = { Text("ライト") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentContainer,
                        selectedLabelColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 14.dp))

            // サイトアイコンの配色
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("サイトアイコン", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("一覧に表示するアイコンの色を選べます", color = colors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !isIconMonochrome,
                    onClick = { onSetIconMonochrome(false) },
                    label = { Text("サイトカラー") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentContainer,
                        selectedLabelColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isIconMonochrome,
                    onClick = { onSetIconMonochrome(true) },
                    label = { Text("モノクロ") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentContainer,
                        selectedLabelColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("プレビュー:", color = colors.textSecondary, fontSize = 12.sp)
                SiteAvatar("Amazon", "amazon.co.jp", isIconMonochrome, size = 32.dp)
                SiteAvatar("Rakuten", "rakuten.co.jp", isIconMonochrome, size = 32.dp)
                SiteAvatar("Google", "google.com", isIconMonochrome, size = 32.dp)
                SiteAvatar("GitHub", "github.com", isIconMonochrome, size = 32.dp)
            }
        }

        // 2. 自動入力
        SectionCard(title = "自動入力") {
            SettingsActionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "自動入力サービスの設定",
                subtitle = if (isAutofillEnabled) "オンになっています" else "タップして LocalVault を選択",
                subtitleColor = if (isAutofillEnabled) colors.success else colors.accent,
                onClick = onOpenAutofillSettings
            )
        }

        // 3. ロックとセキュリティ
        SectionCard(title = "ロックとセキュリティ") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("生体認証でロック解除", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (biometricAvailable) {
                            "指紋や顔認証ですばやくロックを解除できます"
                        } else {
                            "この端末では生体認証を利用できません"
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    enabled = biometricAvailable,
                    onCheckedChange = onToggleBiometric,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent
                    )
                )
            }

            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LockClock, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
                Text("アプリを閉じたときの自動ロック", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "すぐロック", 60 to "1分後", 300 to "5分後").forEach { (sec, label) ->
                    FilterChip(
                        selected = autoLockSeconds == sec,
                        onClick = { onSetAutoLockSeconds(sec) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentContainer,
                            selectedLabelColor = colors.textPrimary
                        )
                    )
                }
            }

            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 14.dp))

            Text("コピーしたパスワードの自動消去", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15秒後", 30 to "30秒後", 60 to "60秒後").forEach { (sec, label) ->
                    FilterChip(
                        selected = clipboardClearSeconds == sec,
                        onClick = { onSetClipboardClearSeconds(sec) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentContainer,
                            selectedLabelColor = colors.textPrimary
                        )
                    )
                }
            }

            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 14.dp))

            SettingsActionRow(
                icon = Icons.Rounded.Key,
                title = "マスターパスワードを変更",
                subtitle = "ロック解除用のパスワードを変更します",
                onClick = { showChangePwDialog = true }
            )
        }

        // 4. バックアップと復元
        SectionCard(title = "バックアップと復元") {
            SettingsActionRow(
                icon = Icons.Rounded.Upload,
                title = "バックアップを保存",
                subtitle = "機種変更に備えて暗号化ファイルとして保存します",
                onClick = { showExportDialog = true }
            )
            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 12.dp))
            SettingsActionRow(
                icon = Icons.Rounded.Download,
                title = "バックアップから復元",
                subtitle = "保存したファイルからパスワードを取り込みます",
                onClick = { showImportDialog = true }
            )
        }

        // 5. オフライン安心表示
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "完全オフラインで安全に保護中",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "このアプリはインターネット通信を一切行いません。すべてのパスワードは端末内に暗号化して保存され、スクリーンショットも自動でブロックされます。",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = VaultTheme.colors
    Column {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = VaultTheme.colors.textSecondary,
    onClick: () -> Unit
) {
    val colors = VaultTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = subtitleColor, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textSecondary)
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    val colors = VaultTheme.colors
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text(title, color = colors.textPrimary) },
        text = {
            Column {
                Text(description, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("バックアップ用パスワード") },
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = passphrase.length >= 8,
                onClick = { onConfirm(passphrase.toCharArray()) }
            ) {
                Text(confirmLabel, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun ChangeMasterPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (CharArray, CharArray, (Boolean) -> Unit) -> Unit
) {
    val colors = VaultTheme.colors
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text("マスターパスワードを変更", color = colors.textPrimary) },
        text = {
            val passwordKeyboard = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            )
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.borderStrong,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = { Text("現在のパスワード") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = passwordKeyboard,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it; error = null },
                    label = { Text("新しいパスワード（8文字以上）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = passwordKeyboard,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("新しいパスワード（確認用）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = passwordKeyboard,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = colors.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    next.length < 8 -> error = "新しいパスワードは8文字以上にしてください"
                    next != confirm -> error = "確認用パスワードが一致しません"
                    else -> onSubmit(current.toCharArray(), next.toCharArray()) { ok ->
                        if (!ok) error = "現在のパスワードが正しくありません"
                    }
                }
            }) {
                Text("変更する", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = colors.textSecondary)
            }
        }
    )
}

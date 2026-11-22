package com.localvault.app.ui.vault

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.data.Credential
import com.localvault.app.security.ClipboardHelper
import com.localvault.app.security.PasswordGenerator
import com.localvault.app.ui.components.SiteAvatar
import com.localvault.app.ui.theme.VaultTheme

@Composable
fun CredentialDetailScreen(
    credential: Credential,
    reusedPeers: List<Credential>,
    isIconMonochrome: Boolean,
    clipboardClearSeconds: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRegenerateAndEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = VaultTheme.colors
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val strength = remember(credential.password) {
        PasswordGenerator.evaluateStrength(credential.password)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.card,
            title = { Text("この項目を削除しますか？", color = colors.textPrimary) },
            text = {
                Text(
                    "「${credential.title}」のログイン情報を削除します。削除後は元に戻せません。",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("削除する", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("キャンセル", color = colors.textSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // トップバー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "戻る",
                    tint = colors.textPrimary
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "編集",
                        tint = colors.textPrimary
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "削除",
                        tint = colors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // サービスヘッダー
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteAvatar(
                title = credential.title,
                domain = credential.domain,
                isMonochrome = isIconMonochrome,
                size = 50.dp,
                packageName = credential.packageName
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = credential.title,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (credential.domain.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = credential.domain,
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 認証情報カード
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // ユーザー名
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ユーザー名・メール", color = colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = credential.username.ifBlank { "未設定" },
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                    }
                    if (credential.username.isNotBlank()) {
                        IconButton(onClick = {
                            ClipboardHelper.copySensitive(
                                context, "username", credential.username, clipboardClearSeconds
                            )
                            Toast.makeText(context, "ユーザー名をコピーしました", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "コピー",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = colors.border,
                    modifier = Modifier.padding(vertical = 14.dp)
                )

                // パスワード
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "パスワード（強さ: ${strength.label}）",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (showPassword) {
                                credential.password
                            } else {
                                "•".repeat(credential.password.length.coerceIn(8, 18))
                            },
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontFamily = if (showPassword) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "表示切替",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        ClipboardHelper.copySensitive(
                            context, "password", credential.password, clipboardClearSeconds
                        )
                        val msg = if (clipboardClearSeconds > 0) {
                            "コピーしました（${clipboardClearSeconds}秒後に自動で消去されます）"
                        } else {
                            "パスワードをコピーしました"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "コピー",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // パスワード使いまわし警告カード
        if (reusedPeers.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            val peerNames = reusedPeers.joinToString("・") { it.title }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.warningBg),
                border = BorderStroke(1.dp, colors.warningBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "$peerNames と同じパスワードが使われています",
                        color = colors.warningAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "安全のため、サービスごとに別のパスワードへ変更することをおすすめします。",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRegenerateAndEdit,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = if (colors.isDark) Color(0xFF090A0F) else Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("新しいパスワードに変更する", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // URL / アプリ / メモ
        if (credential.url.isNotBlank() || credential.packageName.isNotBlank() || credential.notes.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (credential.url.isNotBlank()) {
                        Column {
                            Text("Webサイト", color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(credential.url, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                    if (credential.packageName.isNotBlank()) {
                        Column {
                            Text("対象アプリID", color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(credential.packageName, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                    if (credential.notes.isNotBlank()) {
                        Column {
                            Text("メモ", color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(credential.notes, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = if (clipboardClearSeconds > 0) {
                "コピーしたパスワードは${clipboardClearSeconds}秒後に自動で消去されます"
            } else {
                "この端末の中だけで安全に保管されています"
            },
            color = colors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(28.dp))
    }
}

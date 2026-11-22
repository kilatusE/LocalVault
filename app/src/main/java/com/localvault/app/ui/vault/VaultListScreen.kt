package com.localvault.app.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.data.Credential
import com.localvault.app.security.SecurityHealthReport
import com.localvault.app.ui.components.SiteAvatar
import com.localvault.app.ui.theme.VaultTheme

@Composable
fun VaultListScreen(
    credentials: List<Credential>,
    healthReport: SecurityHealthReport,
    isIconMonochrome: Boolean,
    isAutofillEnabled: Boolean,
    onOpenAutofillSettings: () -> Unit,
    onOpenHealthTab: () -> Unit,
    onSelectCredential: (Long) -> Unit,
    onAddCredential: () -> Unit,
    onLockNow: () -> Unit
) {
    val colors = VaultTheme.colors
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(credentials, searchQuery) {
        if (searchQuery.isBlank()) {
            credentials
        } else {
            val q = searchQuery.trim()
            credentials.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.domain.contains(q, ignoreCase = true) ||
                    it.username.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "保管庫",
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onLockNow) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "今すぐロック",
                        tint = colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 検索バー
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("サイト・アプリを検索", color = colors.textMuted, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // 自動入力サービス未設定バナー
            if (!isAutofillEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onOpenAutofillSettings() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動入力をオンにしましょう",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "サイトやアプリでパスワードを自動入力できます",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Text(
                            text = "設定 →",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // パスワード重複警告バー
            if (healthReport.reusedCredentialCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clickable { onOpenHealthTab() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.warningBg),
                    border = BorderStroke(1.dp, colors.warningBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.warningAccent)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${healthReport.reusedCredentialCount}件のパスワードが使いまわされています",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "確認 →",
                            color = colors.warningAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // パスワード一覧
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (credentials.isEmpty()) "保存されたパスワードはありません" else "一致する項目がありません",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                        if (credentials.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "右下の「＋」から登録、またはブラウザでログインすると保存できます",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isReused = item.id in healthReport.reusedCredentialIds
                        CredentialListRow(
                            credential = item,
                            isReused = isReused,
                            isIconMonochrome = isIconMonochrome,
                            onClick = { onSelectCredential(item.id) }
                        )
                        HorizontalDivider(
                            color = colors.border,
                            thickness = 0.8.dp
                        )
                    }
                }
            }
        }

        // 右下 FAB（ボトムナビすぐ上の絶妙な位置）
        FloatingActionButton(
            onClick = onAddCredential,
            shape = CircleShape,
            containerColor = colors.accent,
            contentColor = if (colors.isDark) Color(0xFF090A0F) else Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 14.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "新規登録")
        }
    }
}

@Composable
private fun CredentialListRow(
    credential: Credential,
    isReused: Boolean,
    isIconMonochrome: Boolean,
    onClick: () -> Unit
) {
    val colors = VaultTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiteAvatar(
            title = credential.title,
            domain = credential.domain,
            isMonochrome = isIconMonochrome,
            size = 42.dp,
            packageName = credential.packageName
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = credential.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = credential.username.ifBlank { credential.domain },
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isReused) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.warningBg)
                    .border(BorderStroke(1.dp, colors.warningBorder), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "重複",
                    color = colors.warningAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

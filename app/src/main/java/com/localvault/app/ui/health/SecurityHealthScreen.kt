package com.localvault.app.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.security.SecurityHealthReport
import com.localvault.app.ui.components.SiteAvatar
import com.localvault.app.ui.theme.VaultTheme

@Composable
fun SecurityHealthScreen(
    healthReport: SecurityHealthReport,
    isIconMonochrome: Boolean,
    onSelectCredential: (Long) -> Unit
) {
    val colors = VaultTheme.colors
    val scoreColor = when {
        healthReport.securityScore >= 85 -> colors.success
        healthReport.securityScore >= 60 -> colors.warningAccent
        else -> colors.danger
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "セキュリティ診断",
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "パスワードの使いまわしや推測されやすさをチェックします",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        // スコアカード
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { healthReport.securityScore / 100f },
                            color = scoreColor,
                            trackColor = colors.surfaceElevated,
                            strokeWidth = 7.dp,
                            modifier = Modifier.size(76.dp)
                        )
                        Text(
                            text = "${healthReport.securityScore}%",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "登録済み: ${healthReport.totalCount} 件",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "使いまわし: ${healthReport.reusedCredentialCount} 件",
                            color = if (healthReport.reusedCredentialCount > 0) colors.warningAccent else colors.success,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "弱いパスワード: ${healthReport.weakCredentials.size} 件",
                            color = if (healthReport.weakCredentials.isNotEmpty()) colors.danger else colors.success,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 使いまわしグループ一覧
        item {
            Text(
                text = "使いまわされているパスワード",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (healthReport.reusedGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = "使いまわされているパスワードはありません。安全です！",
                        color = colors.success,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(healthReport.reusedGroups) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.warningBg),
                    border = BorderStroke(1.dp, colors.warningBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "同じパスワードを ${group.credentials.size} 件のサービスで使用中",
                            color = colors.warningAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        group.credentials.forEachIndexed { idx, cred ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectCredential(cred.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SiteAvatar(
                                    title = cred.title,
                                    domain = cred.domain,
                                    isMonochrome = isIconMonochrome,
                                    size = 36.dp,
                                    packageName = cred.packageName
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cred.title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(cred.username, color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Text("変更 →", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (idx < group.credentials.lastIndex) {
                                HorizontalDivider(color = colors.warningBorder.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        // 弱いパスワード一覧
        if (healthReport.weakCredentials.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "推測されやすいパスワード",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(healthReport.weakCredentials, key = { "weak_${it.id}" }) { cred ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCredential(cred.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SiteAvatar(
                            title = cred.title,
                            domain = cred.domain,
                            isMonochrome = isIconMonochrome,
                            size = 38.dp,
                            packageName = cred.packageName
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cred.title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(cred.username, color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Text("見直す →", color = colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

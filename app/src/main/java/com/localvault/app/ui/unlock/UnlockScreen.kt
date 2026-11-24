package com.localvault.app.ui.unlock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.R
import com.localvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(
    biometricAvailableAndEnabled: Boolean,
    onRequestBiometricUnlock: () -> Unit,
    onUnlockWithPassword: suspend (CharArray) -> Boolean,
    getRemainingLockoutSeconds: () -> Long = { 0L }
) {
    val colors = VaultTheme.colors
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricAvailableAndEnabled) {
        if (biometricAvailableAndEnabled) {
            onRequestBiometricUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ロゴ＋タイトル
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (colors.isDark) {
                        Brush.linearGradient(listOf(Color(0xFF23283B), Color(0xFF121520)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF1E243D), Color(0xFF0E111E)))
                    }
                )
                .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "LocalVault",
                modifier = Modifier.size(68.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "LocalVault",
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.card)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "オフライン保護",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(40.dp))

        // 生体認証リング
        if (biometricAvailableAndEnabled) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onRequestBiometricUnlock() }
                    .padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "生体認証でロック解除",
                        tint = colors.accent,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "生体認証でロック解除",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        // マスターパスワード入力欄（中央寄りの押しやすい高さに配置）
        Column(modifier = Modifier.fillMaxWidth()) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text("マスターパスワード", color = colors.textMuted, fontSize = 14.sp)
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            errorMessage = null
                            val ok = onUnlockWithPassword(password.toCharArray())
                            password = ""
                            isVerifying = false
                            if (!ok) {
                                val remaining = getRemainingLockoutSeconds()
                                errorMessage = if (remaining > 0L) {
                                    "連続失敗のため一時ロック中です（あと約${remaining}秒お待ちください）"
                                } else {
                                    "パスワードが正しくありません"
                                }
                            }
                        }
                    },
                    enabled = !isVerifying && password.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonFg
                    ),
                    modifier = Modifier.height(54.dp)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            color = colors.primaryButtonFg,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("解除", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 下部に適度な余白を設けて入力欄を上に引き上げる
        Spacer(Modifier.height(64.dp))
    }
}

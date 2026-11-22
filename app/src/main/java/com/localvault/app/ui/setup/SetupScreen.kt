package com.localvault.app.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.R
import com.localvault.app.security.PasswordGenerator
import com.localvault.app.security.PasswordStrength
import com.localvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    onInitializeVault: suspend (CharArray) -> Unit,
    onSetupComplete: () -> Unit
) {
    val colors = VaultTheme.colors
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val strengthScore = remember(password) { PasswordGenerator.calculateStrengthScore(password) }
    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }
    val strengthColor = colors.colorForStrength(strength)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "LocalVault",
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "LocalVault へようこそ",
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.card)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "完全オフラインで安全に管理",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "マスターパスワードを決める",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "アプリを開くときに使う大切なパスワードです。通信を一切行わないため、忘れると再発行できませんのでご注意ください。",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("マスターパスワード（8文字以上）") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "表示切替",
                                tint = colors.textSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("強さ: ${strength.label}", color = strengthColor, fontSize = 12.sp)
                        Text("$strengthScore / 100", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { strengthScore / 100f },
                        color = strengthColor,
                        trackColor = colors.surfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("もう一度入力（確認用）") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
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
                    Spacer(Modifier.height(10.dp))
                    Text(errorMessage!!, color = colors.danger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        when {
                            password.length < 8 -> {
                                errorMessage = "安全のため8文字以上で設定してください"
                            }
                            password != confirmPassword -> {
                                errorMessage = "確認用パスワードが一致しません"
                            }
                            else -> {
                                scope.launch {
                                    isCreating = true
                                    try {
                                        onInitializeVault(password.toCharArray())
                                        password = ""
                                        confirmPassword = ""
                                        onSetupComplete()
                                    } catch (e: Exception) {
                                        errorMessage = "作成に失敗しました: ${e.message}"
                                    } finally {
                                        isCreating = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isCreating && password.isNotEmpty() && confirmPassword.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonFg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            color = colors.primaryButtonFg,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("はじめる", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

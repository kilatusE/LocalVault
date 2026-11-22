package com.localvault.app.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.autofill.DomainMatcher
import com.localvault.app.data.Credential
import com.localvault.app.security.PasswordGenOptions
import com.localvault.app.security.PasswordGenerator
import com.localvault.app.security.PasswordHealthAnalyzer
import com.localvault.app.ui.components.PasswordGenOptionsCard
import com.localvault.app.ui.theme.VaultTheme

@Composable
fun CredentialEditScreen(
    initialCredential: Credential?,
    autoGenerateOnStart: Boolean,
    genOptions: PasswordGenOptions,
    onGenOptionsChange: (PasswordGenOptions) -> Unit,
    allCredentials: List<Credential>,
    onBack: () -> Unit,
    onSave: (Credential) -> Unit
) {
    val colors = VaultTheme.colors
    var title by remember { mutableStateOf(initialCredential?.title.orEmpty()) }
    var url by remember { mutableStateOf(initialCredential?.url.orEmpty()) }
    var packageName by remember { mutableStateOf(initialCredential?.packageName.orEmpty()) }
    var username by remember { mutableStateOf(initialCredential?.username.orEmpty()) }
    var password by remember {
        mutableStateOf(
            if (autoGenerateOnStart) PasswordGenerator.generate(genOptions)
            else initialCredential?.password.orEmpty()
        )
    }
    var notes by remember { mutableStateOf(initialCredential?.notes.orEmpty()) }
    var showPassword by remember { mutableStateOf(autoGenerateOnStart) }
    var genOptionsExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val liveDuplicates = remember(password, allCredentials, initialCredential?.id) {
        PasswordHealthAnalyzer.findDuplicatesForInput(
            inputPassword = password,
            allCredentials = allCredentials,
            excludeId = initialCredential?.id
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.borderStrong,
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "戻る",
                    tint = colors.textPrimary
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (initialCredential == null) "パスワードを新規登録" else "パスワードを編集",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("サービス名 *") },
            placeholder = { Text("例: Amazon, 楽天, GitHub") },
            singleLine = true,
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("WebサイトのURL") },
            placeholder = { Text("例: https://www.amazon.co.jp") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false
            ),
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("ユーザー名・メールアドレス") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false
            ),
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("パスワード *") },
            singleLine = true,
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
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                password = PasswordGenerator.generate(genOptions)
                showPassword = true
            },
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent)
            Spacer(Modifier.width(8.dp))
            Text("安全なパスワードを自動作成", color = colors.accent)
        }

        // 「パスワード作成」タブと完全一致する生成条件パネルを最初から表示（タップで開閉）
        Spacer(Modifier.height(10.dp))
        PasswordGenOptionsCard(
            options = genOptions,
            expanded = genOptionsExpanded,
            onToggleExpanded = { genOptionsExpanded = !genOptionsExpanded },
            onOptionsChange = { newOpts ->
                onGenOptionsChange(newOpts)
                password = PasswordGenerator.generate(newOpts)
                showPassword = true
            }
        )

        if (liveDuplicates.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val peerNames = liveDuplicates.joinToString("・") { it.title }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.warningBg),
                border = BorderStroke(1.dp, colors.warningBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "他のサイトと同じパスワードです",
                        color = colors.warningAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "「$peerNames」と同じパスワードが使われています。別のパスワードにすることをおすすめします。",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = packageName,
            onValueChange = { packageName = it },
            label = { Text("対象のアプリID（任意）") },
            placeholder = { Text("例: com.example.app") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("メモ（任意）") },
            minLines = 3,
            colors = fieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, colors.borderStrong),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("キャンセル", color = colors.textSecondary)
            }
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    val cleanDomain = DomainMatcher.extractDomain(url)
                    val resolvedTitle = title.trim().ifBlank {
                        cleanDomain.ifBlank { "名称未設定" }
                    }
                    onSave(
                        Credential(
                            id = initialCredential?.id ?: 0L,
                            title = resolvedTitle,
                            url = url.trim(),
                            domain = cleanDomain,
                            packageName = packageName.trim(),
                            username = username.trim(),
                            password = password,
                            notes = notes.trim(),
                            createdAt = initialCredential?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                enabled = !isSaving && title.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryButtonBg,
                    contentColor = colors.primaryButtonFg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("保存する", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

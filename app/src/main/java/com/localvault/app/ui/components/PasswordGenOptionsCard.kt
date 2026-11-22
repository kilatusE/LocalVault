package com.localvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.security.PasswordGenOptions
import com.localvault.app.ui.theme.VaultTheme

/**
 * 「パスワード作成」画面と「新規登録・編集」画面で共通使用する、
 * タップで開閉できるパスワード生成条件カード。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGenOptionsCard(
    options: PasswordGenOptions,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOptionsChange: (PasswordGenOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VaultTheme.colors
    val lengthChoices = remember { listOf(8, 12, 16, 20, 24, 32, 48) }
    var lengthMenuExpanded by remember { mutableStateOf(false) }

    val summary = remember(options) {
        val types = buildList {
            if (options.includeUppercase) add("大文字")
            if (options.includeLowercase) add("小文字")
            if (options.includeDigits) add("数字")
            if (options.includeSymbols) add("記号")
        }.joinToString("・").ifEmpty { "標準" }
        "${options.length}文字 / $types"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column {
            // タップで表示切替するヘッダー行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = colors.accent
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "作成条件",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "閉じる" else "開く",
                    tint = colors.textSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(bottom = 14.dp))

                    // 文字数ドロップダウン
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "文字数",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        ExposedDropdownMenuBox(
                            expanded = lengthMenuExpanded,
                            onExpandedChange = { lengthMenuExpanded = !lengthMenuExpanded },
                            modifier = Modifier.width(136.dp)
                        ) {
                            OutlinedTextField(
                                value = "${options.length} 文字",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = lengthMenuExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.borderStrong,
                                    focusedContainerColor = colors.surfaceElevated,
                                    unfocusedContainerColor = colors.surfaceElevated,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            ExposedDropdownMenu(
                                expanded = lengthMenuExpanded,
                                onDismissRequest = { lengthMenuExpanded = false },
                                containerColor = colors.card
                            ) {
                                lengthChoices.forEach { count ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "$count 文字",
                                                color = if (count == options.length) colors.accent else colors.textPrimary,
                                                fontWeight = if (count == options.length) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            lengthMenuExpanded = false
                                            onOptionsChange(options.copy(length = count))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    GenOptionToggleRow(
                        label = "英大文字を含める (A–Z)",
                        checked = options.includeUppercase,
                        onCheckedChange = { onOptionsChange(options.copy(includeUppercase = it)) }
                    )
                    GenOptionToggleRow(
                        label = "英小文字を含める (a–z)",
                        checked = options.includeLowercase,
                        onCheckedChange = { onOptionsChange(options.copy(includeLowercase = it)) }
                    )
                    GenOptionToggleRow(
                        label = "数字を含める (0–9)",
                        checked = options.includeDigits,
                        onCheckedChange = { onOptionsChange(options.copy(includeDigits = it)) }
                    )
                    GenOptionToggleRow(
                        label = "記号を含める (!@#\$...)",
                        checked = options.includeSymbols,
                        onCheckedChange = { onOptionsChange(options.copy(includeSymbols = it)) }
                    )
                    GenOptionToggleRow(
                        label = "見分けにくい文字を除く (0, O, 1, l, I)",
                        checked = options.excludeAmbiguous,
                        onCheckedChange = { onOptionsChange(options.copy(excludeAmbiguous = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenOptionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = VaultTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.textPrimary, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent
            )
        )
    }
}

package com.localvault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.localvault.app.security.PasswordStrength

@Immutable
data class VaultPalette(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentContainer: Color,
    val primaryButtonBg: Color,
    val primaryButtonFg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningAccent: Color,
    val danger: Color,
    val success: Color
) {
    /**
     * パスワード強度ラベルに対応する色を一元管理して返す。
     * STRONG / EXCELLENT = 緑 (success)、FAIR = アンバー (warningAccent)、WEAK = 赤 (danger)
     */
    fun colorForStrength(strength: PasswordStrength): Color = when (strength) {
        PasswordStrength.EXCELLENT, PasswordStrength.STRONG -> success
        PasswordStrength.FAIR -> warningAccent
        PasswordStrength.WEAK -> danger
    }
}

// 案B：オブシディアン・ダーク
val DarkVaultPalette = VaultPalette(
    isDark = true,
    bg = Color(0xFF090A0F),
    card = Color(0xFF14161D),
    surfaceElevated = Color(0xFF1B1E28),
    border = Color.White.copy(alpha = 0.08f),
    borderStrong = Color.White.copy(alpha = 0.14f),
    accent = Color(0xFF818CF8),
    accentContainer = Color(0xFF1E2240),
    primaryButtonBg = Color.White,
    primaryButtonFg = Color(0xFF090A0F),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    warningBg = Color(0xFF261E12),
    warningBorder = Color(0xFF5C431C),
    warningAccent = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    success = Color(0xFF34D399)
)

// 案Aベース：ミニマル・ライト（警告色はアンバー、危険色は赤に明確に分離）
val LightVaultPalette = VaultPalette(
    isDark = false,
    bg = Color(0xFFF6F7FA),
    card = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEEF1F6),
    border = Color(0xFFE5E7EB),
    borderStrong = Color(0xFFD1D5DB),
    accent = Color(0xFF2563EB),
    accentContainer = Color(0xFFE8F0FE),
    primaryButtonBg = Color(0xFF111827),
    primaryButtonFg = Color.White,
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5563),
    textMuted = Color(0xFF9CA3AF),
    warningBg = Color(0xFFFFFBEB),
    warningBorder = Color(0xFFFCD34D),
    warningAccent = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    success = Color(0xFF059669)
)

val LocalVaultPalette = staticCompositionLocalOf { DarkVaultPalette }

object VaultTheme {
    val colors: VaultPalette
        @Composable get() = LocalVaultPalette.current
}

@Composable
fun LocalVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkVaultPalette else LightVaultPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF090A0F),
            primaryContainer = palette.accentContainer,
            onPrimaryContainer = palette.textPrimary,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderStrong,
            error = palette.danger,
            onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentContainer,
            onPrimaryContainer = palette.textPrimary,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderStrong,
            error = palette.danger,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalVaultPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

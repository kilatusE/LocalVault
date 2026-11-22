package com.localvault.app.security

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordGenOptions(
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
)

enum class PasswordStrength(val label: String, val scoreRange: IntRange) {
    WEAK("弱い", 0..39),
    FAIR("普通", 40..64),
    STRONG("強い", 65..84),
    EXCELLENT("非常に強い", 85..100)
}

/**
 * SecureRandom（暗号学的擬似乱数生成器）を用いた安全なパスワード生成＆強度評価。
 */
object PasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val UPPERCASE_CLEAR = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val LOWERCASE_CLEAR = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val DIGITS_CLEAR = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:,.<>?"

    private val random = SecureRandom()

    fun generate(options: PasswordGenOptions = PasswordGenOptions()): String {
        val length = options.length.coerceIn(8, 64)
        val pools = mutableListOf<String>()
        if (options.includeUppercase) {
            pools += if (options.excludeAmbiguous) UPPERCASE_CLEAR else UPPERCASE
        }
        if (options.includeLowercase) {
            pools += if (options.excludeAmbiguous) LOWERCASE_CLEAR else LOWERCASE
        }
        if (options.includeDigits) {
            pools += if (options.excludeAmbiguous) DIGITS_CLEAR else DIGITS
        }
        if (options.includeSymbols) {
            pools += SYMBOLS
        }
        if (pools.isEmpty()) {
            pools += LOWERCASE_CLEAR
            pools += DIGITS_CLEAR
        }

        val combined = pools.joinToString("")
        val result = CharArray(length)
        var index = 0

        // 選択された各文字種を最低1文字保証
        for (pool in pools) {
            if (index < length) {
                result[index++] = pool[random.nextInt(pool.length)]
            }
        }
        while (index < length) {
            result[index++] = combined[random.nextInt(combined.length)]
        }

        // Fisher-Yates シャッフル
        for (i in result.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }

        return String(result)
    }

    /**
     * パスワードのエントロピーから 0〜100 の強度スコアを算出する。
     */
    fun calculateStrengthScore(password: String): Int {
        if (password.isEmpty()) return 0
        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32
        if (poolSize == 0) return 0

        val entropy = password.length * log2(poolSize.toDouble())
        val uniqueRatio = password.toSet().size.toDouble() / password.length.toDouble()
        val adjustedEntropy = entropy * (0.6 + 0.4 * uniqueRatio)
        return ((adjustedEntropy / 95.0) * 100.0).toInt().coerceIn(0, 100)
    }

    fun evaluateStrength(password: String): PasswordStrength {
        val score = calculateStrengthScore(password)
        return when {
            score >= 85 -> PasswordStrength.EXCELLENT
            score >= 65 -> PasswordStrength.STRONG
            score >= 40 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}

package com.localvault.app.security

import com.localvault.app.data.Credential

data class ReusedPasswordGroup(
    val passwordHash: String,
    val credentials: List<Credential>
)

data class SecurityHealthReport(
    val totalCount: Int,
    val securityScore: Int,
    val reusedGroups: List<ReusedPasswordGroup>,
    val reusedCredentialIds: Set<Long>,
    val weakCredentials: List<Credential>
) {
    val reusedCredentialCount: Int get() = reusedCredentialIds.size

    fun getReusedPeersFor(credentialId: Long): List<Credential> {
        val group = reusedGroups.firstOrNull { g -> g.credentials.any { it.id == credentialId } }
            ?: return emptyList()
        return group.credentials.filter { it.id != credentialId }
    }
}

/**
 * 完全オフラインでローカルの資格情報からパスワード使いまわし・強度不足を診断するアナライザー。
 */
object PasswordHealthAnalyzer {
    fun analyze(credentials: List<Credential>): SecurityHealthReport {
        if (credentials.isEmpty()) {
            return SecurityHealthReport(
                totalCount = 0,
                securityScore = 100,
                reusedGroups = emptyList(),
                reusedCredentialIds = emptySet(),
                weakCredentials = emptyList()
            )
        }

        val groupedByHash = credentials
            .filter { it.password.isNotEmpty() }
            .groupBy { CryptoManager.sha256Hex(it.password) }

        val reusedGroups = groupedByHash
            .filter { it.value.size >= 2 }
            .map { (hash, list) -> ReusedPasswordGroup(passwordHash = hash, credentials = list) }
            .sortedByDescending { it.credentials.size }

        val reusedIds = reusedGroups.flatMap { group -> group.credentials.map { it.id } }.toSet()
        val weakList = credentials.filter {
            PasswordGenerator.calculateStrengthScore(it.password) < 55
        }

        // セキュリティスコア算出（使いまわし・弱いパスワードの割合に応じて減点）
        val total = credentials.size.toDouble()
        val reusePenalty = (reusedIds.size / total) * 55.0
        val weakPenalty = (weakList.size / total) * 35.0
        val score = (100.0 - reusePenalty - weakPenalty).toInt().coerceIn(0, 100)

        return SecurityHealthReport(
            totalCount = credentials.size,
            securityScore = score,
            reusedGroups = reusedGroups,
            reusedCredentialIds = reusedIds,
            weakCredentials = weakList
        )
    }

    /**
     * 入力中のパスワードが既存の他アカウントと重複していないかリアルタイム判定する。
     */
    fun findDuplicatesForInput(
        inputPassword: String,
        allCredentials: List<Credential>,
        excludeId: Long? = null
    ): List<Credential> {
        if (inputPassword.isEmpty()) return emptyList()
        val targetHash = CryptoManager.sha256Hex(inputPassword)
        return allCredentials.filter { cred ->
            cred.id != excludeId &&
                cred.password.isNotEmpty() &&
                CryptoManager.sha256Hex(cred.password) == targetHash
        }
    }
}

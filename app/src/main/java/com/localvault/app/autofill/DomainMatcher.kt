package com.localvault.app.autofill

import java.net.URI
import java.util.Locale

/**
 * WebドメインおよびAndroidパッケージ名の正規化・照合ユーティリティ。
 */
object DomainMatcher {
    private val multiPartTlds = setOf(
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "ed.jp", "gr.jp", "lg.jp",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
        "co.kr", "or.kr", "com.tw", "org.tw", "com.br", "com.cn", "com.hk",
        "com.sg", "co.in", "co.nz", "co.za",
        // 共有ホスティング / PaaS ドメイン（サブドメイン間で別テナントとなるため同一サイトとみなさない）
        "github.io", "gitlab.io", "pages.dev", "vercel.app", "netlify.app",
        "web.app", "firebaseapp.com", "appspot.com", "herokuapp.com", "azurewebsites.net",
        "cloudfront.net", "s3.amazonaws.com", "workers.dev"
    )

    fun extractDomain(rawUrlOrDomain: String): String {
        val trimmed = rawUrlOrDomain.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return ""
        return try {
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = URI(withScheme)
            val host = uri.host
                ?: trimmed
                    .substringAfter("://")
                    .substringBefore("/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .substringAfterLast("@") // userinfo (https://good.com@evil.com) フィッシング対策
                    .substringBefore(":")
            host.removePrefix("www.").trimEnd('.')
        } catch (_: Exception) {
            trimmed
                .substringAfter("://")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringAfterLast("@")
                .substringBefore(":")
                .removePrefix("www.")
                .trimEnd('.')
        }
    }

    /**
     * ベースドメイン（例: login.shop.example.co.jp -> example.co.jp）を取得する。
     */
    fun extractRegistrableDomain(domain: String): String {
        val clean = extractDomain(domain)
        if (clean.isEmpty()) return ""
        val parts = clean.split(".").filter { it.isNotEmpty() }
        if (parts.size <= 2) return clean
        val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        return if (lastTwo in multiPartTlds && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun domainsMatch(storedDomainOrUrl: String, targetDomain: String): Boolean {
        val stored = extractDomain(storedDomainOrUrl)
        val target = extractDomain(targetDomain)
        if (stored.isEmpty() || target.isEmpty()) return false
        if (stored == target) return true
        return extractRegistrableDomain(stored) == extractRegistrableDomain(target)
    }

    fun matchesTarget(
        storedUrl: String,
        storedDomain: String,
        storedPackageName: String,
        targetWebDomain: String?,
        targetPackageName: String?
    ): Boolean {
        if (!targetWebDomain.isNullOrBlank()) {
            val candidateDomain = storedDomain.ifBlank { extractDomain(storedUrl) }
            if (domainsMatch(candidateDomain, targetWebDomain)) return true
        }
        if (!targetPackageName.isNullOrBlank() && storedPackageName.isNotBlank()) {
            if (storedPackageName.equals(targetPackageName, ignoreCase = true)) return true
        }
        return false
    }
}

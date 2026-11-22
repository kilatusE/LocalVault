package com.localvault.app.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import java.util.Locale

data class ParsedAutofillFields(
    val webDomain: String?,
    val packageName: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val usernameValue: String?,
    val passwordValue: String?
) {
    val hasAuthFields: Boolean get() = usernameId != null || passwordId != null
}

/**
 * ブラウザや他アプリの AssistStructure を再帰解析し、
 * Webドメイン、パッケージ名、ユーザー名欄・パスワード欄の AutofillId と現在値を抽出する。
 */
object AssistStructureParser {
    private val usernameKeywords = listOf(
        "user", "login", "email", "mail", "account", "id", "phone", "tel", "ログイン", "メール", "ユーザー"
    )
    private val passwordKeywords = listOf(
        "pass", "pwd", "secret", "pin", "パスワード", "暗証"
    )

    fun parse(structure: AssistStructure): ParsedAutofillFields {
        var webDomain: String? = null
        var packageName: String? = structure.activityComponent?.packageName
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue: String? = null
        var passwordValue: String? = null

        val windowCount = structure.windowNodeCount
        for (i in 0 until windowCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode ?: continue
            traverseNode(root) { node ->
                if (webDomain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                    webDomain = node.webDomain
                }
                if (packageName.isNullOrBlank() && !node.idPackage.isNullOrBlank()) {
                    packageName = node.idPackage
                }

                val autofillId = node.autofillId ?: return@traverseNode
                if (!isEditableField(node)) return@traverseNode

                val fieldType = classifyNode(node)
                val currentText = extractNodeText(node)

                when (fieldType) {
                    FieldType.PASSWORD -> {
                        if (passwordId == null || !currentText.isNullOrEmpty()) {
                            passwordId = autofillId
                            passwordValue = currentText
                        }
                    }
                    FieldType.USERNAME -> {
                        if (usernameId == null || !currentText.isNullOrEmpty()) {
                            usernameId = autofillId
                            usernameValue = currentText
                        }
                    }
                    FieldType.UNKNOWN -> Unit
                }
            }
        }

        return ParsedAutofillFields(
            webDomain = webDomain?.let { DomainMatcher.extractDomain(it) }?.ifBlank { null },
            packageName = packageName,
            usernameId = usernameId,
            passwordId = passwordId,
            usernameValue = usernameValue,
            passwordValue = passwordValue
        )
    }

    private enum class FieldType { USERNAME, PASSWORD, UNKNOWN }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        visitor: (AssistStructure.ViewNode) -> Unit
    ) {
        visitor(node)
        for (i in 0 until node.childCount) {
            node.getChildAt(i)?.let { traverseNode(it, visitor) }
        }
    }

    private fun isEditableField(node: AssistStructure.ViewNode): Boolean {
        if (node.autofillType == View.AUTOFILL_TYPE_TEXT) return true
        val className = node.className.orEmpty()
        if (className.contains("EditText", ignoreCase = true)) return true
        val htmlTag = node.htmlInfo?.tag?.lowercase(Locale.ROOT)
        if (htmlTag == "input") {
            val typeAttr = node.htmlInfo?.attributes
                ?.firstOrNull { it.first.equals("type", ignoreCase = true) }
                ?.second?.lowercase(Locale.ROOT)
            return typeAttr == null || typeAttr in setOf("text", "email", "password", "tel", "search")
        }
        return false
    }

    private fun classifyNode(node: AssistStructure.ViewNode): FieldType {
        // 1. 標準 autofillHints を最優先
        val hints = node.autofillHints.orEmpty().map { it.lowercase(Locale.ROOT) }
        if (hints.any { it.contains("password") }) return FieldType.PASSWORD
        if (hints.any {
                it.contains("username") || it.contains("email") || it.contains("phone")
            }
        ) {
            return FieldType.USERNAME
        }

        // 2. HTML input type 属性（Webブラウザ向け）
        val htmlAttrs = node.htmlInfo?.attributes.orEmpty()
        val htmlType = htmlAttrs
            .firstOrNull { it.first.equals("type", ignoreCase = true) }
            ?.second?.lowercase(Locale.ROOT)
        if (htmlType == "password") return FieldType.PASSWORD
        if (htmlType == "email") return FieldType.USERNAME

        val htmlAutocomplete = htmlAttrs
            .firstOrNull { it.first.equals("autocomplete", ignoreCase = true) }
            ?.second?.lowercase(Locale.ROOT)
        if (htmlAutocomplete != null) {
            if (htmlAutocomplete.contains("password")) return FieldType.PASSWORD
            if (htmlAutocomplete.contains("username") || htmlAutocomplete.contains("email")) {
                return FieldType.USERNAME
            }
        }

        // 3. Android InputType 判定
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return FieldType.PASSWORD

                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return FieldType.USERNAME
            }
        } else if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return FieldType.PASSWORD
        }

        // 4. ヒューリスティック判定（idEntry, hint, HTML name/id/placeholder）
        val metadata = buildString {
            append(node.idEntry.orEmpty()).append(' ')
            append(node.hint.orEmpty()).append(' ')
            append(node.contentDescription?.toString().orEmpty()).append(' ')
            for (attr in htmlAttrs) {
                if (attr.first.lowercase(Locale.ROOT) in setOf("name", "id", "placeholder", "aria-label")) {
                    append(attr.second).append(' ')
                }
            }
        }.lowercase(Locale.ROOT)

        if (passwordKeywords.any { metadata.contains(it) }) return FieldType.PASSWORD
        if (usernameKeywords.any { metadata.contains(it) }) return FieldType.USERNAME

        return FieldType.UNKNOWN
    }

    private fun extractNodeText(node: AssistStructure.ViewNode): String? {
        val afValue = node.autofillValue
        if (afValue != null && afValue.isText) {
            return afValue.textValue?.toString()
        }
        return node.text?.toString()
    }
}

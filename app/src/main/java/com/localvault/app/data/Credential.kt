package com.localvault.app.data

data class Credential(
    val id: Long = 0L,
    val title: String,
    val url: String = "",
    val domain: String = "",
    val packageName: String = "",
    val username: String,
    val password: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

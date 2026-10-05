package com.sudo.googleai

enum class Role { USER, MODEL }

data class ChatMessage(
    val role: Role,
    val content: String,
    val isError: Boolean = false
)
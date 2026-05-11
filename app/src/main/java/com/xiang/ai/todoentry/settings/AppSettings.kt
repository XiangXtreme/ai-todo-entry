package com.xiang.ai.todoentry.settings

data class AppSettings(
    val llmBaseUrl: String = "https://api.deepseek.com",
    val llmModel: String = "deepseek-v4-flash",
    val defaultListId: String? = null
)

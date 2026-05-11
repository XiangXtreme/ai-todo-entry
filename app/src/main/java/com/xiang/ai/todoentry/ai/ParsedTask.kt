package com.xiang.ai.todoentry.ai

data class ParsedTask(
    val title: String,
    val body: String? = null,
    val dueDateTime: String? = null,
    val reminderDateTime: String? = null,
    val importance: TaskImportance = TaskImportance.NORMAL,
    val targetListName: String? = null,
    val confidence: Float = 0f
)

data class ParsedTaskBatch(
    val tasks: List<ParsedTask>
)

enum class TaskImportance(val graphValue: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun from(value: String?): TaskImportance = when (value?.lowercase()) {
            "low" -> LOW
            "high" -> HIGH
            else -> NORMAL
        }
    }
}

interface TaskParser {
    suspend fun parse(input: String, settings: com.xiang.ai.todoentry.settings.AppSettings, apiKey: String): ParsedTaskBatch
}

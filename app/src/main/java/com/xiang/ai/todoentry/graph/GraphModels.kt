package com.xiang.ai.todoentry.graph

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.xiang.ai.todoentry.ai.ParsedTask
import java.time.LocalDateTime
import java.time.ZoneId

@JsonClass(generateAdapter = false)
data class TodoListResponse(
    val value: List<TodoListDto> = emptyList()
)

@JsonClass(generateAdapter = false)
data class TodoTaskResponse(
    val value: List<TodoTaskDto> = emptyList()
)

@JsonClass(generateAdapter = false)
data class TodoListDto(
    val id: String,
    val displayName: String
)

@JsonClass(generateAdapter = false)
data class TodoTaskDto(
    val id: String? = null,
    val title: String,
    val importance: String? = null,
    val status: String? = null,
    val body: ItemBody? = null,
    val dueDateTime: DateTimeTimeZone? = null,
    val reminderDateTime: DateTimeTimeZone? = null,
    val isReminderOn: Boolean? = null
)

@JsonClass(generateAdapter = false)
data class UpdateTodoTaskRequest(
    val title: String? = null,
    val status: String? = null,
    val importance: String? = null,
    val body: ItemBody? = null
)

@JsonClass(generateAdapter = false)
data class CreateTodoTaskRequest(
    val title: String,
    val body: ItemBody? = null,
    val dueDateTime: DateTimeTimeZone? = null,
    val reminderDateTime: DateTimeTimeZone? = null,
    val isReminderOn: Boolean? = null,
    val importance: String = "normal"
) {
    companion object {
        fun from(task: ParsedTask, zoneId: ZoneId = ZoneId.systemDefault()): CreateTodoTaskRequest {
            val title = task.title.trim()
            require(title.isNotBlank()) { "Task title is required" }
            return CreateTodoTaskRequest(
                title = title,
                body = task.body?.takeIf { it.isNotBlank() }?.let { ItemBody(content = it) },
                dueDateTime = task.dueDateTime?.toGraphDate(zoneId),
                reminderDateTime = task.reminderDateTime?.toGraphDate(zoneId),
                isReminderOn = task.reminderDateTime?.isNotBlank() == true,
                importance = task.importance.graphValue
            )
        }

        private fun String.toGraphDate(zoneId: ZoneId): DateTimeTimeZone? {
            val parsed = runCatching { LocalDateTime.parse(this) }.getOrNull() ?: return null
            return DateTimeTimeZone(
                dateTime = parsed.toString(),
                timeZone = zoneId.id
            )
        }
    }
}

@JsonClass(generateAdapter = false)
data class ItemBody(
    val content: String,
    val contentType: String = "text"
)

@JsonClass(generateAdapter = false)
data class DateTimeTimeZone(
    val dateTime: String,
    val timeZone: String
)

@JsonClass(generateAdapter = false)
data class GraphErrorResponse(
    val error: GraphError? = null
)

@JsonClass(generateAdapter = false)
data class GraphError(
    val code: String? = null,
    val message: String? = null,
    @Json(name = "innerError") val innerError: Map<String, Any?>? = null
)

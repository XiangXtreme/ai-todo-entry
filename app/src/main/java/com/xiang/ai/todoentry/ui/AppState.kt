package com.xiang.ai.todoentry.ui

import com.xiang.ai.todoentry.ai.ParsedTask
import com.xiang.ai.todoentry.ai.TaskImportance
import com.xiang.ai.todoentry.graph.TodoListDto
import com.xiang.ai.todoentry.graph.TodoTaskDto
import com.xiang.ai.todoentry.settings.AppSettings

data class AppUiState(
    val input: String = "",
    val currentTab: AppTab = AppTab.HOME,
    val currentPage: AppPage = AppPage.MAIN,
    val previewTasks: List<EditableTask> = emptyList(),
    val selectedTask: EditableTaskDetail? = null,
    val lastCreatedTitle: String? = null,
    val lastCreatedListName: String? = null,
    val lists: List<TodoListDto> = emptyList(),
    val tasks: List<TodoTaskDto> = emptyList(),
    val selectedListId: String? = null,
    val accountName: String? = null,
    val settings: AppSettings = AppSettings(),
    val hasApiKey: Boolean = false,
    val isBusy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val showSettings: Boolean = false
)

enum class AppTab {
    HOME,
    TASKS,
    PROFILE
}

enum class AppPage {
    MAIN,
    PREVIEW,
    DETAIL,
    VOICE
}

data class EditableTask(
    val title: String = "",
    val body: String = "",
    val dueDateTime: String = "",
    val reminderDateTime: String = "",
    val importance: TaskImportance = TaskImportance.NORMAL,
    val targetListName: String = "",
    val confidence: Float = 0f
) {
    fun toParsedTask(): ParsedTask = ParsedTask(
        title = title,
        body = body.takeIf { it.isNotBlank() },
        dueDateTime = dueDateTime.takeIf { it.isNotBlank() },
        reminderDateTime = reminderDateTime.takeIf { it.isNotBlank() },
        importance = importance,
        targetListName = targetListName.takeIf { it.isNotBlank() },
        confidence = confidence
    )

    companion object {
        fun from(task: ParsedTask): EditableTask = EditableTask(
            title = task.title,
            body = task.body.orEmpty(),
            dueDateTime = task.dueDateTime.orEmpty(),
            reminderDateTime = task.reminderDateTime.orEmpty(),
            importance = task.importance,
            targetListName = task.targetListName.orEmpty(),
            confidence = task.confidence
        )
    }
}

data class EditableTaskDetail(
    val id: String,
    val title: String,
    val body: String = "",
    val status: String? = null,
    val importance: TaskImportance = TaskImportance.NORMAL,
    val dueDateTime: String = "",
    val reminderDateTime: String = ""
) {
    companion object {
        fun from(task: TodoTaskDto): EditableTaskDetail? {
            val id = task.id ?: return null
            return EditableTaskDetail(
                id = id,
                title = task.title,
                body = task.body?.content.orEmpty(),
                status = task.status,
                importance = TaskImportance.from(task.importance),
                dueDateTime = task.dueDateTime?.dateTime.orEmpty(),
                reminderDateTime = task.reminderDateTime?.dateTime.orEmpty()
            )
        }
    }
}

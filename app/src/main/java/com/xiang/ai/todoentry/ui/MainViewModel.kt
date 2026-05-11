package com.xiang.ai.todoentry.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiang.ai.todoentry.ai.OpenAiTaskParser
import com.xiang.ai.todoentry.ai.TaskImportance
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.graph.CreateTodoTaskRequest
import com.xiang.ai.todoentry.graph.GraphClient
import com.xiang.ai.todoentry.graph.GraphException
import com.xiang.ai.todoentry.graph.ItemBody
import com.xiang.ai.todoentry.graph.TodoListDto
import com.xiang.ai.todoentry.graph.TodoTaskDto
import com.xiang.ai.todoentry.graph.UpdateTodoTaskRequest
import com.xiang.ai.todoentry.settings.AppSettings
import com.xiang.ai.todoentry.settings.SettingsRepository
import com.xiang.ai.todoentry.widget.TodayTasksWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context? = null,
    private val graphClient: GraphClient = GraphClient(),
    private val taskParser: OpenAiTaskParser = OpenAiTaskParser()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        hasApiKey = settingsRepository.getApiKey() != null,
                        selectedListId = it.selectedListId ?: settings.defaultListId
                    )
                }
            }
        }
        refreshAccount()
    }

    fun refreshAccount() {
        runBusy {
            val account = authRepository.currentAccount()
            _uiState.update { it.copy(accountName = account?.username) }
            if (account != null) refreshListsInternal()
        }
    }

    fun signIn(activity: Activity) {
        runBusy {
            val account = authRepository.signIn(activity)
            _uiState.update { it.copy(accountName = account.username, status = "Signed in as ${account.username}") }
            refreshListsInternal()
        }
    }

    fun signOut() {
        runBusy {
            authRepository.signOut()
            _uiState.update {
                it.copy(
                    accountName = null,
                    lists = emptyList(),
                    tasks = emptyList(),
                    selectedListId = null,
                    selectedTask = null,
                    status = "Signed out"
                )
            }
            updateWidget()
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab, error = null, status = null) }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, error = null, status = null) }
    }

    fun useExample(text: String) {
        _uiState.update { it.copy(input = text, error = null, status = null) }
    }

    fun parseInput() {
        runBusy {
            val current = _uiState.value
            val input = current.input.trim()
            require(input.isNotBlank()) { "Describe the task first" }
            val apiKey = settingsRepository.getApiKey() ?: throw IllegalStateException("Configure your LLM API key first")
            val settings = settingsRepository.settings.first()
            val parsed = taskParser.parse(input, settings, apiKey)
            val selectedList = chooseList(
                parsed.tasks.firstOrNull()?.targetListName,
                current.lists,
                current.selectedListId ?: settings.defaultListId
            )
            _uiState.update {
                it.copy(
                    previewTasks = parsed.tasks.map(EditableTask::from),
                    selectedListId = selectedList?.id ?: it.selectedListId,
                    currentTab = AppTab.HOME,
                    status = "Parsed ${parsed.tasks.size} task(s). Review before creating."
                )
            }
        }
    }

    fun updateDraft(index: Int, task: EditableTask) {
        _uiState.update { state ->
            state.copy(
                previewTasks = state.previewTasks.mapIndexed { i, current -> if (i == index) task else current },
                error = null,
                status = null
            )
        }
    }

    fun removeDraft(index: Int) {
        _uiState.update { state ->
            state.copy(previewTasks = state.previewTasks.filterIndexed { i, _ -> i != index }, error = null, status = null)
        }
    }

    fun selectList(listId: String) {
        _uiState.update { it.copy(selectedListId = listId, tasks = emptyList()) }
        refreshTasks()
    }

    fun createPreviewTasks() {
        runBusy {
            val current = _uiState.value
            val drafts = current.previewTasks
            require(drafts.isNotEmpty()) { "Parse tasks first" }
            require(drafts.all { it.title.isNotBlank() }) { "Task title is required" }
            val listId = current.selectedListId ?: throw IllegalStateException("Select a To Do list first")
            val token = authRepository.acquireTokenSilent()
            val created = mutableListOf<TodoTaskDto>()
            val remaining = drafts.toMutableList()
            while (remaining.isNotEmpty()) {
                val draft = remaining.first()
                try {
                    val task = graphClient.createTask(token, listId, CreateTodoTaskRequest.from(draft.toParsedTask()))
                    created += task
                    remaining.removeAt(0)
                } catch (throwable: Throwable) {
                    if (created.isNotEmpty()) {
                        val tasks = graphClient.getTasks(token, listId)
                        _uiState.update {
                            it.copy(
                                previewTasks = remaining,
                                tasks = tasks,
                                lastCreatedTitle = created.lastOrNull()?.title,
                                status = "Created ${created.size} task(s). ${remaining.size} task(s) still need retry."
                            )
                        }
                        updateWidget()
                    }
                    throw throwable
                }
            }
            val tasks = graphClient.getTasks(token, listId)
            val listName = current.lists.firstOrNull { it.id == listId }?.displayName ?: "selected list"
            _uiState.update {
                it.copy(
                    input = "",
                    previewTasks = remaining,
                    tasks = tasks,
                    lastCreatedTitle = created.lastOrNull()?.title,
                    lastCreatedListName = listName,
                    currentTab = AppTab.TASKS,
                    status = "Created ${created.size} task(s) in $listName"
                )
            }
            updateWidget()
        }
    }

    fun refreshTasks() {
        runBusy {
            val listId = _uiState.value.selectedListId ?: throw IllegalStateException("Select a To Do list first")
            val token = authRepository.acquireTokenSilent()
            val tasks = graphClient.getTasks(token, listId)
            _uiState.update { it.copy(tasks = tasks, status = "Tasks refreshed") }
            updateWidget()
        }
    }

    fun toggleTaskCompletion(task: TodoTaskDto) {
        runBusy {
            val listId = _uiState.value.selectedListId ?: throw IllegalStateException("Select a To Do list first")
            val taskId = task.id ?: throw IllegalStateException("Task id is missing")
            val completed = task.status == "completed"
            val token = authRepository.acquireTokenSilent()
            val updated = graphClient.updateTask(
                accessToken = token,
                listId = listId,
                taskId = taskId,
                requestBody = UpdateTodoTaskRequest(status = if (completed) "notStarted" else "completed")
            )
            val tasks = graphClient.getTasks(token, listId)
            _uiState.update {
                it.copy(
                    tasks = tasks,
                    selectedTask = it.selectedTask?.takeIf { detail -> detail.id != taskId }
                        ?: EditableTaskDetail.from(updated),
                    status = if (completed) "Task reopened" else "Task completed"
                )
            }
            updateWidget()
        }
    }

    fun deleteTask(task: TodoTaskDto) {
        runBusy {
            val listId = _uiState.value.selectedListId ?: throw IllegalStateException("Select a To Do list first")
            val taskId = task.id ?: throw IllegalStateException("Task id is missing")
            val token = authRepository.acquireTokenSilent()
            graphClient.deleteTask(token, listId, taskId)
            _uiState.update { state ->
                state.copy(
                    tasks = state.tasks.filterNot { it.id == taskId },
                    selectedTask = state.selectedTask?.takeIf { it.id != taskId },
                    status = "Deleted \"${task.title}\""
                )
            }
            updateWidget()
        }
    }

    fun openTaskDetail(task: TodoTaskDto) {
        _uiState.update {
            it.copy(selectedTask = EditableTaskDetail.from(task), currentTab = AppTab.TASKS, error = null, status = null)
        }
    }

    fun closeTaskDetail() {
        _uiState.update { it.copy(selectedTask = null, error = null, status = null) }
    }

    fun updateTaskDetail(detail: EditableTaskDetail) {
        _uiState.update { it.copy(selectedTask = detail, error = null, status = null) }
    }

    fun saveTaskDetail() {
        runBusy {
            val detail = _uiState.value.selectedTask ?: throw IllegalStateException("Select a task first")
            require(detail.title.isNotBlank()) { "Task title is required" }
            val listId = _uiState.value.selectedListId ?: throw IllegalStateException("Select a To Do list first")
            val token = authRepository.acquireTokenSilent()
            val updated = graphClient.updateTask(
                accessToken = token,
                listId = listId,
                taskId = detail.id,
                requestBody = UpdateTodoTaskRequest(
                    title = detail.title.trim(),
                    importance = detail.importance.graphValue,
                    body = ItemBody(content = detail.body)
                )
            )
            val tasks = graphClient.getTasks(token, listId)
            _uiState.update {
                it.copy(
                    tasks = tasks,
                    selectedTask = EditableTaskDetail.from(updated) ?: detail,
                    status = "Task saved"
                )
            }
            updateWidget()
        }
    }

    fun toggleSelectedTaskCompletion() {
        val detail = _uiState.value.selectedTask ?: return
        toggleTaskCompletion(
            TodoTaskDto(
                id = detail.id,
                title = detail.title,
                status = detail.status,
                importance = detail.importance.graphValue,
                body = ItemBody(detail.body)
            )
        )
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show, error = null, status = null, currentTab = if (show) AppTab.PROFILE else it.currentTab) }
    }

    fun saveSettings(baseUrl: String, model: String, defaultListId: String?, apiKey: String?) {
        runBusy {
            settingsRepository.saveSettings(
                AppSettings(
                    llmBaseUrl = baseUrl,
                    llmModel = model,
                    defaultListId = defaultListId
                )
            )
            if (!apiKey.isNullOrBlank()) settingsRepository.saveApiKey(apiKey)
            _uiState.update { it.copy(hasApiKey = settingsRepository.getApiKey() != null, showSettings = false, status = "Settings saved") }
            updateWidget()
        }
    }

    fun clearApiKey() {
        settingsRepository.clearApiKey()
        _uiState.update { it.copy(hasApiKey = false, status = "API key cleared") }
    }

    private suspend fun refreshListsInternal() {
        val token = authRepository.acquireTokenSilent()
        val lists = graphClient.getLists(token)
        _uiState.update { state ->
            val selected = state.selectedListId?.takeIf { id -> lists.any { it.id == id } }
                ?: state.settings.defaultListId?.takeIf { id -> lists.any { it.id == id } }
                ?: lists.firstOrNull()?.id
            state.copy(lists = lists, selectedListId = selected)
        }
        val selectedListId = _uiState.value.selectedListId
        if (selectedListId != null) {
            val tasks = graphClient.getTasks(token, selectedListId)
            _uiState.update { it.copy(tasks = tasks) }
        }
        updateWidget()
    }

    private fun chooseList(targetListName: String?, lists: List<TodoListDto>, fallbackId: String?): TodoListDto? {
        val normalized = targetListName?.trim()?.lowercase()
        return lists.firstOrNull { it.displayName.lowercase() == normalized }
            ?: fallbackId?.let { id -> lists.firstOrNull { it.id == id } }
            ?: lists.firstOrNull()
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null, status = null) }
            runCatching { block() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun updateWidget() {
        appContext?.let(TodayTasksWidgetProvider::refresh)
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is GraphException -> when (statusCode) {
            401 -> "Microsoft sign-in expired. Sign in again."
            429 -> "Microsoft Graph is throttling requests. Try again shortly."
            in 500..599 -> "Microsoft Graph is temporarily unavailable."
            else -> message ?: "Graph request failed."
        }
        else -> message ?: "Something went wrong."
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val settingsRepository: SettingsRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(authRepository, settingsRepository, appContext.applicationContext) as T
        }
    }
}

fun TaskImportance.label(): String = when (this) {
    TaskImportance.LOW -> "Low"
    TaskImportance.NORMAL -> "Normal"
    TaskImportance.HIGH -> "High"
}

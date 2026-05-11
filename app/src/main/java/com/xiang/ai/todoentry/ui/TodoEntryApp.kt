package com.xiang.ai.todoentry.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xiang.ai.todoentry.ai.TaskImportance
import com.xiang.ai.todoentry.graph.TodoListDto
import com.xiang.ai.todoentry.graph.TodoTaskDto

@Composable
fun TodoEntryApp(viewModel: MainViewModel, activity: Activity) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.status) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
        state.status?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { AppTopBar(state) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.currentTab == AppTab.HOME,
                    onClick = { viewModel.selectTab(AppTab.HOME) },
                    icon = { Text("AI") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = state.currentTab == AppTab.TASKS,
                    onClick = { viewModel.selectTab(AppTab.TASKS) },
                    icon = { Text("✓") },
                    label = { Text("任务") }
                )
                NavigationBarItem(
                    selected = state.currentTab == AppTab.PROFILE,
                    onClick = { viewModel.selectTab(AppTab.PROFILE) },
                    icon = { Text("我") },
                    label = { Text("我的") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (state.currentTab) {
            AppTab.HOME -> HomeScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onInputChange = viewModel::updateInput,
                onUseExample = viewModel::useExample,
                onParse = viewModel::parseInput,
                onOpenProfile = { viewModel.selectTab(AppTab.PROFILE) },
                onSignIn = { viewModel.signIn(activity) },
                onDraftChanged = viewModel::updateDraft,
                onRemoveDraft = viewModel::removeDraft,
                onCreate = viewModel::createPreviewTasks
            )
            AppTab.TASKS -> TasksScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onSelectedList = viewModel::selectList,
                onRefresh = viewModel::refreshTasks,
                onToggleComplete = viewModel::toggleTaskCompletion,
                onDelete = viewModel::deleteTask,
                onOpenDetail = viewModel::openTaskDetail,
                onCloseDetail = viewModel::closeTaskDetail,
                onDetailChanged = viewModel::updateTaskDetail,
                onSaveDetail = viewModel::saveTaskDetail,
                onToggleDetail = viewModel::toggleSelectedTaskCompletion
            )
            AppTab.PROFILE -> ProfileScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onSignIn = { viewModel.signIn(activity) },
                onSignOut = viewModel::signOut,
                onSave = viewModel::saveSettings,
                onClearApiKey = viewModel::clearApiKey
            )
        }

        if (state.showSettings) {
            viewModel.selectTab(AppTab.PROFILE)
            viewModel.showSettings(false)
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppUiState,
    modifier: Modifier,
    onInputChange: (String) -> Unit,
    onUseExample: (String) -> Unit,
    onParse: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignIn: () -> Unit,
    onDraftChanged: (Int, EditableTask) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.accountName == null) {
            NoticeCard("需要 Microsoft 登录", "登录后才能读取列表并创建 To Do 任务。") {
                TextButton(onClick = onSignIn) { Text("登录") }
            }
        }
        if (!state.hasApiKey) {
            NoticeCard("需要 LLM API Key", "请在“我的”中配置 DeepSeek 或 OpenAI-compatible 服务。") {
                TextButton(onClick = onOpenProfile) { Text("去配置") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 快速添加", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("例如：明天下午三点提醒我买牛奶，周五提交报告") },
                    minLines = 4
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("明天下午三点提醒我买牛奶", "周五下班前提交周报", "今晚八点整理旅行清单").forEach { example ->
                        AssistChip(onClick = { onUseExample(example) }, label = { Text(example) })
                    }
                }
                Button(
                    onClick = onParse,
                    enabled = !state.isBusy && state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AI 解析任务")
                }
            }
        }
        state.lastCreatedTitle?.let {
            NoticeCard("最近创建", "$it 已创建到 ${state.lastCreatedListName ?: "Microsoft To Do"}。") {}
        }
        if (state.previewTasks.isNotEmpty()) {
            PreviewTasksCard(
                tasks = state.previewTasks,
                isBusy = state.isBusy,
                onTaskChanged = onDraftChanged,
                onRemove = onRemoveDraft,
                onCreate = onCreate
            )
        }
        BusyRow(state.isBusy)
    }
}

@Composable
private fun TasksScreen(
    state: AppUiState,
    modifier: Modifier,
    onSelectedList: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleComplete: (TodoTaskDto) -> Unit,
    onDelete: (TodoTaskDto) -> Unit,
    onOpenDetail: (TodoTaskDto) -> Unit,
    onCloseDetail: () -> Unit,
    onDetailChanged: (EditableTaskDetail) -> Unit,
    onSaveDetail: () -> Unit,
    onToggleDetail: () -> Unit
) {
    Row(modifier = modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListPicker(state.lists, state.selectedListId, onSelectedList)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("任务列表", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onRefresh, enabled = state.accountName != null && state.selectedListId != null && !state.isBusy) {
                    Text("刷新")
                }
            }
            if (state.accountName == null) {
                Text("登录后查看 Microsoft To Do。")
            } else if (state.tasks.isEmpty()) {
                Text("当前列表没有任务。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tasks, key = { it.id ?: it.title }) { task ->
                        TaskRow(task, state.isBusy, onOpenDetail, onToggleComplete, onDelete)
                    }
                }
            }
        }
        state.selectedTask?.let { detail ->
            TaskDetailPanel(
                detail = detail,
                isBusy = state.isBusy,
                modifier = Modifier.weight(1f),
                onClose = onCloseDetail,
                onChanged = onDetailChanged,
                onSave = onSaveDetail,
                onToggleComplete = onToggleDetail
            )
        }
    }
}

@Composable
private fun ProfileScreen(
    state: AppUiState,
    modifier: Modifier,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit,
    onClearApiKey: () -> Unit
) {
    var baseUrl by remember(state.settings.llmBaseUrl) { mutableStateOf(state.settings.llmBaseUrl) }
    var model by remember(state.settings.llmModel) { mutableStateOf(state.settings.llmModel) }
    var apiKey by remember { mutableStateOf("") }
    var defaultListId by remember(state.settings.defaultListId) { mutableStateOf(state.settings.defaultListId.orEmpty()) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Microsoft 账号", style = MaterialTheme.typography.titleLarge)
                Text(state.accountName ?: "未登录")
                if (state.accountName == null) {
                    Button(onClick = onSignIn, enabled = !state.isBusy) { Text("登录 Microsoft") }
                } else {
                    OutlinedButton(onClick = onSignOut, enabled = !state.isBusy) { Text("退出登录") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 配置", style = MaterialTheme.typography.titleLarge)
                Text("自然语言内容会发送到你配置的 LLM 服务。API Key 只保存在本机加密存储中。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("LLM base URL") }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.hasApiKey) "替换 API Key" else "API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                ListPicker(state.lists, defaultListId.ifBlank { state.selectedListId }, onSelected = { defaultListId = it })
                Button(
                    onClick = { onSave(baseUrl, model, defaultListId.ifBlank { null }, apiKey.ifBlank { null }) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存配置")
                }
                if (state.hasApiKey) {
                    TextButton(onClick = onClearApiKey) { Text("清除 API Key") }
                }
            }
        }
    }
}

@Composable
private fun PreviewTasksCard(
    tasks: List<EditableTask>,
    isBusy: Boolean,
    onTaskChanged: (Int, EditableTask) -> Unit,
    onRemove: (Int) -> Unit,
    onCreate: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("创建前确认", style = MaterialTheme.typography.titleLarge)
            tasks.forEachIndexed { index, task ->
                EditableTaskFields(index, task, onTaskChanged, onRemove)
            }
            Button(onClick = onCreate, enabled = !isBusy && tasks.all { it.title.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
                Text("批量创建 ${tasks.size} 个任务")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableTaskFields(
    index: Int,
    task: EditableTask,
    onTaskChanged: (Int, EditableTask) -> Unit,
    onRemove: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("任务 ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemove(index) }) { Text("移除") }
        }
        OutlinedTextField(task.title, { onTaskChanged(index, task.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
        OutlinedTextField(task.body, { onTaskChanged(index, task.copy(body = it)) }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
        OutlinedTextField(task.dueDateTime, { onTaskChanged(index, task.copy(dueDateTime = it)) }, Modifier.fillMaxWidth(), label = { Text("到期时间") }, singleLine = true)
        OutlinedTextField(task.reminderDateTime, { onTaskChanged(index, task.copy(reminderDateTime = it)) }, Modifier.fillMaxWidth(), label = { Text("提醒时间") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = task.importance.label(),
                onValueChange = {},
                label = { Text("重要性") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TaskImportance.entries.forEach { importance ->
                    DropdownMenuItem(
                        text = { Text(importance.label()) },
                        onClick = {
                            onTaskChanged(index, task.copy(importance = importance))
                            expanded = false
                        }
                    )
                }
            }
        }
        Text("AI confidence: ${(task.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TaskRow(
    task: TodoTaskDto,
    isBusy: Boolean,
    onOpenDetail: (TodoTaskDto) -> Unit,
    onToggleComplete: (TodoTaskDto) -> Unit,
    onDelete: (TodoTaskDto) -> Unit
) {
    val completed = task.status == "completed"
    Card(Modifier.fillMaxWidth().clickable { onOpenDetail(task) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(task.title, textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
                task.dueDateTime?.dateTime?.takeIf { it.isNotBlank() }?.let {
                    Text("Due: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            AssistChip(onClick = { onToggleComplete(task) }, enabled = !isBusy && task.id != null, label = { Text(if (completed) "重开" else "完成") })
            TextButton(onClick = { onDelete(task) }, enabled = !isBusy && task.id != null) { Text("删除") }
        }
    }
}

@Composable
private fun TaskDetailPanel(
    detail: EditableTaskDetail,
    isBusy: Boolean,
    modifier: Modifier,
    onClose: () -> Unit,
    onChanged: (EditableTaskDetail) -> Unit,
    onSave: () -> Unit,
    onToggleComplete: () -> Unit
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("任务详情", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text("关闭") }
            }
            OutlinedTextField(detail.title, { onChanged(detail.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
            OutlinedTextField(detail.body, { onChanged(detail.copy(body = it)) }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 4)
            if (detail.dueDateTime.isNotBlank()) Text("到期：${detail.dueDateTime}", style = MaterialTheme.typography.bodySmall)
            if (detail.reminderDateTime.isNotBlank()) Text("提醒：${detail.reminderDateTime}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = !isBusy && detail.title.isNotBlank(), modifier = Modifier.weight(1f)) { Text("保存") }
                OutlinedButton(onClick = onToggleComplete, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text(if (detail.status == "completed") "重新打开" else "完成")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPicker(lists: List<TodoListDto>, selectedListId: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = lists.firstOrNull { it.id == selectedListId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = selected?.displayName ?: "No list loaded",
            onValueChange = {},
            label = { Text("Microsoft To Do list") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.displayName) },
                    onClick = {
                        onSelected(list.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: AppUiState) {
    TopAppBar(
        title = {
            Column {
                Text("AI To Do")
                Text(state.accountName ?: "未登录", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun NoticeCard(title: String, message: String, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}

@Composable
private fun BusyRow(isBusy: Boolean) {
    if (!isBusy) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Working...")
    }
}

package com.xiang.ai.todoentry.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiang.ai.todoentry.ai.TaskImportance
import com.xiang.ai.todoentry.graph.TodoListDto
import com.xiang.ai.todoentry.graph.TodoTaskDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val Ink = Color(0xFF101936)
private val Muted = Color(0xFF6F7890)
private val Panel = Color.White
private val SoftPanel = Color(0xFFF5F6FF)
private val Line = Color(0xFFE7EAF4)
private val Purple = Color(0xFF6257FF)
private val Blue = Color(0xFF4169FF)
private val WorkTag = Color(0xFFEDEBFF)
private val LifeTag = Color(0xFFE8F2FF)

@Composable
fun TodoEntryApp(viewModel: MainViewModel, activity: Activity) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.status) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
        state.status?.let { snackbarHostState.showSnackbar(it) }
    }

    Surface(color = Color(0xFFF8FAFF)) {
        when (state.currentPage) {
            AppPage.PREVIEW -> PreviewPage(
                state = state,
                onBack = viewModel::closePage,
                onDraftChanged = viewModel::updateDraft,
                onRemoveDraft = viewModel::removeDraft,
                onCreate = viewModel::createPreviewTasks,
                snackbarHostState = snackbarHostState
            )
            AppPage.DETAIL -> DetailPage(
                state = state,
                onBack = viewModel::closeTaskDetail,
                onChanged = viewModel::updateTaskDetail,
                onSave = viewModel::saveTaskDetail,
                onToggleComplete = viewModel::toggleSelectedTaskCompletion,
                snackbarHostState = snackbarHostState
            )
            AppPage.MAIN -> MainShell(
                state = state,
                activity = activity,
                snackbarHostState = snackbarHostState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun MainShell(
    state: AppUiState,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel
) {
    Scaffold(
        containerColor = Color(0xFFF8FAFF),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = state.currentTab == AppTab.HOME,
                    onClick = { viewModel.selectTab(AppTab.HOME) },
                    icon = { Text("⌂", fontSize = 20.sp) },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = state.currentTab == AppTab.TASKS,
                    onClick = { viewModel.selectTab(AppTab.TASKS) },
                    icon = { Text("✓", fontSize = 18.sp) },
                    label = { Text("任务") }
                )
                NavigationBarItem(
                    selected = state.currentTab == AppTab.PROFILE,
                    onClick = { viewModel.selectTab(AppTab.PROFILE) },
                    icon = { Text("♡", fontSize = 18.sp) },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        when (state.currentTab) {
            AppTab.HOME -> HomePage(
                state = state,
                padding = padding,
                onInputChange = viewModel::updateInput,
                onUseExample = viewModel::useExample,
                onParse = viewModel::parseInput,
                onOpenAll = { viewModel.selectTab(AppTab.TASKS) },
                onOpenSettings = { viewModel.selectTab(AppTab.PROFILE) }
            )
            AppTab.TASKS -> TaskListPage(
                state = state,
                padding = padding,
                onSelectedList = viewModel::selectList,
                onRefresh = viewModel::refreshTasks,
                onToggleComplete = viewModel::toggleTaskCompletion,
                onDelete = viewModel::deleteTask,
                onOpenDetail = viewModel::openTaskDetail,
                onAdd = { viewModel.selectTab(AppTab.HOME) }
            )
            AppTab.PROFILE -> SettingsPage(
                state = state,
                padding = padding,
                onSignIn = { viewModel.signIn(activity) },
                onSignOut = viewModel::signOut,
                onSave = viewModel::saveSettings,
                onClearApiKey = viewModel::clearApiKey,
                onTestAi = viewModel::testAiConnectivity
            )
        }
    }
}

@Composable
private fun HomePage(
    state: AppUiState,
    padding: PaddingValues,
    onInputChange: (String) -> Unit,
    onUseExample: (String) -> Unit,
    onParse: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HomeHeader(onSettings = onOpenSettings)
        AiInputCard(
            input = state.input,
            isBusy = state.isBusy,
            busyMessage = state.busyMessage,
            onInputChange = onInputChange,
            onUseExample = onUseExample,
            onParse = onParse
        )
        RecentTasksSection(
            tasks = state.tasks.take(4),
            onOpenAll = onOpenAll
        )
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("AI ToDo", color = Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        IconTextButton("⚙", onSettings)
    }
}

@Composable
private fun AiInputCard(
    input: String,
    isBusy: Boolean,
    busyMessage: String?,
    onInputChange: (String) -> Unit,
    onUseExample: (String) -> Unit,
    onParse: () -> Unit
) {
    ElevatedPanel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                placeholder = { Text("用自然语言描述你要做的事...") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SoftPanel).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("示例：", color = Blue, fontSize = 13.sp)
                ExampleRow("明天下午3点开会，准备PPT", onUseExample)
                ExampleRow("周末去超市买菜，记得带雨伞", onUseExample)
            }
            if (isBusy && busyMessage != null) {
                GenerationStatus(message = busyMessage)
            }
            GradientButton(
                text = if (isBusy) "AI 正在生成..." else "✦  AI 生成任务",
                enabled = !isBusy && input.isNotBlank(),
                onClick = onParse
            )
        }
    }
}

@Composable
private fun GenerationStatus(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEFF4FF))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Blue
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(message, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("正在调用 AI 并整理成 Microsoft To Do 任务", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ExampleRow(text: String, onUseExample: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).clickable { onUseExample(text) }.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Blue, fontSize = 14.sp)
        Text("›", color = Blue, fontSize = 22.sp)
    }
}

@Composable
private fun RecentTasksSection(tasks: List<TodoTaskDto>, onOpenAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("最近创建", color = Ink, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenAll) { Text("查看全部  ›") }
        }
        ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
            if (tasks.isEmpty()) {
                Text("暂无任务", color = Muted, modifier = Modifier.padding(18.dp))
            } else {
                Column {
                    tasks.forEachIndexed { index, task ->
                        HomeTaskRow(task)
                        if (index != tasks.lastIndex) DividerLine()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTaskRow(task: TodoTaskDto) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        CompletionCircle(completed = task.status == "completed")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            TaskTimeText(task.dueDateTime?.dateTime)
        }
        if (task.importance == "high") Text("☆", color = Blue, fontSize = 24.sp)
    }
}

@Composable
private fun TaskTimeText(dateTime: String?) {
    dateTime.toDisplayDateTime()?.let {
        Text("▣ $it", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PreviewPage(
    state: AppUiState,
    onBack: () -> Unit,
    onDraftChanged: (Int, EditableTask) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    onCreate: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        containerColor = Color(0xFFF8FAFF),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SimpleTopBar("任务解析预览", onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GradientBubble(state.input.ifBlank { "AI 已生成任务" })
            Text("AI 已为你生成以下任务：", color = Muted)
            ElevatedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.previewTasks.forEachIndexed { index, task ->
                        PreviewTaskItem(index, task, onDraftChanged, onRemoveDraft)
                    }
                    TextButton(onClick = { }) { Text("+ 添加子任务") }
                }
            }
            GradientButton("确认创建 (${state.previewTasks.size})", enabled = !state.isBusy && state.previewTasks.isNotEmpty(), onClick = onCreate)
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("重新编辑") }
        }
    }
}

@Composable
private fun PreviewTaskItem(index: Int, task: EditableTask, onDraftChanged: (Int, EditableTask) -> Unit, onRemoveDraft: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(task.title, { onDraftChanged(index, task.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
                OutlinedTextField(task.body, { onDraftChanged(index, task.copy(body = it)) }, Modifier.fillMaxWidth(), label = { Text("描述") }, minLines = 2)
            }
            TextButton(onClick = { onRemoveDraft(index) }) { Text("移除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            task.dueDateTime.toDisplayDateTime()?.let { Tag(it, SoftPanel) }
            Tag(task.importance.label(), SoftPanel)
        }
    }
}

@Composable
private fun TaskListPage(
    state: AppUiState,
    padding: PaddingValues,
    onSelectedList: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleComplete: (TodoTaskDto) -> Unit,
    onDelete: (TodoTaskDto) -> Unit,
    onOpenDetail: (TodoTaskDto) -> Unit,
    onAdd: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("任务", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRefresh, enabled = !state.isBusy && state.accountName != null) { Text("刷新") }
            }
            ListPicker(state.lists, state.selectedListId, onSelectedList)
            if (state.accountName == null) {
                ElevatedPanel { Text("请先在“我的”页面登录 Microsoft 账号。", color = Muted) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.tasks, key = { it.id ?: it.title }) { task ->
                        FullTaskRow(task, state.isBusy, onOpenDetail, onToggleComplete, onDelete)
                    }
                }
            }
        }
        FloatingAddButton(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 18.dp), onClick = onAdd)
    }
}

@Composable
private fun FullTaskRow(task: TodoTaskDto, isBusy: Boolean, onOpenDetail: (TodoTaskDto) -> Unit, onToggleComplete: (TodoTaskDto) -> Unit, onDelete: (TodoTaskDto) -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onOpenDetail(task) }, verticalAlignment = Alignment.CenterVertically) {
            CompletionCircle(task.status == "completed", Modifier.clickable(enabled = !isBusy) { onToggleComplete(task) })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, textDecoration = if (task.status == "completed") TextDecoration.LineThrough else TextDecoration.None)
                TaskTimeText(task.dueDateTime?.dateTime)
            }
            TextButton(onClick = { onDelete(task) }, enabled = !isBusy) { Text("删除") }
        }
    }
}

@Composable
private fun DetailPage(
    state: AppUiState,
    onBack: () -> Unit,
    onChanged: (EditableTaskDetail) -> Unit,
    onSave: () -> Unit,
    onToggleComplete: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val detail = state.selectedTask
    Scaffold(
        containerColor = Color(0xFFF8FAFF),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SimpleTopBar("", onBack, trailing = "⋮") }
    ) { padding ->
        if (detail == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeader(detail = detail, onChanged = onChanged, onToggleComplete = onToggleComplete)
            DetailPropertyCard(detail = detail, onChanged = onChanged)
            DetailNotesCard(detail = detail, onChanged = onChanged)
        }
        FloatingConfirmButton(Modifier.padding(end = 26.dp, bottom = 26.dp).fillMaxSize(), onSave)
    }
}

@Composable
private fun DetailHeader(detail: EditableTaskDetail, onChanged: (EditableTaskDetail) -> Unit, onToggleComplete: () -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompletionCircle(detail.status == "completed", Modifier.clickable(onClick = onToggleComplete), size = 26)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                detail.title,
                { onChanged(detail.copy(title = it)) },
                Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true
            )
            TextButton(onClick = {
                onChanged(detail.copy(importance = if (detail.importance == TaskImportance.HIGH) TaskImportance.NORMAL else TaskImportance.HIGH))
            }) {
                Text(if (detail.importance == TaskImportance.HIGH) "★" else "☆", fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun DetailPropertyCard(detail: EditableTaskDetail, onChanged: (EditableTaskDetail) -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
        Column {
            DetailDatePickerRow(
                icon = "⏰",
                label = "提醒我",
                value = detail.reminderDateTime,
                includeTime = true,
                emptyText = "添加提醒",
                onValueChange = { onChanged(detail.copy(reminderDateTime = it)) },
                onClear = { onChanged(detail.copy(reminderDateTime = "")) }
            )
            DividerLine()
            DetailDatePickerRow(
                icon = "▣",
                label = "到期",
                value = detail.dueDateTime,
                includeTime = false,
                emptyText = "设置截止日期",
                onValueChange = { onChanged(detail.copy(dueDateTime = it)) },
                onClear = { onChanged(detail.copy(dueDateTime = "")) }
            )
        }
    }
}

@Composable
private fun DetailDatePickerRow(
    icon: String,
    label: String,
    value: String,
    includeTime: Boolean,
    emptyText: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val current = value.toLocalDateTimeOrNull() ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val selectedDate = LocalDate.of(year, month + 1, day)
                        if (includeTime) {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onValueChange(selectedDate.atTime(hour, minute, 0).toString())
                                },
                                current.hour,
                                current.minute,
                                true
                            ).show()
                        } else {
                            onValueChange(selectedDate.atStartOfDay().toString())
                        }
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth
                ).show()
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = Muted, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = if (value.isBlank()) Muted else Blue, fontSize = 13.sp)
            Text(
                value.toDetailDisplayDateTime(includeTime) ?: emptyText,
                color = if (value.isBlank()) Muted else Ink,
                fontSize = 16.sp,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold
            )
        }
        if (value.isNotBlank()) {
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}

@Composable
private fun DetailNotesCard(detail: EditableTaskDetail, onChanged: (EditableTaskDetail) -> Unit) {
    ElevatedPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("备注", color = Muted, fontSize = 13.sp)
            OutlinedTextField(
                detail.body,
                { onChanged(detail.copy(body = it)) },
                Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text("添加备注") }
            )
        }
    }
}

@Composable
private fun SettingsPage(
    state: AppUiState,
    padding: PaddingValues,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSave: (String, String, String?, String?, Boolean) -> Unit,
    onClearApiKey: () -> Unit,
    onTestAi: (String, String, String?) -> Unit
) {
    var baseUrl by remember(state.settings.llmBaseUrl) { mutableStateOf(state.settings.llmBaseUrl) }
    var model by remember(state.settings.llmModel) { mutableStateOf(state.settings.llmModel) }
    var apiKey by remember { mutableStateOf("") }
    var defaultListId by remember(state.settings.defaultListId) { mutableStateOf(state.settings.defaultListId.orEmpty()) }
    var skipConfirmation by remember(state.settings.skipAiCreationConfirmation) {
        mutableStateOf(state.settings.skipAiCreationConfirmation)
    }

    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("设置", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ElevatedPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFFC98B), Color(0xFF6C63FF)))))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.accountName ?: "Microsoft 账户", color = Ink, fontWeight = FontWeight.Bold)
                    Text(if (state.accountName == null) "未连接" else "已连接", color = if (state.accountName == null) Muted else Color(0xFF11A36A), fontSize = 12.sp)
                }
                TextButton(onClick = if (state.accountName == null) onSignIn else onSignOut) {
                    Text(if (state.accountName == null) "登录" else "退出")
                }
            }
        }
        ElevatedPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("默认任务列表", color = Muted, fontSize = 13.sp)
                ListPicker(state.lists, defaultListId.ifBlank { state.selectedListId }, onSelected = { defaultListId = it })
            }
        }
        ElevatedPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 设置", color = Ink, fontWeight = FontWeight.Bold)
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("LLM base URL") }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(if (state.hasApiKey) "替换 API Key" else "API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                SettingSwitch("跳过创建确认", skipConfirmation, onCheckedChange = { skipConfirmation = it })
                OutlinedButton(
                    onClick = { onTestAi(baseUrl, model, apiKey.ifBlank { null }) },
                    enabled = !state.isBusy && (apiKey.isNotBlank() || state.hasApiKey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.busyMessage == "正在测试 AI 连通性...") "正在测试..." else "测试 AI 连通性")
                }
                GradientButton("保存设置", enabled = !state.isBusy, onClick = {
                    onSave(
                        baseUrl,
                        model,
                        defaultListId.ifBlank { null },
                        apiKey.ifBlank { null },
                        skipConfirmation
                    )
                })
                if (state.hasApiKey) TextButton(onClick = onClearApiKey) { Text("清除 API Key") }
            }
        }
        ElevatedPanel { Text("版本 1.0.0", color = Ink) }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null) {
    var value by remember(checked) { mutableStateOf(checked) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink)
        Switch(
            checked = value,
            onCheckedChange = {
                value = it
                onCheckedChange?.invoke(it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, trailing: String? = null) {
    TopAppBar(
        title = { Text(title, color = Ink, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Text("‹", fontSize = 30.sp, color = Ink) } },
        actions = { trailing?.let { Text(it, Modifier.padding(end = 16.dp), color = Ink, fontSize = 24.sp) } }
    )
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
            value = selected?.displayName ?: "我的每日任务",
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(text = { Text(list.displayName) }, onClick = {
                    onSelected(list.id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun ElevatedPanel(contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
private fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFFC7CCDD)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Purple, Blue))),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FloatingAddButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(58.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Purple, Blue))).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text("+", color = Color.White, fontSize = 34.sp)
    }
}

@Composable
private fun FloatingConfirmButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Purple, Blue))).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text("✓", color = Color.White, fontSize = 28.sp)
        }
    }
}

@Composable
private fun CompletionCircle(completed: Boolean, modifier: Modifier = Modifier, size: Int = 22) {
    Box(
        modifier.size(size.dp).clip(CircleShape).border(1.5.dp, if (completed) Blue else Color(0xFF9AA3B8), CircleShape).background(if (completed) Color(0xFFEDEBFF) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (completed) Text("✓", color = Blue, fontSize = 12.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        color = Blue,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun GradientBubble(text: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(Purple, Blue))).padding(14.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun IconTextButton(text: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, fontSize = 20.sp)
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

private fun String?.toDisplayDateTime(): String? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val localDateTime = parseGraphDateTime(raw) ?: return raw.take(16)
    val today = LocalDate.now()
    val date = localDateTime.toLocalDate()
    val dateText = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    val time = localDateTime.toLocalTime()
    val hasExplicitTime = !(time.hour == 0 && time.minute == 0 && time.second == 0)
    return if (hasExplicitTime) {
        "$dateText ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else {
        dateText
    }
}

private fun parseGraphDateTime(value: String): LocalDateTime? =
    runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    try {
        LocalDateTime.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }

private fun String.toDetailDisplayDateTime(includeTime: Boolean): String? {
    val dateTime = toLocalDateTimeOrNull() ?: return null
    val today = LocalDate.now()
    val dateText = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("M月d日"))
    }
    return if (includeTime) {
        "$dateText ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else {
        dateText
    }
}

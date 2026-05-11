package com.xiang.ai.todoentry

import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiang.ai.todoentry.ai.OpenAiTaskParser
import com.xiang.ai.todoentry.ai.ParsedTask
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.graph.CreateTodoTaskRequest
import com.xiang.ai.todoentry.graph.GraphClient
import com.xiang.ai.todoentry.settings.AppSettings
import com.xiang.ai.todoentry.settings.SettingsRepository
import com.xiang.ai.todoentry.ui.theme.AiTodoEntryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WidgetAiEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            AiTodoEntryTheme {
                WidgetAiEntryScreen(
                    onDismiss = { finish() },
                    onParse = ::parseTasks,
                    onCreate = ::createTasks
                )
            }
        }
    }

    private suspend fun parseTasks(input: String): WidgetParsedResult = withContext(Dispatchers.IO) {
        val settingsRepository = SettingsRepository(applicationContext)
        val parser = OpenAiTaskParser()
        val apiKey = settingsRepository.getApiKey() ?: error("请先在 App 的“我的”里配置 API Key")
        val settings = settingsRepository.settings.first()
        val parsed = parser.parse(input, settings, apiKey).tasks.filter { it.title.isNotBlank() }
        require(parsed.isNotEmpty()) { "AI 没有解析出可创建的任务" }
        WidgetParsedResult(parsed, settings)
    }

    private suspend fun createTasks(tasks: List<ParsedTask>): String = withContext(Dispatchers.IO) {
        val settingsRepository = SettingsRepository(applicationContext)
        val authRepository = AuthRepository(applicationContext)
        val graphClient = GraphClient()
        val settings = settingsRepository.settings.first()
        val token = authRepository.acquireTokenSilent()
        val lists = graphClient.getLists(token)
        val listId = settings.defaultListId?.takeIf { id -> lists.any { it.id == id } }
            ?: lists.firstOrNull()?.id
            ?: error("没有可用的 Microsoft To Do 列表")
        tasks.forEach { task ->
            graphClient.createTask(token, listId, CreateTodoTaskRequest.from(task))
        }
        "已创建 ${tasks.size} 个任务"
    }
}

private data class WidgetParsedResult(
    val tasks: List<ParsedTask>,
    val settings: AppSettings
)

@Composable
private fun WidgetAiEntryScreen(
    onDismiss: () -> Unit,
    onParse: suspend (String) -> WidgetParsedResult,
    onCreate: suspend (List<ParsedTask>) -> String
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
    var parsedTasks by remember { mutableStateOf<List<ParsedTask>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(enabled = !isBusy, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .clickable(enabled = false) {},
            color = Color.White,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("○", color = Color(0xFFB9BBC2), fontSize = 34.sp)
                    }
                    TextField(
                        value = input,
                        onValueChange = {
                            input = it
                            parsedTasks = emptyList()
                            message = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("添加任务", fontSize = 22.sp, color = Color(0xFF7B7D85)) },
                        singleLine = true,
                        enabled = !isBusy,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        enabled = input.isNotBlank() && !isBusy,
                        onClick = {
                            isBusy = true
                            message = null
                            scope.launch {
                                runCatching {
                                    val result = onParse(input.trim())
                                    if (result.settings.skipAiCreationConfirmation) {
                                        message = onCreate(result.tasks)
                                        input = ""
                                        keyboard?.hide()
                                        onDismiss()
                                    } else {
                                        parsedTasks = result.tasks
                                        keyboard?.hide()
                                        message = null
                                        isBusy = false
                                    }
                                }.onFailure { throwable ->
                                    message = throwable.message ?: "创建失败"
                                    isBusy = false
                                    val imm = context.getSystemService(InputMethodManager::class.java)
                                    imm?.showSoftInput(null, InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (input.isBlank()) Color(0xFFC9CBD1) else Color(0xFF9EA1A8),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↑", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (parsedTasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("确认创建", color = Color(0xFF172033), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        parsedTasks.forEach { task ->
                            ParsedTaskCard(task)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = !isBusy,
                                onClick = {
                                    parsedTasks = emptyList()
                                    keyboard?.show()
                                }
                            ) {
                                Text("继续编辑")
                            }
                            Button(
                                enabled = !isBusy,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                onClick = {
                                    isBusy = true
                                    message = null
                                    scope.launch {
                                        runCatching {
                                            message = onCreate(parsedTasks)
                                            input = ""
                                            parsedTasks = emptyList()
                                            onDismiss()
                                        }.onFailure { throwable ->
                                            message = throwable.message ?: "创建失败"
                                            isBusy = false
                                        }
                                    }
                                }
                            ) {
                                Text("确认创建", color = Color.White)
                            }
                        }
                    }
                }

                message?.let {
                    Text(
                        text = it,
                        color = if (it.startsWith("已创建")) Color(0xFF107C10) else Color(0xFFC62828),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun ParsedTaskCard(task: ParsedTask) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F7FB), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(task.title, color = Color(0xFF172033), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        task.body?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFF6D7078), fontSize = 13.sp, maxLines = 2)
        }
        task.reminderDateTime.toWidgetDisplayDateTime()?.let {
            Text("提醒 $it", color = Color(0xFF2563EB), fontSize = 13.sp)
        } ?: task.dueDateTime.toWidgetDisplayDateTime()?.let {
            Text("截止 $it", color = Color(0xFF2563EB), fontSize = 13.sp)
        }
    }
}

private fun String?.toWidgetDisplayDateTime(): String? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val dateTime = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return raw.take(16)
    val today = LocalDate.now()
    val dateText = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("M月d日"))
    }
    val time = dateTime.toLocalTime()
    val hasTime = !(time.hour == 0 && time.minute == 0 && time.second == 0)
    return if (hasTime) "$dateText ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}" else dateText
}

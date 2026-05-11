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
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.graph.CreateTodoTaskRequest
import com.xiang.ai.todoentry.graph.GraphClient
import com.xiang.ai.todoentry.settings.SettingsRepository
import com.xiang.ai.todoentry.ui.theme.AiTodoEntryTheme
import com.xiang.ai.todoentry.widget.TodayTasksWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetAiEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            AiTodoEntryTheme {
                WidgetAiEntryScreen(
                    onDismiss = { finish() },
                    onCreate = ::createTasks
                )
            }
        }
    }

    private suspend fun createTasks(input: String): String = withContext(Dispatchers.IO) {
        val settingsRepository = SettingsRepository(applicationContext)
        val authRepository = AuthRepository(applicationContext)
        val graphClient = GraphClient()
        val parser = OpenAiTaskParser()
        val apiKey = settingsRepository.getApiKey() ?: error("请先在 App 的“我的”里配置 API Key")
        val settings = settingsRepository.settings.first()
        val token = authRepository.acquireTokenSilent()
        val lists = graphClient.getLists(token)
        val listId = settings.defaultListId?.takeIf { id -> lists.any { it.id == id } }
            ?: lists.firstOrNull()?.id
            ?: error("没有可用的 Microsoft To Do 列表")
        val parsed = parser.parse(input, settings, apiKey).tasks.filter { it.title.isNotBlank() }
        require(parsed.isNotEmpty()) { "AI 没有解析出可创建的任务" }
        parsed.forEach { task ->
            graphClient.createTask(token, listId, CreateTodoTaskRequest.from(task))
        }
        TodayTasksWidgetProvider.refresh(applicationContext)
        "已创建 ${parsed.size} 个任务"
    }
}

@Composable
private fun WidgetAiEntryScreen(
    onDismiss: () -> Unit,
    onCreate: suspend (String) -> String
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                            message = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("添加任务", fontSize = 24.sp, color = Color(0xFF7B7D85)) },
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
                                    message = onCreate(input.trim())
                                    input = ""
                                    keyboard?.hide()
                                    onDismiss()
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickOption("⌂", "任务")
                    QuickOption("▣", "设置截止日期")
                    QuickOption("♢", "提醒我")
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
private fun QuickOption(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, color = Color(0xFF6D7078), fontSize = 30.sp)
        Text(text, color = Color(0xFF6D7078), fontSize = 22.sp)
    }
}

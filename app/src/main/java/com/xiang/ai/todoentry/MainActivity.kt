package com.xiang.ai.todoentry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.settings.SettingsRepository
import com.xiang.ai.todoentry.ui.MainViewModel
import com.xiang.ai.todoentry.ui.TodoEntryApp
import com.xiang.ai.todoentry.ui.theme.AiTodoEntryTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            authRepository = AuthRepository(this),
            settingsRepository = SettingsRepository(this),
            appContext = applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiTodoEntryTheme {
                TodoEntryApp(
                    viewModel = viewModel,
                    activity = this
                )
            }
        }
        handleWidgetIntent()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent()
    }

    private fun handleWidgetIntent() {
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_AI -> viewModel.openHomeTab()
            OPEN_TASKS -> viewModel.openTaskTab()
            OPEN_PROFILE -> viewModel.openProfileTab()
            OPEN_DETAIL -> {
                viewModel.openTaskTab()
                intent?.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() }?.let(viewModel::openTaskDetailById)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "extra_open"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val OPEN_AI = "ai"
        const val OPEN_TASKS = "tasks"
        const val OPEN_PROFILE = "profile"
        const val OPEN_DETAIL = "detail"
    }
}

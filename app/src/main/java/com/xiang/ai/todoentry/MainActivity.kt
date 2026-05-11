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
    }
}

package com.xiang.ai.todoentry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.xiang.ai.todoentry.MainActivity
import com.xiang.ai.todoentry.R
import com.xiang.ai.todoentry.WidgetAiEntryActivity
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.graph.GraphClient
import com.xiang.ai.todoentry.graph.UpdateTodoTaskRequest
import com.xiang.ai.todoentry.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

open class TodayTasksWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> refresh(context)
            ACTION_STATUS -> updateStatus(context, intent)
            ACTION_IMPORTANCE -> toggleImportance(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    companion object {
        private const val ACTION_REFRESH = "com.xiang.ai.todoentry.widget.REFRESH"
        private const val ACTION_STATUS = "com.xiang.ai.todoentry.widget.STATUS"
        private const val ACTION_IMPORTANCE = "com.xiang.ai.todoentry.widget.IMPORTANCE"
        private const val EXTRA_LIST_ID = "extra_list_id"
        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val EXTRA_IMPORTANT = "extra_important"
        private const val EXTRA_COMPLETED = "extra_completed"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(appContext, TodayTasksWidgetProvider::class.java))
                if (widgetIds.isEmpty()) return@launch

                val result = runCatching {
                    val settingsRepository = SettingsRepository(appContext)
                    val authRepository = AuthRepository(appContext)
                    val graphClient = GraphClient()
                    val settings = settingsRepository.settings.first()
                    val token = authRepository.acquireTokenSilent()
                    val lists = graphClient.getLists(token)
                    val listId = settings.defaultListId?.takeIf { id -> lists.any { it.id == id } }
                        ?: lists.firstOrNull()?.id
                        ?: error("No To Do list")
                    graphClient.getTasks(token, listId)
                        .take(4)
                        .map {
                            WidgetTask(
                                id = it.id.orEmpty(),
                                listId = listId,
                                title = it.title,
                                due = it.dueDateTime?.dateTime.toWidgetDateTime().orEmpty(),
                                important = it.importance == "high",
                                completed = it.status == "completed"
                            )
                        }
                }

                widgetIds.forEach { widgetId ->
                    val views = buildViews(appContext, R.layout.widget_today_tasks, result.getOrNull(), result.exceptionOrNull())
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
                val comboIds = appWidgetManager.getAppWidgetIds(ComponentName(appContext, ComboWidgetProvider::class.java))
                comboIds.forEach { widgetId ->
                    val views = buildViews(appContext, R.layout.widget_combo, result.getOrNull(), result.exceptionOrNull())
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun buildViews(context: Context, layoutId: Int, tasks: List<WidgetTask>?, error: Throwable?): RemoteViews {
            val views = RemoteViews(context.packageName, layoutId)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 0, MainActivity.OPEN_TASKS))
            runCatching { views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context, 1, MainActivity.OPEN_TASKS)) }
            runCatching { views.setOnClickPendingIntent(R.id.widget_add_button, openAiEntryIntent(context, 2)) }
            runCatching { views.setOnClickPendingIntent(R.id.widget_settings, openAppIntent(context, 3, MainActivity.OPEN_PROFILE)) }
            runCatching { views.setOnClickPendingIntent(R.id.widget_refresh, broadcastIntent(context, ACTION_REFRESH, 1000)) }

            val rows = listOf(
                WidgetRow(R.id.widget_task_row_1, R.id.widget_task_check_1, R.id.widget_task_title_1, R.id.widget_task_due_1, R.id.widget_task_star_1),
                WidgetRow(R.id.widget_task_row_2, R.id.widget_task_check_2, R.id.widget_task_title_2, R.id.widget_task_due_2, R.id.widget_task_star_2),
                WidgetRow(R.id.widget_task_row_3, R.id.widget_task_check_3, R.id.widget_task_title_3, R.id.widget_task_due_3, R.id.widget_task_star_3),
                WidgetRow(R.id.widget_task_row_4, R.id.widget_task_check_4, R.id.widget_task_title_4, R.id.widget_task_due_4, R.id.widget_task_star_4)
            )
            rows.forEach { views.setViewVisibility(it.rowId, View.GONE) }
            if (error != null) {
                runCatching {
                    views.setTextViewText(R.id.widget_status, "打开 App 登录")
                    views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                }
                return views
            }
            if (tasks.isNullOrEmpty()) {
                runCatching {
                    views.setTextViewText(R.id.widget_status, "今日没有待办")
                    views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                }
                return views
            }
            runCatching { views.setViewVisibility(R.id.widget_status, View.GONE) }
            tasks.forEachIndexed { index, task ->
                val row = rows[index]
                views.setViewVisibility(row.rowId, View.VISIBLE)
                runCatching { views.setTextViewText(row.titleId, task.title) }
                runCatching {
                    views.setInt(row.titleId, "setPaintFlags", if (task.completed) 17 else 1)
                    views.setInt(row.checkId, "setBackgroundResource", if (task.completed) R.drawable.ms_widget_circle_done else R.drawable.ms_widget_circle_open)
                    views.setTextViewText(row.checkId, if (task.completed) "✓" else "")
                }
                runCatching {
                    if (task.due.isBlank()) {
                        views.setViewVisibility(row.dueId, View.GONE)
                    } else {
                        views.setTextViewText(row.dueId, "▣ ${task.due}")
                        views.setViewVisibility(row.dueId, View.VISIBLE)
                    }
                }
                runCatching { views.setTextViewText(row.starId, if (task.important) "★" else "☆") }
                if (task.id.isNotBlank()) {
                    views.setOnClickPendingIntent(
                        row.checkId,
                        taskIntent(context, ACTION_STATUS, 2000 + index, task)
                    )
                    views.setOnClickPendingIntent(
                        row.starId,
                        taskIntent(context, ACTION_IMPORTANCE, 3000 + index, task)
                    )
                    views.setOnClickPendingIntent(
                        row.rowId,
                        openDetailIntent(context, 4000 + index, task.id)
                    )
                }
            }
            return views
        }

        private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, TodayTasksWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun taskIntent(context: Context, action: String, requestCode: Int, task: WidgetTask): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, TodayTasksWidgetProvider::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_LIST_ID, task.listId)
                    .putExtra(EXTRA_TASK_ID, task.id)
                    .putExtra(EXTRA_IMPORTANT, task.important)
                    .putExtra(EXTRA_COMPLETED, task.completed),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openAppIntent(context: Context, requestCode: Int, destination: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openAiEntryIntent(context: Context, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, WidgetAiEntryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openDetailIntent(context: Context, requestCode: Int, taskId: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DETAIL)
                    .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun updateStatus(context: Context, intent: Intent) {
            val listId = intent.getStringExtra(EXTRA_LIST_ID).orEmpty()
            val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
            val completed = intent.getBooleanExtra(EXTRA_COMPLETED, false)
            if (listId.isBlank() || taskId.isBlank()) return
            val appContext = context.applicationContext
            scope.launch {
                runCatching {
                    val token = AuthRepository(appContext).acquireTokenSilent()
                    GraphClient().updateTask(
                        accessToken = token,
                        listId = listId,
                        taskId = taskId,
                        requestBody = UpdateTodoTaskRequest(status = if (completed) "notStarted" else "completed")
                    )
                }
                refresh(appContext)
            }
        }

        private fun toggleImportance(context: Context, intent: Intent) {
            val listId = intent.getStringExtra(EXTRA_LIST_ID).orEmpty()
            val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
            val important = intent.getBooleanExtra(EXTRA_IMPORTANT, false)
            if (listId.isBlank() || taskId.isBlank()) return
            val appContext = context.applicationContext
            scope.launch {
                runCatching {
                    val token = AuthRepository(appContext).acquireTokenSilent()
                    GraphClient().updateTask(
                        accessToken = token,
                        listId = listId,
                        taskId = taskId,
                        requestBody = UpdateTodoTaskRequest(importance = if (important) "normal" else "high")
                    )
                }
                refresh(appContext)
            }
        }
    }
}

private fun String?.toWidgetDateTime(): String? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val localDateTime = parseWidgetDateTime(raw) ?: return raw.take(16)
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

private fun parseWidgetDateTime(value: String): LocalDateTime? =
    runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()

private data class WidgetRow(
    val rowId: Int,
    val checkId: Int,
    val titleId: Int,
    val dueId: Int,
    val starId: Int
)

private data class WidgetTask(
    val id: String,
    val listId: String,
    val title: String,
    val due: String,
    val important: Boolean,
    val completed: Boolean
)

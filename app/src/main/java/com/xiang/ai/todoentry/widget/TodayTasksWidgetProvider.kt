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
import com.xiang.ai.todoentry.auth.AuthRepository
import com.xiang.ai.todoentry.graph.GraphClient
import com.xiang.ai.todoentry.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TodayTasksWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    companion object {
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
                        .filterNot { it.status == "completed" }
                        .take(4)
                        .map { it.title }
                }

                widgetIds.forEach { widgetId ->
                    val views = buildViews(appContext, result.getOrNull(), result.exceptionOrNull())
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun buildViews(context: Context, titles: List<String>?, error: Throwable?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tasks)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val rows = listOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3, R.id.widget_task_4)
            rows.forEach { views.setViewVisibility(it, View.GONE) }
            if (error != null) {
                views.setTextViewText(R.id.widget_status, "打开 App 登录")
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                return views
            }
            if (titles.isNullOrEmpty()) {
                views.setTextViewText(R.id.widget_status, "今日没有待办")
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                return views
            }
            views.setViewVisibility(R.id.widget_status, View.GONE)
            titles.forEachIndexed { index, title ->
                views.setTextViewText(rows[index], "• $title")
                views.setViewVisibility(rows[index], View.VISIBLE)
            }
            return views
        }
    }
}

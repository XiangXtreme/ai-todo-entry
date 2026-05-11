package com.xiang.ai.todoentry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xiang.ai.todoentry.R
import com.xiang.ai.todoentry.WidgetAiEntryActivity

class AiEntryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_ai_entry_1x2)
            val pendingIntent = openAiEntryIntent(context, widgetId)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_add_button, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun openAiEntryIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            "ai_entry_$widgetId".hashCode(),
            Intent(context, WidgetAiEntryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

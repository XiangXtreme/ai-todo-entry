package com.xiang.ai.todoentry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xiang.ai.todoentry.R
import com.xiang.ai.todoentry.WidgetAiEntryActivity

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(R.id.widget_root, openAiEntryIntent(context))
            views.setOnClickPendingIntent(R.id.widget_add_button, openAiEntryIntent(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun openAiEntryIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            11,
            Intent(context, WidgetAiEntryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

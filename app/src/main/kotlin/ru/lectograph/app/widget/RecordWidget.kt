package ru.lectograph.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.lectograph.app.MainActivity
import ru.lectograph.app.R

open class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, MainActivity::class.java).putExtra("quickRecord", true)
        val start = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ids.forEach { id ->
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.record_widget).apply { setOnClickPendingIntent(R.id.record, start) })
        }
    }
}

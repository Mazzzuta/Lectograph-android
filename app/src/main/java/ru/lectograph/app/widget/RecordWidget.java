package ru.lectograph.app.widget;

import ru.lectograph.app.MainActivity;
import ru.lectograph.app.R;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class RecordWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Intent intent = new Intent(context, MainActivity.class).putExtra("quickRecord", true);
        PendingIntent start = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.record_widget);
            views.setOnClickPendingIntent(R.id.record, start);
            manager.updateAppWidget(id, views);
        }
    }
}

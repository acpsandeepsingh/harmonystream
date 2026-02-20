package com.sansoft.harmonystram;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class PlaybackWidgetProvider extends AppWidgetProvider {

    static final String ACTION_REFRESH_WIDGET = "com.sansoft.harmonystram.REFRESH_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (ACTION_REFRESH_WIDGET.equals(intent.getAction())
                || PlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(context, PlaybackWidgetProvider.class));
            onUpdate(context, manager, ids);
        }
    }

    static void requestRefresh(Context context) {
        Intent refreshIntent = new Intent(context, PlaybackWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        context.sendBroadcast(refreshIntent);
    }

    private RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.playback_widget);

        PlaybackService.PlaybackSnapshot snapshot = PlaybackService.readSnapshot(context);
        views.setTextViewText(R.id.widget_title, snapshot.title);
        views.setTextViewText(R.id.widget_artist, snapshot.artist == null ? "" : snapshot.artist);
        views.setTextViewText(R.id.widget_state, snapshot.playing ? "Playing" : "Paused");

        views.setOnClickPendingIntent(R.id.widget_prev, serviceActionIntent(context, PlaybackService.ACTION_PREVIOUS, 3101));
        views.setOnClickPendingIntent(R.id.widget_play_pause, serviceActionIntent(context, PlaybackService.ACTION_PLAY_PAUSE, 3102));
        views.setOnClickPendingIntent(R.id.widget_next, serviceActionIntent(context, PlaybackService.ACTION_NEXT, 3103));
        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context));

        return views;
    }

    private PendingIntent serviceActionIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    private PendingIntent launchIntent(Context context) {
        Intent intent = new Intent(context, WebAppActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 3104, intent, flags);
    }
}

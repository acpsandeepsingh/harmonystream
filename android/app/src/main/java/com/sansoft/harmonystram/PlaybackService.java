package com.sansoft.harmonystram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class PlaybackService extends Service {

    public static final String CHANNEL_ID = "harmonystream_playback";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_UPDATE_STATE = "com.sansoft.harmonystram.UPDATE_STATE";
    public static final String ACTION_PREVIOUS = "com.sansoft.harmonystram.PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "com.sansoft.harmonystram.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.sansoft.harmonystram.NEXT";
    public static final String ACTION_MEDIA_CONTROL = "com.sansoft.harmonystram.MEDIA_CONTROL";
    public static final String EXTRA_PENDING_MEDIA_ACTION = "pending_media_action";

    private String currentTitle = "HarmonyStream";
    private String currentArtist = "";
    private boolean isPlaying = false;
    private long currentPositionMs = 0;
    private long currentDurationMs = 0;
    private Bitmap artworkBitmap;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            updateNotification();
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_UPDATE_STATE:
                currentTitle = valueOrDefault(intent.getStringExtra("title"), "HarmonyStream");
                currentArtist = valueOrDefault(intent.getStringExtra("artist"), "");
                isPlaying = intent.getBooleanExtra("playing", false);
                currentPositionMs = Math.max(0, intent.getLongExtra("position_ms", 0));
                currentDurationMs = Math.max(0, intent.getLongExtra("duration_ms", 0));

                String artworkBase64 = intent.getStringExtra("artwork_base64");
                if (artworkBase64 != null && !artworkBase64.isEmpty()) {
                    try {
                        byte[] data = Base64.decode(artworkBase64, Base64.DEFAULT);
                        artworkBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    } catch (Exception ignored) {
                    }
                }
                updateNotification();
                break;
            case ACTION_PREVIOUS:
            case ACTION_PLAY_PAUSE:
            case ACTION_NEXT:
                dispatchActionToUi(action);
                break;
            default:
                break;
        }

        return START_STICKY;
    }

    private String valueOrDefault(@Nullable String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private PendingIntent createServiceActionIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private PendingIntent createContentIntent() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 2000, launchIntent, flags);
    }

    private void dispatchActionToUi(String action) {
        Intent intent = new Intent(ACTION_MEDIA_CONTROL);
        intent.setPackage(getPackageName());
        intent.putExtra("action", action);
        sendBroadcast(intent);

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.putExtra(EXTRA_PENDING_MEDIA_ACTION, action);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(launchIntent);
        } catch (Exception ignored) {
            // Broadcast path above still handles active-process control dispatch.
        }
    }

    private void updateNotification() {
        NotificationCompat.Action prevAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                createServiceActionIntent(ACTION_PREVIOUS)
        );

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                createServiceActionIntent(ACTION_PLAY_PAUSE)
        );

        NotificationCompat.Action nextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                createServiceActionIntent(ACTION_NEXT)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(createContentIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(new MediaStyle().setShowActionsInCompactView(0, 1, 2));

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap);
        }

        if (currentDurationMs > 0) {
            builder.setProgress((int) currentDurationMs, (int) Math.min(currentPositionMs, currentDurationMs), false);
        } else {
            builder.setProgress(0, 0, false);
        }

        Notification notification = builder.build();

        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HarmonyStream playback controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

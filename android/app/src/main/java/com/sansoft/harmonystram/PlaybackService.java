package com.sansoft.harmonystram;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class PlaybackService extends Service {

    private static final String TAG = "PlaybackService";

    public static final String CHANNEL_ID = "harmonystream_playback";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_UPDATE_STATE = "com.sansoft.harmonystram.UPDATE_STATE";
    public static final String ACTION_PREVIOUS = "com.sansoft.harmonystram.PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "com.sansoft.harmonystram.PLAY_PAUSE";
    public static final String ACTION_PLAY = "com.sansoft.harmonystram.PLAY";
    public static final String ACTION_PAUSE = "com.sansoft.harmonystram.PAUSE";
    public static final String ACTION_NEXT = "com.sansoft.harmonystram.NEXT";
    public static final String ACTION_SEEK = "com.sansoft.harmonystram.SEEK";
    public static final String ACTION_SET_QUEUE = "com.sansoft.harmonystram.SET_QUEUE";
    public static final String ACTION_MEDIA_CONTROL = "com.sansoft.harmonystram.MEDIA_CONTROL";
    public static final String ACTION_GET_STATE = "com.sansoft.harmonystram.GET_STATE";
    public static final String ACTION_STATE_CHANGED = "com.sansoft.harmonystram.STATE_CHANGED";
    public static final String ACTION_CLEAR_PENDING_MEDIA_ACTION = "com.sansoft.harmonystram.CLEAR_PENDING_MEDIA_ACTION";
    public static final String EXTRA_PENDING_MEDIA_ACTION = "pending_media_action";

    private static final String PREFS_NAME = "playback_service_state";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_POSITION_MS = "position_ms";
    private static final String KEY_DURATION_MS = "duration_ms";
    private static final String KEY_PENDING_MEDIA_ACTION = "pending_media_action";

    public static class PlaybackSnapshot {
        public final String title;
        public final String artist;
        public final boolean playing;
        public final long positionMs;
        public final long durationMs;

        PlaybackSnapshot(String title, String artist, boolean playing, long positionMs, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.playing = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
        }
    }

    public static PlaybackSnapshot readSnapshot(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String title = prefs.getString(KEY_TITLE, "HarmonyStream");
        String artist = prefs.getString(KEY_ARTIST, "");
        boolean playing = prefs.getBoolean(KEY_PLAYING, false);
        long positionMs = Math.max(0, prefs.getLong(KEY_POSITION_MS, 0));
        long durationMs = Math.max(0, prefs.getLong(KEY_DURATION_MS, 0));
        return new PlaybackSnapshot(title == null || title.isEmpty() ? "HarmonyStream" : title,
                artist == null ? "" : artist,
                playing,
                positionMs,
                durationMs);
    }

    private String currentTitle = "HarmonyStream";
    private String currentArtist = "";
    private boolean isPlaying = false;
    private long currentPositionMs = 0;
    private long currentDurationMs = 0;
    private Bitmap artworkBitmap;
    private String pendingMediaAction;
    @Nullable
    private PowerManager.WakeLock playbackWakeLock;
    @Nullable
    private MediaSessionCompat mediaSession;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (isPlaying && currentDurationMs > 0) {
                currentPositionMs = Math.min(currentDurationMs, currentPositionMs + 1000);
                updateNotification();
                broadcastState();
                persistState();
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initWakeLock();
        initMediaSession();
        restoreState();
        updateMediaSessionState();
        syncWakeLock();
        progressHandler.post(progressTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            ensureForegroundIfPlaying();
            updateNotification();
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_UPDATE_STATE:
                applyStateUpdate(intent);
                persistState();
                updateNotification();
                broadcastState();
                updateMediaSessionState();
                syncWakeLock();
                break;
            case ACTION_PREVIOUS:
            case ACTION_PLAY_PAUSE:
            case ACTION_PLAY:
            case ACTION_PAUSE:
            case ACTION_NEXT:
                if (ACTION_PLAY.equals(action)) {
                    isPlaying = true;
                } else if (ACTION_PAUSE.equals(action)) {
                    isPlaying = false;
                } else if (ACTION_PLAY_PAUSE.equals(action)) {
                    isPlaying = !isPlaying;
                }
                persistState();
                updateNotification();
                broadcastState();
                dispatchActionToUi(action, intent);
                updateMediaSessionState();
                syncWakeLock();
                break;
            case ACTION_SEEK:
                currentPositionMs = Math.max(0L, intent.getLongExtra("position_ms", currentPositionMs));
                if (currentDurationMs > 0L) {
                    currentPositionMs = Math.min(currentDurationMs, currentPositionMs);
                }
                persistState();
                updateNotification();
                broadcastState();
                dispatchActionToUi(action, intent);
                updateMediaSessionState();
                syncWakeLock();
                break;
            case ACTION_SET_QUEUE:
                dispatchActionToUi(action, intent);
                break;
            case ACTION_GET_STATE:
                broadcastState();
                break;
            case ACTION_CLEAR_PENDING_MEDIA_ACTION:
                pendingMediaAction = null;
                persistState();
                updateNotification();
                updateMediaSessionState();
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
        Intent launchIntent = new Intent(this, WebAppActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (pendingMediaAction != null && !pendingMediaAction.isEmpty()) {
            launchIntent.putExtra(EXTRA_PENDING_MEDIA_ACTION, pendingMediaAction);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 2000, launchIntent, flags);
    }

    private void dispatchActionToUi(String action, @Nullable Intent sourceIntent) {
        boolean fromWebBridge = sourceIntent != null && "web".equals(sourceIntent.getStringExtra("source"));
        if (!fromWebBridge) {
            pendingMediaAction = action;
            persistState();
        }

        Intent intent = new Intent(ACTION_MEDIA_CONTROL);
        intent.setPackage(getPackageName());
        intent.putExtra("action", action);
        if (sourceIntent != null) {
            if (sourceIntent.hasExtra("position_ms")) {
                intent.putExtra("position_ms", Math.max(0L, sourceIntent.getLongExtra("position_ms", 0L)));
            }
            if (sourceIntent.hasExtra("queue_json")) {
                intent.putExtra("queue_json", valueOrDefault(sourceIntent.getStringExtra("queue_json"), "[]"));
            }
        }

        if (!fromWebBridge) {
            sendBroadcast(intent);
        }
    }

    private void applyStateUpdate(Intent intent) {
        if (intent.hasExtra("title")) {
            currentTitle = valueOrDefault(intent.getStringExtra("title"), currentTitle);
        }
        if (intent.hasExtra("artist")) {
            currentArtist = valueOrDefault(intent.getStringExtra("artist"), currentArtist);
        }
        if (intent.hasExtra("playing")) {
            isPlaying = intent.getBooleanExtra("playing", isPlaying);
        }
        if (intent.hasExtra("position_ms")) {
            currentPositionMs = Math.max(0L, intent.getLongExtra("position_ms", currentPositionMs));
        }
        if (intent.hasExtra("duration_ms")) {
            currentDurationMs = Math.max(0L, intent.getLongExtra("duration_ms", currentDurationMs));
        }

        String artworkBase64 = intent.getStringExtra("artwork_base64");
        if (artworkBase64 != null && !artworkBase64.isEmpty()) {
            try {
                byte[] data = Base64.decode(artworkBase64, Base64.DEFAULT);
                artworkBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignored) {
            }
        }
    }

    private void broadcastState() {
        Intent stateIntent = new Intent(ACTION_STATE_CHANGED);
        stateIntent.setPackage(getPackageName());
        stateIntent.putExtra("title", currentTitle);
        stateIntent.putExtra("artist", currentArtist);
        stateIntent.putExtra("playing", isPlaying);
        stateIntent.putExtra("position_ms", currentPositionMs);
        stateIntent.putExtra("duration_ms", currentDurationMs);
        sendBroadcast(stateIntent);
        PlaybackWidgetProvider.requestRefresh(this);
    }

    private void ensureForegroundIfPlaying() {
        if (!isPlaying) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(currentTitle)
                    .setContentText(currentArtist)
                    .setContentIntent(createContentIntent())
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
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
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(createContentIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(new MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession == null ? null : mediaSession.getSessionToken()))
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                ));

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap);
        }

        if (currentDurationMs > 0) {
            builder.setProgress((int) currentDurationMs, (int) Math.min(currentPositionMs, currentDurationMs), false);
        } else {
            builder.setProgress(0, 0, false);
        }

        Notification notification = builder.build();

        if (!hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission is not granted. Continuing playback service without drawer notification.");
            if (isPlaying) {
                try {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (SecurityException securityException) {
                    Log.e(TAG, "Unable to start foreground playback without notification permission", securityException);
                }
            }
            return;
        }

        try {
            if (isPlaying) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                stopForeground(false);
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException securityException) {
            Log.e(TAG, "Unable to publish playback notification", securityException);
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void persistState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_TITLE, currentTitle)
                .putString(KEY_ARTIST, currentArtist)
                .putBoolean(KEY_PLAYING, isPlaying)
                .putLong(KEY_POSITION_MS, currentPositionMs)
                .putLong(KEY_DURATION_MS, currentDurationMs)
                .putString(KEY_PENDING_MEDIA_ACTION, pendingMediaAction)
                .apply();
        PlaybackWidgetProvider.requestRefresh(this);
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTitle = valueOrDefault(prefs.getString(KEY_TITLE, currentTitle), "HarmonyStream");
        currentArtist = valueOrDefault(prefs.getString(KEY_ARTIST, currentArtist), "");
        isPlaying = prefs.getBoolean(KEY_PLAYING, false);
        currentPositionMs = Math.max(0, prefs.getLong(KEY_POSITION_MS, 0));
        currentDurationMs = Math.max(0, prefs.getLong(KEY_DURATION_MS, 0));
        pendingMediaAction = prefs.getString(KEY_PENDING_MEDIA_ACTION, null);
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

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                intent.setAction(ACTION_PLAY);
                startService(intent);
            }

            @Override
            public void onPause() {
                Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                intent.setAction(ACTION_PAUSE);
                startService(intent);
            }

            @Override
            public void onSkipToNext() {
                Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                intent.setAction(ACTION_NEXT);
                startService(intent);
            }

            @Override
            public void onSkipToPrevious() {
                Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                intent.setAction(ACTION_PREVIOUS);
                startService(intent);
            }

            @Override
            public void onSeekTo(long pos) {
                Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                intent.setAction(ACTION_SEEK);
                intent.putExtra("position_ms", Math.max(0L, pos));
                startService(intent);
            }
        });
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) {
            return;
        }

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_STOP;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        float speed = isPlaying ? 1.0f : 0.0f;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, Math.max(0L, currentPositionMs), speed, SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(0L, currentDurationMs))
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        playbackWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":PlaybackWakeLock");
        playbackWakeLock.setReferenceCounted(false);
    }

    private void syncWakeLock() {
        if (playbackWakeLock == null) {
            return;
        }
        try {
            if (isPlaying && !playbackWakeLock.isHeld()) {
                playbackWakeLock.acquire();
            } else if (!isPlaying && playbackWakeLock.isHeld()) {
                playbackWakeLock.release();
            }
        } catch (RuntimeException runtimeException) {
            Log.w(TAG, "Failed to update playback wake lock state", runtimeException);
        }
    }

    private void releaseWakeLock() {
        if (playbackWakeLock == null || !playbackWakeLock.isHeld()) {
            return;
        }
        try {
            playbackWakeLock.release();
        } catch (RuntimeException runtimeException) {
            Log.w(TAG, "Failed to release playback wake lock", runtimeException);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (isPlaying) {
            scheduleSelfRestart();
        }
    }

    private void scheduleSelfRestart() {
        Intent restartIntent = new Intent(getApplicationContext(), PlaybackService.class);
        restartIntent.setAction(ACTION_GET_STATE);
        PendingIntent restartServiceIntent = PendingIntent.getService(
                this,
                9001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000,
                    restartServiceIntent);
        }
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        releaseWakeLock();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (isPlaying) {
            scheduleSelfRestart();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

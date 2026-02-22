package com.sansoft.harmonystram;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public static final String ACTION_SET_MODE = "com.sansoft.harmonystram.SET_MODE";
    public static final String EXTRA_PENDING_MEDIA_ACTION = "pending_media_action";

    private static final String PREFS_NAME = "playback_service_state";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_POSITION_MS = "position_ms";
    private static final String KEY_DURATION_MS = "duration_ms";

    private static volatile WebView linkedWebView;

    public static void attachWebView(@Nullable WebView webView) {
        linkedWebView = webView;
    }

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
        return new PlaybackSnapshot(
                prefs.getString(KEY_TITLE, "HarmonyStream"),
                prefs.getString(KEY_ARTIST, ""),
                prefs.getBoolean(KEY_PLAYING, false),
                Math.max(0, prefs.getLong(KEY_POSITION_MS, 0)),
                Math.max(0, prefs.getLong(KEY_DURATION_MS, 0))
        );
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private final Runnable progressSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                long pos = Math.max(0, player.getCurrentPosition());
                long dur = Math.max(0, player.getDuration());
                currentPositionMs = pos;
                currentDurationMs = dur;
                sendProgressToWeb(pos, dur);
                broadcastState();
                persistState();
            }
            mainHandler.postDelayed(this, 500L);
        }
    };

    private ExoPlayer player;
    private String currentTitle = "HarmonyStream";
    private String currentArtist = "";
    private long currentPositionMs = 0L;
    private long currentDurationMs = 0L;
    private String currentVideoId;
    private String audioStreamUrl;
    private String videoStreamUrl;
    private boolean videoMode;
    @Nullable
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        restoreState();
        initWakeLock();
        initExtractor();
        initPlayer();
        mainHandler.post(progressSyncRunnable);
    }

    private void initExtractor() {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(DownloaderImpl.create());
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "NewPipe init failed", throwable);
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                syncWakeLock(isPlaying);
                updateNotification();
                broadcastState();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                    currentDurationMs = Math.max(0, player.getDuration());
                }
                if (state == Player.STATE_ENDED) {
                    dispatchActionToUi(ACTION_NEXT);
                }
                updateNotification();
                broadcastState();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Playback error", error);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_PLAY:
                handlePlay(intent);
                break;
            case ACTION_PAUSE:
                if (player != null) player.pause();
                break;
            case ACTION_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
                break;
            case ACTION_SEEK:
                if (player != null) player.seekTo(Math.max(0L, intent.getLongExtra("position_ms", 0L)));
                break;
            case ACTION_SET_MODE:
                boolean enableVideo = intent.getBooleanExtra("video_mode", false);
                switchMode(enableVideo);
                break;
            case ACTION_NEXT:
            case ACTION_PREVIOUS:
            case ACTION_SET_QUEUE:
                dispatchActionToUi(action);
                break;
            case ACTION_GET_STATE:
                broadcastState();
                break;
            default:
                break;
        }

        updateNotification();
        persistState();
        return START_STICKY;
    }

    private void handlePlay(Intent intent) {
        String videoId = intent.getStringExtra("video_id");
        if (videoId == null || videoId.isEmpty()) {
            if (player != null) player.play();
            return;
        }

        currentVideoId = videoId;
        currentTitle = intent.getStringExtra("title") == null ? "HarmonyStream" : intent.getStringExtra("title");
        resolveAndPlay(videoId, 0L);
    }

    private void resolveAndPlay(String videoId, long seekMs) {
        resolverExecutor.execute(() -> {
            try {
                StreamingService yt = ServiceList.YouTube;
                StreamInfo info = StreamInfo.getInfo(yt, videoId);
                List<AudioStream> audioStreams = info.getAudioStreams();
                List<VideoStream> videoStreams = info.getVideoStreams();

                audioStreamUrl = pickItag140(audioStreams);
                if (videoStreams != null && !videoStreams.isEmpty()) {
                    videoStreamUrl = videoStreams.get(0).getContent();
                }

                String selected = videoMode && videoStreamUrl != null ? videoStreamUrl : audioStreamUrl;
                if (selected == null || !selected.contains("googlevideo.com")) {
                    throw new IllegalStateException("No playable googlevideo stream found");
                }

                mainHandler.post(() -> {
                    if (player == null) return;
                    player.setMediaItem(MediaItem.fromUri(selected));
                    player.prepare();
                    if (seekMs > 0) {
                        player.seekTo(seekMs);
                    }
                    player.play();
                    updateNotification();
                    broadcastState();
                });
            } catch (Throwable throwable) {
                Log.e(TAG, "Unable to resolve stream URL", throwable);
            }
        });
    }

    private String pickItag140(List<AudioStream> streams) {
        if (streams == null) return null;
        for (AudioStream stream : streams) {
            if (stream != null && stream.getItag() == 140 && stream.getContent() != null) {
                return stream.getContent();
            }
        }
        for (AudioStream stream : streams) {
            if (stream != null && stream.getContent() != null && stream.getContent().contains("googlevideo.com")) {
                return stream.getContent();
            }
        }
        return null;
    }

    private void switchMode(boolean enableVideo) {
        if (videoMode == enableVideo) return;
        videoMode = enableVideo;
        long ts = player == null ? 0L : player.getCurrentPosition();
        String url = enableVideo ? videoStreamUrl : audioStreamUrl;
        if (url == null && currentVideoId != null) {
            resolveAndPlay(currentVideoId, ts);
            return;
        }
        if (player != null && url != null) {
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.seekTo(ts);
            player.play();
        }
    }

    private void sendProgressToWeb(long pos, long dur) {
        WebView webView = linkedWebView;
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript("window.updateProgress(" + pos + "," + dur + ")", null));
    }

    private void dispatchActionToUi(String action) {
        Intent intent = new Intent(ACTION_MEDIA_CONTROL);
        intent.setPackage(getPackageName());
        intent.putExtra("action", action);
        sendBroadcast(intent);
    }

    private void broadcastState() {
        Intent stateIntent = new Intent(ACTION_STATE_CHANGED);
        stateIntent.setPackage(getPackageName());
        stateIntent.putExtra("title", currentTitle);
        stateIntent.putExtra("artist", currentArtist);
        stateIntent.putExtra("playing", player != null && player.isPlaying());
        stateIntent.putExtra("position_ms", player == null ? currentPositionMs : Math.max(0, player.getCurrentPosition()));
        stateIntent.putExtra("duration_ms", player == null ? currentDurationMs : Math.max(0, player.getDuration()));
        sendBroadcast(stateIntent);
        PlaybackWidgetProvider.requestRefresh(this);
    }

    private void updateNotification() {
        boolean playing = player != null && player.isPlaying();
        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play",
                createServiceActionIntent(ACTION_PLAY_PAUSE)
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setContentIntent(createContentIntent())
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", createServiceActionIntent(ACTION_PREVIOUS)))
                .addAction(playPauseAction)
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", createServiceActionIntent(ACTION_NEXT)))
                .setStyle(new MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .build();

        if (!hasNotificationPermission()) return;

        if (playing) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent createServiceActionIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private PendingIntent createContentIntent() {
        Intent launchIntent = new Intent(this, WebAppActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 2000, launchIntent, flags);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("HarmonyStream playback controls");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void persistState() {
        boolean playing = player != null && player.isPlaying();
        long position = player == null ? currentPositionMs : Math.max(0L, player.getCurrentPosition());
        long duration = player == null ? currentDurationMs : Math.max(0L, player.getDuration());
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_TITLE, currentTitle)
                .putString(KEY_ARTIST, currentArtist)
                .putBoolean(KEY_PLAYING, playing)
                .putLong(KEY_POSITION_MS, position)
                .putLong(KEY_DURATION_MS, duration)
                .apply();
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTitle = prefs.getString(KEY_TITLE, "HarmonyStream");
        currentArtist = prefs.getString(KEY_ARTIST, "");
        currentPositionMs = Math.max(0, prefs.getLong(KEY_POSITION_MS, 0));
        currentDurationMs = Math.max(0, prefs.getLong(KEY_DURATION_MS, 0));
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) return;
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":PlaybackWakeLock");
        wakeLock.setReferenceCounted(false);
    }

    private void syncWakeLock(boolean playing) {
        if (wakeLock == null) return;
        try {
            if (playing && !wakeLock.isHeld()) wakeLock.acquire();
            if (!playing && wakeLock.isHeld()) wakeLock.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "Wake lock sync failed", e);
        }
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        resolverExecutor.shutdownNow();
        if (player != null) {
            player.release();
            player = null;
        }
        syncWakeLock(false);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package com.sansoft.harmonystram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Binder;
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
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends Service {

    private static final String TAG = "PlaybackService";
    public static final String CHANNEL_ID = "harmonystream_playback";
    public static final int NOTIFICATION_ID = 1001;

    private static final long ENABLED_PLAYBACK_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SEEK_TO;

    public static final String ACTION_UPDATE_STATE               = "com.sansoft.harmonystram.UPDATE_STATE";
    public static final String ACTION_PREVIOUS                   = "com.sansoft.harmonystram.PREVIOUS";
    public static final String ACTION_PLAY_PAUSE                 = "com.sansoft.harmonystram.PLAY_PAUSE";
    public static final String ACTION_PLAY                       = "com.sansoft.harmonystram.PLAY";
    public static final String ACTION_PAUSE                      = "com.sansoft.harmonystram.PAUSE";
    public static final String ACTION_NEXT                       = "com.sansoft.harmonystram.NEXT";
    public static final String ACTION_SEEK                       = "com.sansoft.harmonystram.SEEK";
    public static final String ACTION_SET_QUEUE                  = "com.sansoft.harmonystram.SET_QUEUE";
    public static final String ACTION_SET_INDEX                  = "com.sansoft.harmonystram.SET_INDEX";
    public static final String ACTION_MEDIA_CONTROL              = "com.sansoft.harmonystram.MEDIA_CONTROL";
    public static final String ACTION_GET_STATE                  = "com.sansoft.harmonystram.GET_STATE";
    public static final String ACTION_STATE_CHANGED              = "com.sansoft.harmonystram.STATE_CHANGED";
    public static final String ACTION_CLEAR_PENDING_MEDIA_ACTION = "com.sansoft.harmonystram.CLEAR_PENDING_MEDIA_ACTION";
    public static final String ACTION_SET_MODE                   = "com.sansoft.harmonystram.SET_MODE";
    public static final String ACTION_SEEK_RELATIVE              = "com.sansoft.harmonystram.SEEK_RELATIVE";
    public static final String EXTRA_PENDING_MEDIA_ACTION        = "pending_media_action";

    private static final String PREFS_NAME        = "playback_service_state";
    private static final String KEY_TITLE         = "title";
    private static final String KEY_ARTIST        = "artist";
    private static final String KEY_PLAYING       = "playing";
    private static final String KEY_POSITION_MS   = "position_ms";
    private static final String KEY_DURATION_MS   = "duration_ms";
    private static final String KEY_QUEUE_JSON    = "queue_json";
    private static final String KEY_QUEUE_INDEX   = "queue_index";
    private static final String KEY_THUMBNAIL_URL = "thumbnail_url";
    private static final int    MAX_ARTWORK_PX    = 512;

    private static volatile WebView linkedWebView;

    public static void attachWebView(@Nullable WebView webView) {
        linkedWebView = webView;
    }

    // -------------------------------------------------------------------------
    // PlaybackSnapshot
    // -------------------------------------------------------------------------
    public static class PlaybackSnapshot {
        public final String  title;
        public final String  artist;
        public final boolean playing;
        public final long    positionMs;
        public final long    durationMs;

        PlaybackSnapshot(String title, String artist, boolean playing,
                         long positionMs, long durationMs) {
            this.title      = title;
            this.artist     = artist;
            this.playing    = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
        }
    }

    public static PlaybackSnapshot readSnapshot(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(
                PREFS_NAME, android.content.Context.MODE_PRIVATE);
        return new PlaybackSnapshot(
                p.getString(KEY_TITLE,    "HarmonyStream"),
                p.getString(KEY_ARTIST,   ""),
                p.getBoolean(KEY_PLAYING, false),
                Math.max(0, p.getLong(KEY_POSITION_MS, 0)),
                Math.max(0, p.getLong(KEY_DURATION_MS,  0))
        );
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    private final Handler         mainHandler      = new Handler(Looper.getMainLooper());
    private final IBinder         localBinder      = new LocalBinder();
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkExecutor  = Executors.newSingleThreadExecutor();

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
            if (player != null && player.isPlaying()) {
                mainHandler.postDelayed(this, 500L);
            }
        }
    };

    private ExoPlayer             player;
    private String                currentTitle        = "HarmonyStream";
    private String                currentArtist       = "";
    private long                  currentPositionMs   = 0L;
    private long                  currentDurationMs   = 0L;
    private String                currentVideoId;
    private String                currentThumbnailUrl = "";
    private String                audioStreamUrl;
    @SuppressWarnings("unused")
    private String                videoStreamUrl;
    private boolean               videoMode           = false;
    private boolean               progressLoopRunning;
    private volatile long         pendingPlayRequestedAtMs;

    private final List<QueueItem> playbackQueue     = new ArrayList<>();
    private int                   currentQueueIndex = -1;

    private MediaSessionCompat          mediaSession;
    private MediaSessionConnector       mediaSessionConnector;
    private PlaybackStateCompat.Builder playbackStateBuilder;

    @Nullable private PowerManager.WakeLock wakeLock;
    @Nullable private Bitmap               currentArtworkBitmap;
    @Nullable private Bitmap               placeholderBitmap;
    private           int                  artworkRequestVersion = 0;

    // -------------------------------------------------------------------------
    // Binder / QueueItem
    // -------------------------------------------------------------------------
    public class LocalBinder extends Binder {
        PlaybackService getService() { return PlaybackService.this; }
    }

    private static class QueueItem {
        final String id;
        final String title;
        final String artist;
        final String videoId;
        final String thumbnailUrl;

        QueueItem(String id, String title, String artist,
                  String videoId, String thumbnailUrl) {
            this.id           = id;
            this.title        = title;
            this.artist       = artist;
            this.videoId      = videoId;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        restoreState();
        refreshArtworkAsync(currentThumbnailUrl);
        initWakeLock();
        initExtractor();
        initMediaSession();
        initPlayer();
    }

    private void initExtractor() {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(DownloaderImpl.create());
            }
        } catch (Throwable t) {
            Log.w(TAG, "NewPipe init failed", t);
        }
    }

    // -------------------------------------------------------------------------
    // FIX #1: initMediaSession + initPlayer use TimelineQueueNavigator so
    // next/previous work from Bluetooth remotes and the notification.
    // -------------------------------------------------------------------------
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Only play/pause/seek here; next/prev are handled by the QueueNavigator.
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()  { if (player != null) player.play(); }
            @Override public void onPause() { if (player != null) player.pause(); }
            @Override public void onSeekTo(long pos) {
                if (player != null) player.seekTo(Math.max(0L, pos));
            }
        });

        mediaSession.setActive(true);
        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(ENABLED_PLAYBACK_ACTIONS);
    }

    private void initPlayer() {
        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(audioAttrs, true);

        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);
        mediaSessionConnector.setEnabledPlaybackActions(ENABLED_PLAYBACK_ACTIONS);

        // FIX #1: Register a QueueNavigator on the connector.
        // The connector intercepts KEYCODE_MEDIA_NEXT / PREVIOUS from Bluetooth
        // and the notification and routes them here, calling our handleSkip().
        // NOTE: onSkipToNext(Player) and onSkipToPrevious(Player) are the
        // correct single-argument signatures for ExoPlayer 2.17+.
        mediaSessionConnector.setQueueNavigator(new TimelineQueueNavigator(mediaSession) {
            @Override
            public long getSupportedQueueNavigatorActions(Player p) {
                return PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                     | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                     | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
            }

            @Override
            public void onSkipToNext(Player p) {
                handleSkip(+1);
                dispatchActionToUi(ACTION_NEXT);
            }

            @Override
            public void onSkipToPrevious(Player p) {
                handleSkip(-1);
                dispatchActionToUi(ACTION_PREVIOUS);
            }

            @Override
            public MediaDescriptionCompat getMediaDescription(Player p, int windowIndex) {
                if (windowIndex >= 0 && windowIndex < playbackQueue.size()) {
                    QueueItem item = playbackQueue.get(windowIndex);
                    return new MediaDescriptionCompat.Builder()
                            .setTitle(item.title)
                            .setSubtitle(item.artist)
                            .build();
                }
                return new MediaDescriptionCompat.Builder()
                        .setTitle(currentTitle)
                        .setSubtitle(currentArtist)
                        .build();
            }
        });

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                syncWakeLock(isPlaying);
                if (isPlaying) startProgressUpdates(); else stopProgressUpdates();
                updatePlaybackState();
                updateNotification();
                broadcastState();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                    currentDurationMs = Math.max(0, player.getDuration());
                }
                if (state == Player.STATE_ENDED) {
                    handleSkip(+1);
                    dispatchActionToUi(ACTION_NEXT);
                }
                updatePlaybackState();
                updateNotification();
                broadcastState();
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPos,
                                                Player.PositionInfo newPos,
                                                int reason) {
                updatePlaybackState();
                broadcastState();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Playback error", error);
            }
        });
    }

    // -------------------------------------------------------------------------
    // onStartCommand
    // -------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_STICKY;

        switch (intent.getAction()) {
            case ACTION_PLAY:
                handlePlay(intent);
                broadcastState();
                break;
            case ACTION_UPDATE_STATE:
                handleUpdateState(intent);
                break;
            case ACTION_PAUSE:
                if (player != null) player.pause();
                broadcastState();
                break;
            case ACTION_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
                broadcastState();
                break;
            case ACTION_SEEK:
                if (player != null)
                    player.seekTo(Math.max(0L, intent.getLongExtra("position_ms", 0L)));
                break;
            case ACTION_SEEK_RELATIVE:
                if (player != null) {
                    long delta  = intent.getLongExtra("delta_ms", 0L);
                    long dur    = Math.max(0L, player.getDuration());
                    long target = Math.max(0L, player.getCurrentPosition() + delta);
                    if (dur > 0) target = Math.min(dur, target);
                    player.seekTo(target);
                }
                break;
            case ACTION_SET_MODE:
                switchMode(intent.getBooleanExtra("video_mode", false));
                break;
            case ACTION_NEXT:
                handleSkip(+1);
                broadcastState();
                dispatchActionToUi(ACTION_NEXT);
                break;
            case ACTION_PREVIOUS:
                handleSkip(-1);
                broadcastState();
                dispatchActionToUi(ACTION_PREVIOUS);
                break;
            case ACTION_SET_QUEUE:
                handleSetQueue(intent);
                dispatchActionToUi(ACTION_SET_QUEUE);
                break;
            case ACTION_SET_INDEX:
                handleSetIndex(intent);
                dispatchActionToUi(ACTION_SET_INDEX);
                break;
            case ACTION_GET_STATE:
                broadcastState();
                break;
            case ACTION_CLEAR_PENDING_MEDIA_ACTION:
                break;
            default:
                break;
        }

        updateNotification();
        updatePlaybackState();
        persistState();
        return START_STICKY;
    }

    // -------------------------------------------------------------------------
    // FIX #2: switchMode stops ExoPlayer fully when entering video mode so
    // the YouTube iframe has exclusive audio focus (prevents double audio and
    // random pause/resume fighting).
    // -------------------------------------------------------------------------
    private void switchMode(boolean enableVideo) {
        if (videoMode == enableVideo) return;
        videoMode = enableVideo;

        if (enableVideo) {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
            stopProgressUpdates();
            dispatchToLinkedWebView(
                "window.dispatchEvent(new CustomEvent('nativeSetVideoMode',"
                + "{ detail: { enabled: true } }));");
            Log.d(TAG, "Switched to VIDEO mode - ExoPlayer stopped");
        } else {
            dispatchToLinkedWebView(
                "window.dispatchEvent(new CustomEvent('nativeSetVideoMode',"
                + "{ detail: { enabled: false } }));");
            if (currentVideoId != null && !currentVideoId.isEmpty()) {
                resolveAndPlay(currentVideoId, currentPositionMs);
            }
            Log.d(TAG, "Switched to AUDIO mode - ExoPlayer resuming");
        }

        broadcastState();
        updateNotification();
        persistState();
    }

    // -------------------------------------------------------------------------
    // handlePlay
    // -------------------------------------------------------------------------
    private void handlePlay(Intent intent) {
        String videoId = intent.getStringExtra("video_id");
        if (videoId == null || videoId.isEmpty()) {
            if (player != null) {
                player.play();
            } else if (currentVideoId != null && !currentVideoId.isEmpty()) {
                resolveAndPlay(currentVideoId, Math.max(0L, currentPositionMs));
            }
            return;
        }
        currentVideoId = videoId;
        String t = intent.getStringExtra("title");
        currentTitle = (t != null) ? t : "HarmonyStream";
        String a = intent.getStringExtra("artist");
        if (a != null) currentArtist = a;
        currentThumbnailUrl = sanitizeThumbnailUrl(
                intent.getStringExtra("thumbnailUrl"), videoId);
        syncQueueIndexForVideo(videoId);
        pendingPlayRequestedAtMs = System.currentTimeMillis();
        ensureForegroundWithCurrentState();
        broadcastState();
        resolveAndPlay(videoId, 0L);
    }

    private void handleUpdateState(Intent intent) {
        if (intent == null) return;
        String title        = intent.getStringExtra("title");
        String artist       = intent.getStringExtra("artist");
        String thumbnailUrl = intent.getStringExtra("thumbnailUrl");
        if (title != null && !title.trim().isEmpty()) currentTitle = title;
        currentArtist = (artist != null) ? artist : "";
        if (thumbnailUrl != null) {
            currentThumbnailUrl = sanitizeThumbnailUrl(thumbnailUrl, currentVideoId);
            refreshArtworkAsync(currentThumbnailUrl);
        }
        currentPositionMs = Math.max(0L,
                intent.getLongExtra("position_ms", currentPositionMs));
        currentDurationMs = Math.max(0L,
                intent.getLongExtra("duration_ms", currentDurationMs));
        boolean shouldPlay = intent.getBooleanExtra("playing",
                player != null && player.isPlaying());
        if (player != null) {
            if (shouldPlay  && !player.isPlaying()) player.play();
            if (!shouldPlay && player.isPlaying())  player.pause();
        }
        if (player == null || !player.isPlaying()) broadcastState();
    }

    // -------------------------------------------------------------------------
    // FIX #2: Guard resolveAndPlay so ExoPlayer never starts in video mode.
    // -------------------------------------------------------------------------
    private void resolveAndPlay(final String videoId, final long seekMs) {
        if (videoMode) {
            Log.d(TAG, "resolveAndPlay skipped - video mode active for: " + videoId);
            return;
        }
        resolverExecutor.execute(() -> {
            try {
                StreamingService yt = ServiceList.YouTube;
                StreamInfo info     = resolveStreamInfo(yt, videoId);

                List<AudioStream> audioStreams = info.getAudioStreams();
                List<VideoStream> videoStreams = info.getVideoStreams();

                audioStreamUrl = pickItag140(audioStreams);
                if (videoStreams != null && !videoStreams.isEmpty()) {
                    videoStreamUrl = videoStreams.get(0).getContent();
                }

                if (videoMode) {
                    Log.d(TAG, "Video mode enabled during resolve - aborting");
                    return;
                }

                final String selected = audioStreamUrl;
                if (selected == null || !selected.contains("googlevideo.com")) {
                    throw new IllegalStateException("No playable googlevideo stream found");
                }

                mainHandler.post(() -> {
                    if (player == null || videoMode) return;
                    player.setMediaItem(MediaItem.fromUri(selected));
                    player.prepare();
                    if (seekMs > 0) player.seekTo(seekMs);
                    player.play();
                    pendingPlayRequestedAtMs = 0L;
                    refreshArtworkAsync(currentThumbnailUrl);
                    updateNotification();
                    broadcastState();
                });
            } catch (Throwable t) {
                pendingPlayRequestedAtMs = 0L;
                Log.e(TAG, "Unable to resolve stream URL", t);
            }
        });
    }

    private StreamInfo resolveStreamInfo(StreamingService yt,
                                         String videoIdOrUrl) throws Exception {
        try {
            return StreamInfo.getInfo(yt, videoIdOrUrl);
        } catch (Throwable directFailure) {
            String normalized = normalizeYouTubeWatchUrl(videoIdOrUrl);
            if (normalized.equals(videoIdOrUrl)) throw directFailure;
            Log.w(TAG, "Retrying with normalized URL", directFailure);
            return StreamInfo.getInfo(yt, normalized);
        }
    }

    private String normalizeYouTubeWatchUrl(String videoIdOrUrl) {
        if (videoIdOrUrl == null) return "";
        String t = videoIdOrUrl.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) return t;
        return "https://www.youtube.com/watch?v=" + t;
    }

    // -------------------------------------------------------------------------
    // Queue management
    // -------------------------------------------------------------------------
    private void handleSetQueue(Intent intent) {
        if (intent == null) return;
        String queueJson = intent.getStringExtra("queue_json");
        playbackQueue.clear();
        if (queueJson == null || queueJson.trim().isEmpty()) {
            currentQueueIndex = -1;
            return;
        }
        try {
            JSONArray arr = new JSONArray(queueJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                playbackQueue.add(new QueueItem(
                        obj.optString("id"),
                        obj.optString("title"),
                        obj.optString("artist"),
                        obj.optString("videoId"),
                        obj.optString("thumbnailUrl")
                ));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse queue JSON", e);
        }
        int idx = intent.getIntExtra("queue_index", -1);
        currentQueueIndex = (idx >= 0 && idx < playbackQueue.size()) ? idx : 0;
        broadcastState();
    }

    private void handleSetIndex(Intent intent) {
        if (intent == null) return;
        int index = intent.getIntExtra("queue_index", -1);
        if (index < 0 || index >= playbackQueue.size()) return;
        currentQueueIndex   = index;
        QueueItem item      = playbackQueue.get(index);
        currentVideoId      = item.videoId;
        currentTitle        = item.title;
        currentArtist       = item.artist;
        currentThumbnailUrl = sanitizeThumbnailUrl(item.thumbnailUrl, item.videoId);
        pendingPlayRequestedAtMs = System.currentTimeMillis();
        ensureForegroundWithCurrentState();
        broadcastState();
        resolveAndPlay(item.videoId, 0L);
    }

    private void handleSkip(int direction) {
        if (playbackQueue.isEmpty()) return;
        int newIndex = currentQueueIndex + direction;
        if (newIndex < 0 || newIndex >= playbackQueue.size()) return;
        currentQueueIndex   = newIndex;
        QueueItem item      = playbackQueue.get(newIndex);
        currentVideoId      = item.videoId;
        currentTitle        = item.title;
        currentArtist       = item.artist;
        currentThumbnailUrl = sanitizeThumbnailUrl(item.thumbnailUrl, item.videoId);
        pendingPlayRequestedAtMs = System.currentTimeMillis();
        ensureForegroundWithCurrentState();
        broadcastState();
        resolveAndPlay(item.videoId, 0L);
    }

    private void syncQueueIndexForVideo(String videoId) {
        for (int i = 0; i < playbackQueue.size(); i++) {
            if (videoId.equals(playbackQueue.get(i).videoId)) {
                currentQueueIndex = i;
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "HarmonyStream Playback",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Playback controls");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void ensureForegroundWithCurrentState() {
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void updateNotification() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n);
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n);
        }
    }

    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, WebAppActivity.class);
        contentIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                   ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0, contentIntent, piFlags);

        // Using android.R.drawable system icons so the build does not depend on
        // specific project drawable names. Swap these for your own icons if desired.
        int icSmall = android.R.drawable.ic_media_play;
        int icPrev  = android.R.drawable.ic_media_previous;
        int icNext  = android.R.drawable.ic_media_next;
        int icPlay  = android.R.drawable.ic_media_play;
        int icPause = android.R.drawable.ic_media_pause;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icSmall)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(contentPi)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(buildAction(icPrev, "Previous", ACTION_PREVIOUS, 101))
                .addAction(player != null && player.isPlaying()
                        ? buildAction(icPause, "Pause", ACTION_PAUSE, 102)
                        : buildAction(icPlay,  "Play",  ACTION_PLAY,  102))
                .addAction(buildAction(icNext, "Next", ACTION_NEXT, 103));

        if (currentArtworkBitmap != null) {
            builder.setLargeIcon(currentArtworkBitmap);
        } else if (placeholderBitmap != null) {
            builder.setLargeIcon(placeholderBitmap);
        }
        return builder.build();
    }

    private NotificationCompat.Action buildAction(int icon, String title,
                                                   String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                   ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getService(this, requestCode, intent, flags);
        return new NotificationCompat.Action(icon, title, pi);
    }

    // -------------------------------------------------------------------------
    // PlaybackState + MediaSession metadata
    // -------------------------------------------------------------------------
    private void updatePlaybackState() {
        if (mediaSession == null || playbackStateBuilder == null) return;
        long pos  = player != null
                ? Math.max(0, player.getCurrentPosition()) : currentPositionMs;
        int state = (player != null && player.isPlaying())
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED;

        playbackStateBuilder.setState(state, pos, 1.0f)
                .setActions(ENABLED_PLAYBACK_ACTIONS);
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        player != null
                        ? Math.max(0, player.getDuration()) : currentDurationMs);
        if (currentArtworkBitmap != null) {
            meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                    currentArtworkBitmap);
        }
        mediaSession.setMetadata(meta.build());
    }

    // -------------------------------------------------------------------------
    // Broadcast state
    // -------------------------------------------------------------------------
    private void broadcastState() {
        boolean isPlaying = player != null && player.isPlaying();
        long pos = player != null
                ? Math.max(0, player.getCurrentPosition()) : currentPositionMs;
        long dur = player != null
                ? Math.max(0, player.getDuration()) : currentDurationMs;

        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra("title",        currentTitle);
        intent.putExtra("artist",       currentArtist);
        intent.putExtra("playing",      isPlaying);
        intent.putExtra("position_ms",  pos);
        intent.putExtra("duration_ms",  dur);
        intent.putExtra("thumbnailUrl", currentThumbnailUrl);
        intent.putExtra("queue_index",  currentQueueIndex);
        intent.putExtra("queue_length", playbackQueue.size());
        intent.putExtra("video_mode",   videoMode);
        intent.putExtra("pending_play", pendingPlayRequestedAtMs > 0);
        intent.putExtra("event_ts",     System.currentTimeMillis());
        sendBroadcast(intent);

        PlaybackWidgetProvider.requestRefresh(this);
    }

    private void dispatchActionToUi(String action) {
        Intent intent = new Intent(ACTION_MEDIA_CONTROL);
        intent.putExtra("action", action);
        sendBroadcast(intent);
    }

    private void sendProgressToWeb(long posMs, long durMs) {
        dispatchToLinkedWebView(
            "window.updateProgress&&window.updateProgress(" + posMs + "," + durMs + ");");
    }

    private void dispatchToLinkedWebView(final String js) {
        final WebView wv = linkedWebView;
        if (wv == null) return;
        wv.post(() -> wv.evaluateJavascript(js, null));
    }

    // -------------------------------------------------------------------------
    // Progress loop
    // -------------------------------------------------------------------------
    private void startProgressUpdates() {
        if (progressLoopRunning) return;
        progressLoopRunning = true;
        mainHandler.post(progressSyncRunnable);
    }

    private void stopProgressUpdates() {
        progressLoopRunning = false;
        mainHandler.removeCallbacks(progressSyncRunnable);
    }

    // -------------------------------------------------------------------------
    // WakeLock
    // -------------------------------------------------------------------------
    private void initWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + "::WakeLock");
            wakeLock.setReferenceCounted(false);
        }
    }

    private void syncWakeLock(boolean playing) {
        if (wakeLock == null) return;
        if (playing && !wakeLock.isHeld()) {
            wakeLock.acquire(12 * 60 * 60 * 1000L);
        } else if (!playing && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // -------------------------------------------------------------------------
    // Artwork
    // -------------------------------------------------------------------------
    private void refreshArtworkAsync(final String url) {
        if (url == null || url.trim().isEmpty()) {
            if (placeholderBitmap == null) placeholderBitmap = makePlaceholderBitmap();
            currentArtworkBitmap = null;
            return;
        }
        final int ver = ++artworkRequestVersion;
        artworkExecutor.execute(() -> {
            try {
                Bitmap bmp = fetchBitmap(url);
                if (ver != artworkRequestVersion) return;
                if (bmp != null) bmp = scaleBitmap(bmp, MAX_ARTWORK_PX);
                final Bitmap finalBmp = bmp;
                mainHandler.post(() -> {
                    if (ver != artworkRequestVersion) return;
                    currentArtworkBitmap = finalBmp;
                    updateNotification();
                    updatePlaybackState();
                });
            } catch (Throwable t) {
                Log.w(TAG, "Artwork fetch failed", t);
                if (placeholderBitmap == null) placeholderBitmap = makePlaceholderBitmap();
            }
        });
    }

    private Bitmap fetchBitmap(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();
            InputStream is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap scaleBitmap(Bitmap src, int maxPx) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = Math.min((float) maxPx / w, (float) maxPx / h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    private Bitmap makePlaceholderBitmap() {
        Bitmap bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#131d33"));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(96);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("\u266A", 128, 175, p);
        return bmp;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private String sanitizeThumbnailUrl(String url, String videoId) {
        if (url != null && !url.trim().isEmpty()) return url.trim();
        if (videoId != null && !videoId.trim().isEmpty())
            return "https://i.ytimg.com/vi/" + videoId.trim() + "/hqdefault.jpg";
        return "";
    }

    private String pickItag140(List<AudioStream> streams) {
        if (streams == null || streams.isEmpty()) return null;
        for (AudioStream s : streams) {
            if (s.getItag() == 140) return s.getContent();
        }
        return streams.get(0).getContent();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------
    private void persistState() {
        boolean playing = player != null && player.isPlaying();
        long pos = player != null
                ? Math.max(0, player.getCurrentPosition()) : currentPositionMs;
        long dur = player != null
                ? Math.max(0, player.getDuration()) : currentDurationMs;

        SharedPreferences.Editor ed =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        ed.putString(KEY_TITLE,         currentTitle);
        ed.putString(KEY_ARTIST,        currentArtist);
        ed.putBoolean(KEY_PLAYING,      playing);
        ed.putLong(KEY_POSITION_MS,     pos);
        ed.putLong(KEY_DURATION_MS,     dur);
        ed.putString(KEY_THUMBNAIL_URL, currentThumbnailUrl);
        ed.putInt(KEY_QUEUE_INDEX,      currentQueueIndex);
        try {
            JSONArray arr = new JSONArray();
            for (QueueItem item : playbackQueue) {
                JSONObject obj = new JSONObject();
                obj.put("id",           item.id);
                obj.put("title",        item.title);
                obj.put("artist",       item.artist);
                obj.put("videoId",      item.videoId);
                obj.put("thumbnailUrl", item.thumbnailUrl);
                arr.put(obj);
            }
            ed.putString(KEY_QUEUE_JSON, arr.toString());
        } catch (JSONException ignored) {}
        ed.apply();
    }

    private void restoreState() {
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTitle        = p.getString(KEY_TITLE,         "HarmonyStream");
        currentArtist       = p.getString(KEY_ARTIST,        "");
        currentPositionMs   = Math.max(0, p.getLong(KEY_POSITION_MS, 0));
        currentDurationMs   = Math.max(0, p.getLong(KEY_DURATION_MS,  0));
        currentThumbnailUrl = p.getString(KEY_THUMBNAIL_URL, "");
        currentQueueIndex   = p.getInt(KEY_QUEUE_INDEX,      -1);
        String queueJson    = p.getString(KEY_QUEUE_JSON,    null);
        if (queueJson != null) {
            try {
                JSONArray arr = new JSONArray(queueJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    playbackQueue.add(new QueueItem(
                            obj.optString("id"),
                            obj.optString("title"),
                            obj.optString("artist"),
                            obj.optString("videoId"),
                            obj.optString("thumbnailUrl")
                    ));
                }
            } catch (JSONException e) {
                Log.w(TAG, "Could not restore queue", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public accessor via binder
    // -------------------------------------------------------------------------
    public PlaybackSnapshot getCurrentSnapshot() {
        boolean playing = player != null && player.isPlaying();
        long pos = player != null
                ? Math.max(0, player.getCurrentPosition()) : currentPositionMs;
        long dur = player != null
                ? Math.max(0, player.getDuration()) : currentDurationMs;
        return new PlaybackSnapshot(currentTitle, currentArtist, playing, pos, dur);
    }

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------
    @Override
    public IBinder onBind(Intent intent) { return localBinder; }

    @Override
    public void onDestroy() {
        stopProgressUpdates();
        if (player != null) { player.release(); player = null; }
        if (mediaSessionConnector != null) mediaSessionConnector.setPlayer(null);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        resolverExecutor.shutdownNow();
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }
}

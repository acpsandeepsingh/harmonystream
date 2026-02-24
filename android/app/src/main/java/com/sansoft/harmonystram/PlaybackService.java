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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Binder;
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends Service {

    private static final String TAG = "PlaybackService";
    public static final String CHANNEL_ID = "harmonystream_playback";
    public static final int NOTIFICATION_ID = 1001;
    private static final long ENABLED_PLAYBACK_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SEEK_TO;

    public static final String ACTION_UPDATE_STATE = "com.sansoft.harmonystram.UPDATE_STATE";
    public static final String ACTION_PREVIOUS = "com.sansoft.harmonystram.PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "com.sansoft.harmonystram.PLAY_PAUSE";
    public static final String ACTION_PLAY = "com.sansoft.harmonystram.PLAY";
    public static final String ACTION_PAUSE = "com.sansoft.harmonystram.PAUSE";
    public static final String ACTION_NEXT = "com.sansoft.harmonystram.NEXT";
    public static final String ACTION_SEEK = "com.sansoft.harmonystram.SEEK";
    public static final String ACTION_SET_QUEUE = "com.sansoft.harmonystram.SET_QUEUE";
    public static final String ACTION_SET_INDEX = "com.sansoft.harmonystram.SET_INDEX";
    public static final String ACTION_MEDIA_CONTROL = "com.sansoft.harmonystram.MEDIA_CONTROL";
    public static final String ACTION_GET_STATE = "com.sansoft.harmonystram.GET_STATE";
    public static final String ACTION_STATE_CHANGED = "com.sansoft.harmonystram.STATE_CHANGED";
    public static final String ACTION_CLEAR_PENDING_MEDIA_ACTION = "com.sansoft.harmonystram.CLEAR_PENDING_MEDIA_ACTION";
    public static final String ACTION_SET_MODE = "com.sansoft.harmonystram.SET_MODE";
    public static final String ACTION_SEEK_RELATIVE = "com.sansoft.harmonystram.SEEK_RELATIVE";
    public static final String EXTRA_PENDING_MEDIA_ACTION = "pending_media_action";

    private static final String PREFS_NAME = "playback_service_state";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_POSITION_MS = "position_ms";
    private static final String KEY_DURATION_MS = "duration_ms";
    private static final String KEY_QUEUE_JSON = "queue_json";
    private static final String KEY_QUEUE_INDEX = "queue_index";
    private static final String KEY_THUMBNAIL_URL = "thumbnail_url";
    private static final int MAX_ARTWORK_PX = 512;

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
    private final IBinder localBinder = new LocalBinder();
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
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

    private ExoPlayer player;
    private String currentTitle = "HarmonyStream";
    private String currentArtist = "";
    private long currentPositionMs = 0L;
    private long currentDurationMs = 0L;
    private String currentVideoId;
    private String currentThumbnailUrl = "";
    private String audioStreamUrl;
    private String videoStreamUrl;
    private boolean videoMode;
    private boolean progressLoopRunning;
    private volatile long pendingPlayRequestedAtMs;
    private final List<QueueItem> playbackQueue = new ArrayList<>();
    private int currentQueueIndex = -1;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    @Nullable
    private PowerManager.WakeLock wakeLock;
    @Nullable
    private Bitmap currentArtworkBitmap;
    @Nullable
    private Bitmap placeholderBitmap;
    private int artworkRequestVersion = 0;

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    private static class QueueItem {
        final String id;
        final String title;
        final String artist;
        final String videoId;
        final String thumbnailUrl;

        QueueItem(String id, String title, String artist, String videoId, String thumbnailUrl) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.videoId = videoId;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

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

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (player != null) player.play();
            }

            @Override
            public void onPause() {
                if (player != null) player.pause();
            }

            @Override
            public void onSeekTo(long pos) {
                if (player != null) player.seekTo(Math.max(0L, pos));
            }

            @Override
            public void onSkipToNext() {
                handleSkip(+1);
                dispatchActionToUi(ACTION_NEXT);
            }

            @Override
            public void onSkipToPrevious() {
                handleSkip(-1);
                dispatchActionToUi(ACTION_PREVIOUS);
            }
        });
        mediaSession.setActive(true);

        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(ENABLED_PLAYBACK_ACTIONS);
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
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(audioAttributes, true);

        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);
        mediaSessionConnector.setEnabledPlaybackActions(ENABLED_PLAYBACK_ACTIONS);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                syncWakeLock(isPlaying);
                if (isPlaying) {
                    startProgressUpdates();
                } else {
                    stopProgressUpdates();
                }
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
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                updatePlaybackState();
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
                if (player != null) player.seekTo(Math.max(0L, intent.getLongExtra("position_ms", 0L)));
                break;
            case ACTION_SEEK_RELATIVE:
                if (player != null) {
                    long deltaMs = intent.getLongExtra("delta_ms", 0L);
                    long duration = Math.max(0L, player.getDuration());
                    long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
                    if (duration > 0) target = Math.min(duration, target);
                    player.seekTo(target);
                }
                break;
            case ACTION_SET_MODE:
                boolean enableVideo = intent.getBooleanExtra("video_mode", false);
                switchMode(enableVideo);
                break;
            case ACTION_NEXT:
                handleSkip(+1);
                broadcastState();
                dispatchActionToUi(action);
                break;
            case ACTION_PREVIOUS:
                handleSkip(-1);
                broadcastState();
                dispatchActionToUi(action);
                break;
            case ACTION_SET_QUEUE:
                handleSetQueue(intent);
                dispatchActionToUi(action);
                break;
            case ACTION_SET_INDEX:
                handleSetIndex(intent);
                dispatchActionToUi(action);
                break;
            case ACTION_GET_STATE:
                broadcastState();
                break;
            case ACTION_CLEAR_PENDING_MEDIA_ACTION:
                Log.d(TAG, "Cleared pending media action request");
                break;
            default:
                break;
        }

        updateNotification();
        updatePlaybackState();
        persistState();
        return START_STICKY;
    }

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
        currentTitle = intent.getStringExtra("title") == null ? "HarmonyStream" : intent.getStringExtra("title");
        String artist = intent.getStringExtra("artist");
        if (artist != null) {
            currentArtist = artist;
        }
        currentThumbnailUrl = sanitizeThumbnailUrl(intent.getStringExtra("thumbnailUrl"), videoId);
        syncQueueIndexForVideo(videoId);
        pendingPlayRequestedAtMs = System.currentTimeMillis();
        ensureForegroundWithCurrentState();
        broadcastState();
        resolveAndPlay(videoId, 0L);
    }

    private void handleUpdateState(Intent intent) {
        if (intent == null) return;
        String title = intent.getStringExtra("title");
        String artist = intent.getStringExtra("artist");
        String thumbnailUrl = intent.getStringExtra("thumbnailUrl");
        if (title != null && !title.trim().isEmpty()) {
            currentTitle = title;
        }
        currentArtist = artist == null ? "" : artist;
        if (thumbnailUrl != null) {
            currentThumbnailUrl = sanitizeThumbnailUrl(thumbnailUrl, currentVideoId);
            refreshArtworkAsync(currentThumbnailUrl);
        }
        currentPositionMs = Math.max(0L, intent.getLongExtra("position_ms", currentPositionMs));
        currentDurationMs = Math.max(0L, intent.getLongExtra("duration_ms", currentDurationMs));
        boolean shouldPlay = intent.getBooleanExtra("playing", player != null && player.isPlaying());
        if (player != null) {
            if (shouldPlay && !player.isPlaying()) {
                player.play();
            } else if (!shouldPlay && player.isPlaying()) {
                player.pause();
            }
        }
        if (player == null || !player.isPlaying()) {
            broadcastState();
        }
    }

    private void resolveAndPlay(String videoId, long seekMs) {
        resolverExecutor.execute(() -> {
            try {
                StreamingService yt = ServiceList.YouTube;
                StreamInfo info = resolveStreamInfo(yt, videoId);
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
                    pendingPlayRequestedAtMs = 0L;
                    refreshArtworkAsync(currentThumbnailUrl);
                    updateNotification();
                    broadcastState();
                });
            } catch (Throwable throwable) {
                pendingPlayRequestedAtMs = 0L;
                Log.e(TAG, "Unable to resolve stream URL", throwable);
            }
        });
    }

    private StreamInfo resolveStreamInfo(StreamingService yt, String videoIdOrUrl) throws Exception {
        try {
            return StreamInfo.getInfo(yt, videoIdOrUrl);
        } catch (Throwable directFailure) {
            String normalized = normalizeYouTubeWatchUrl(videoIdOrUrl);
            if (normalized.equals(videoIdOrUrl)) {
                throw directFailure;
            }
            Log.w(TAG, "Direct stream resolve failed, retrying with normalized URL", directFailure);
            return StreamInfo.getInfo(yt, normalized);
        }
    }

    private String normalizeYouTubeWatchUrl(String videoIdOrUrl) {
        if (videoIdOrUrl == null) return "";
        String trimmed = videoIdOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://www.youtube.com/watch?v=" + trimmed;
    }

    private void handleSetQueue(Intent intent) {
        if (intent == null) return;
        String queueJson = intent.getStringExtra("queue_json");
        playbackQueue.clear();
        if (queueJson == null || queueJson.trim().isEmpty()) {
            currentQueueIndex = -1;
            persistState();
            return;
        }
        try {
            JSONArray array = new JSONArray(queueJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String videoId = item.optString("videoId", "");
                if (videoId.isEmpty()) continue;
                playbackQueue.add(new QueueItem(
                        item.optString("id", ""),
                        item.optString("title", "HarmonyStream"),
                        item.optString("artist", ""),
                        videoId,
                        sanitizeThumbnailUrl(item.optString("thumbnailUrl", ""), videoId)
                ));
            }
            syncQueueIndexForVideo(currentVideoId);
        } catch (JSONException jsonException) {
            Log.w(TAG, "Invalid queue_json payload", jsonException);
            currentQueueIndex = -1;
        }
        persistState();
    }


    private void handleSetIndex(Intent intent) {
        if (intent == null || playbackQueue.isEmpty()) return;
        int requestedIndex = intent.getIntExtra("index", -1);
        if (requestedIndex < 0 || requestedIndex >= playbackQueue.size()) {
            return;
        }
        QueueItem item = playbackQueue.get(requestedIndex);
        currentQueueIndex = requestedIndex;
        currentVideoId = item.videoId;
        currentTitle = item.title;
        currentArtist = item.artist;
        currentThumbnailUrl = sanitizeThumbnailUrl(item.thumbnailUrl, item.videoId);
        refreshArtworkAsync(currentThumbnailUrl);
        pendingPlayRequestedAtMs = 0L;
        resolveAndPlay(item.videoId, 0L);
        persistState();
    }

    private void handleSkip(int direction) {
        if (playbackQueue.isEmpty()) return;
        int seedIndex = currentQueueIndex;
        if (seedIndex < 0) {
            seedIndex = 0;
            syncQueueIndexForVideo(currentVideoId);
            if (currentQueueIndex >= 0) {
                seedIndex = currentQueueIndex;
            }
        }
        int nextIndex = (seedIndex + direction + playbackQueue.size()) % playbackQueue.size();
        QueueItem item = playbackQueue.get(nextIndex);
        currentQueueIndex = nextIndex;
        currentVideoId = item.videoId;
        currentTitle = item.title;
        currentArtist = item.artist;
        currentThumbnailUrl = sanitizeThumbnailUrl(item.thumbnailUrl, item.videoId);
        refreshArtworkAsync(currentThumbnailUrl);
        pendingPlayRequestedAtMs = System.currentTimeMillis();
        ensureForegroundWithCurrentState();
        resolveAndPlay(item.videoId, 0L);
        broadcastState();
        persistState();
    }

    private void syncQueueIndexForVideo(String videoId) {
        if (videoId == null || playbackQueue.isEmpty()) {
            currentQueueIndex = -1;
            return;
        }
        for (int i = 0; i < playbackQueue.size(); i++) {
            if (videoId.equals(playbackQueue.get(i).videoId)) {
                currentQueueIndex = i;
                return;
            }
        }
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

    private String formatStage2Failure(Throwable throwable) {
        if (throwable == null) return "Extraction error";
        String raw = throwable.getMessage();
        if (raw == null || raw.trim().isEmpty()) {
            raw = throwable.getClass().getSimpleName();
        }
        String message = raw.trim();
        String lower = message.toLowerCase();
        if (lower.startsWith("error")) {
            message = message.substring(5).trim();
        }
        if (message.isEmpty()) {
            message = "Extraction error";
        }
        return message;
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
        boolean isPlaying = player != null && player.isPlaying();
        long currentPosition = player == null ? currentPositionMs : Math.max(0, player.getCurrentPosition());
        long duration = player == null ? currentDurationMs : Math.max(0, player.getDuration());
        stateIntent.putExtra("playing", isPlaying);
        stateIntent.putExtra("isPlaying", isPlaying);
        stateIntent.putExtra("position_ms", currentPosition);
        stateIntent.putExtra("currentPosition", currentPosition);
        stateIntent.putExtra("duration_ms", duration);
        stateIntent.putExtra("duration", duration);
        stateIntent.putExtra("pending_play", pendingPlayRequestedAtMs > 0L);
        stateIntent.putExtra("queue_index", currentQueueIndex);
        stateIntent.putExtra("currentIndex", currentQueueIndex);
        stateIntent.putExtra("queue_length", playbackQueue.size());
        stateIntent.putExtra("video_mode", videoMode);
        stateIntent.putExtra("videoMode", videoMode);
        stateIntent.putExtra("thumbnailUrl", currentThumbnailUrl);
        stateIntent.putExtra("event_ts", System.currentTimeMillis());
        sendBroadcast(stateIntent);
        PlaybackWidgetProvider.requestRefresh(this);
    }

    private void updateNotification() {
        Notification notification = buildPlaybackNotification(player != null && player.isPlaying());
        if (!hasNotificationPermission()) return;
        startForeground(NOTIFICATION_ID, notification);
        if (player == null || !player.isPlaying()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildPlaybackNotification(boolean playing) {
        PendingIntent previousIntent = createServiceActionIntent(ACTION_PREVIOUS);
        PendingIntent playPauseIntent = createServiceActionIntent(ACTION_PLAY_PAUSE);
        PendingIntent nextIntent = createServiceActionIntent(ACTION_NEXT);
        NotificationCompat.Action previousAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousIntent
        );
        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play",
                playPauseIntent
        );
        NotificationCompat.Action nextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setLargeIcon(currentArtworkBitmap != null ? currentArtworkBitmap : getOrCreatePlaceholderBitmap())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(createContentIntent())
                .addAction(previousAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private void ensureForegroundWithCurrentState() {
        if (!hasNotificationPermission()) return;
        Notification notification = buildPlaybackNotification(true);
        startForeground(NOTIFICATION_ID, notification);
    }

    private PendingIntent createServiceActionIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private PendingIntent createSeekRelativeIntent(long deltaMs) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(ACTION_SEEK_RELATIVE);
        intent.putExtra("delta_ms", deltaMs);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, ("seek_" + deltaMs).hashCode(), intent, flags);
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
                .putString(KEY_THUMBNAIL_URL, currentThumbnailUrl)
                .putString(KEY_QUEUE_JSON, serializeQueue())
                .putInt(KEY_QUEUE_INDEX, currentQueueIndex)
                .apply();
    }

    private String serializeQueue() {
        JSONArray array = new JSONArray();
        for (QueueItem item : playbackQueue) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", item.id);
                json.put("title", item.title);
                json.put("artist", item.artist);
                json.put("videoId", item.videoId);
                json.put("thumbnailUrl", item.thumbnailUrl);
                array.put(json);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private void updatePlaybackState() {
        if (mediaSession == null || playbackStateBuilder == null) return;
        long position = player == null ? currentPositionMs : Math.max(0L, player.getCurrentPosition());
        int compatState = PlaybackStateCompat.STATE_NONE;
        if (player != null) {
            switch (player.getPlaybackState()) {
                case Player.STATE_BUFFERING:
                    compatState = PlaybackStateCompat.STATE_BUFFERING;
                    break;
                case Player.STATE_READY:
                    compatState = player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                    break;
                case Player.STATE_ENDED:
                    compatState = PlaybackStateCompat.STATE_STOPPED;
                    break;
                default:
                    compatState = PlaybackStateCompat.STATE_NONE;
            }
        }
        playbackStateBuilder.setState(compatState, position, player != null && player.isPlaying() ? 1f : 0f, System.currentTimeMillis());
        playbackStateBuilder.setActions(ENABLED_PLAYBACK_ACTIONS);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(0L, player == null ? currentDurationMs : player.getDuration()))
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtworkBitmap != null ? currentArtworkBitmap : getOrCreatePlaceholderBitmap())
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, currentQueueIndex >= 0 ? currentQueueIndex + 1 : 0)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, playbackQueue.size())
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void startProgressUpdates() {
        if (progressLoopRunning) return;
        progressLoopRunning = true;
        mainHandler.post(progressSyncRunnable);
    }

    private void stopProgressUpdates() {
        if (!progressLoopRunning) return;
        progressLoopRunning = false;
        mainHandler.removeCallbacks(progressSyncRunnable);
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTitle = prefs.getString(KEY_TITLE, "HarmonyStream");
        currentArtist = prefs.getString(KEY_ARTIST, "");
        currentPositionMs = Math.max(0, prefs.getLong(KEY_POSITION_MS, 0));
        currentDurationMs = Math.max(0, prefs.getLong(KEY_DURATION_MS, 0));
        currentThumbnailUrl = prefs.getString(KEY_THUMBNAIL_URL, "");
        currentQueueIndex = prefs.getInt(KEY_QUEUE_INDEX, -1);
        playbackQueue.clear();
        String queueJson = prefs.getString(KEY_QUEUE_JSON, "[]");
        try {
            JSONArray array = new JSONArray(queueJson == null ? "[]" : queueJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String videoId = item.optString("videoId", "");
                if (videoId.isEmpty()) continue;
                playbackQueue.add(new QueueItem(
                        item.optString("id", ""),
                        item.optString("title", "HarmonyStream"),
                        item.optString("artist", ""),
                        videoId,
                        sanitizeThumbnailUrl(item.optString("thumbnailUrl", ""), videoId)
                ));
            }
        } catch (JSONException ignored) {
            playbackQueue.clear();
            currentQueueIndex = -1;
        }
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
        artworkExecutor.shutdownNow();
        if (mediaSessionConnector != null) {
            mediaSessionConnector.setPlayer(null);
            mediaSessionConnector = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        recycleBitmapSafely(currentArtworkBitmap, true);
        currentArtworkBitmap = null;
        recycleBitmapSafely(placeholderBitmap, true);
        placeholderBitmap = null;
        syncWakeLock(false);
        super.onDestroy();
    }

    private void refreshArtworkAsync(@Nullable String rawThumbnailUrl) {
        final String thumbnailUrl = sanitizeThumbnailUrl(rawThumbnailUrl, currentVideoId);
        currentThumbnailUrl = thumbnailUrl;
        final int requestVersion = ++artworkRequestVersion;
        artworkExecutor.execute(() -> {
            Bitmap loadedBitmap = loadBitmapFromUrl(thumbnailUrl);
            if (loadedBitmap == null) {
                loadedBitmap = getOrCreatePlaceholderBitmap();
            }
            Bitmap finalBitmap = loadedBitmap;
            mainHandler.post(() -> {
                if (requestVersion != artworkRequestVersion) {
                    if (finalBitmap != placeholderBitmap) {
                        recycleBitmapSafely(finalBitmap, true);
                    }
                    return;
                }
                Bitmap previous = currentArtworkBitmap;
                currentArtworkBitmap = finalBitmap;
                if (previous != null && previous != finalBitmap && previous != placeholderBitmap) {
                    recycleBitmapSafely(previous, true);
                }
                updatePlaybackState();
                updateNotification();
            });
        });
    }

    @Nullable
    private Bitmap loadBitmapFromUrl(@Nullable String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) return null;
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            URL url = new URL(thumbnailUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(true);
            connection.setDoInput(true);
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            stream = connection.getInputStream();
            Bitmap decoded = BitmapFactory.decodeStream(stream);
            return scaleBitmap(decoded);
        } catch (Throwable throwable) {
            Log.w(TAG, "Artwork load failed for url=" + thumbnailUrl, throwable);
            return null;
        } finally {
            try {
                if (stream != null) stream.close();
            } catch (Exception ignored) {
            }
            if (connection != null) connection.disconnect();
        }
    }

    private Bitmap getOrCreatePlaceholderBitmap() {
        if (placeholderBitmap != null && !placeholderBitmap.isRecycled()) return placeholderBitmap;
        Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        if (iconBitmap != null) {
            placeholderBitmap = scaleBitmap(iconBitmap);
            return placeholderBitmap;
        }
        Bitmap fallback = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fallback);
        canvas.drawColor(Color.DKGRAY);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(64f);
        canvas.drawText("HS", 72f, 148f, paint);
        placeholderBitmap = fallback;
        return placeholderBitmap;
    }

    @Nullable
    private Bitmap scaleBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return bitmap;
        int maxDimension = Math.max(width, height);
        if (maxDimension <= MAX_ARTWORK_PX) return bitmap;
        float ratio = ((float) MAX_ARTWORK_PX) / maxDimension;
        int newWidth = Math.max(1, Math.round(width * ratio));
        int newHeight = Math.max(1, Math.round(height * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        if (scaled != bitmap) {
            recycleBitmapSafely(bitmap, true);
        }
        return scaled;
    }

    private void recycleBitmapSafely(@Nullable Bitmap bitmap, boolean allowRecycle) {
        if (!allowRecycle || bitmap == null || bitmap == placeholderBitmap || bitmap.isRecycled()) return;
        try {
            bitmap.recycle();
        } catch (Throwable ignored) {
        }
    }

    private String sanitizeThumbnailUrl(@Nullable String thumbnailUrl, @Nullable String videoId) {
        if (thumbnailUrl != null) {
            String trimmed = thumbnailUrl.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        if (videoId != null && !videoId.trim().isEmpty()) {
            return "https://i.ytimg.com/vi/" + videoId.trim() + "/hqdefault.jpg";
        }
        return "";
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null && player.isPlaying()) {
            updateNotification();
            return;
        }
        stopSelf();
    }

    public PlaybackSnapshot getCurrentSnapshot() {
        return new PlaybackSnapshot(
                currentTitle,
                currentArtist,
                player != null && player.isPlaying(),
                player == null ? currentPositionMs : Math.max(0L, player.getCurrentPosition()),
                player == null ? currentDurationMs : Math.max(0L, player.getDuration())
        );
    }
}

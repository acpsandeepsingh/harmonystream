package com.sansoft.harmonystram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.C;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerUtils;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TrackAdapter.OnTrackClickListener {

    private static final int REQUEST_LIBRARY = 6001;
    private static final int REQUEST_PROFILE = 6002;
    private static final int REQUEST_FULLSCREEN_PLAYER = 6003;

    private static final String SOURCE_FIREBASE = "firebase";
    private static final String SOURCE_YOUTUBE = "youtube";
    private static final String SOURCE_YOUTUBE_ALL = "youtube-all";
    private static final int DEFAULT_SEARCH_MAX_RESULTS = 25;
    private static final String GENRE_ALL = "All Genres";

    private static final int REPEAT_MODE_OFF = 0;
    private static final int REPEAT_MODE_ALL = 1;
    private static final int REPEAT_MODE_ONE = 2;

    private ExoPlayer player;
    private PlayerView playerView;
    private YouTubePlayerView youtubePlayerView;
    private Button playPauseButton;
    private Button repeatModeButton;
    private Button queueButton;
    private TextView nowPlayingText;
    private TextView playbackDiagnosticsText;
    private TextView trackListStatus;
    private TextView accountStatus;
    private EditText searchInput;
    private Spinner sourceSpinner;
    private Spinner genreSpinner;
    private Button searchButton;
    private Button libraryButton;
    private Button profileButton;
    private Button previousButton;
    private Button nextButton;
    private View menuSection;
    private View songSection;
    private View playerSection;

    private final List<Song> tracks = new ArrayList<>();
    private final List<Song> allTracks = new ArrayList<>();
    private String selectedGenre = GENRE_ALL;
    private TrackAdapter trackAdapter;
    private int currentIndex = -1;
    private int selectedTrackIndex = -1;
    private int repeatMode = REPEAT_MODE_OFF;
    private final List<Integer> activeQueueTrackIndexes = new ArrayList<>();
    private int currentQueueIndex = -1;
    private YouTubePlayer embeddedYouTubePlayer;
    private String pendingYouTubeVideoId;
    private String activeYouTubeVideoId;
    private boolean youtubeIsPlaying;
    private boolean youtubeFallbackActivated;

    private FirebaseSongRepository firebaseSongRepository;
    private final YouTubeRepository youTubeRepository = new YouTubeRepository();
    private HomeCatalogRepository homeCatalogRepository;
    private final SongRepository searchRepository = new SongRepository() {
        @Override
        public List<SearchResult> search(String query, int maxResults, String source) throws Exception {
            String normalized = source == null ? SOURCE_FIREBASE : source.trim().toLowerCase();
            if (SOURCE_YOUTUBE.equals(normalized) || SOURCE_YOUTUBE_ALL.equals(normalized)) {
                return youTubeRepository.search(query, maxResults, normalized);
            }
            return firebaseSongRepository.search(query, maxResults, SOURCE_FIREBASE);
        }

        @Override
        public Song getVideoDetails(String videoId) throws Exception {
            return firebaseSongRepository.getVideoDetails(videoId);
        }
    };
    private PlaylistStorageRepository playlistStorageRepository;
    private PlaylistSyncManager playlistSyncManager;
    private PlaybackSessionStore playbackSessionStore;
    private NativeUserSessionStore userSessionStore;
    private ExecutorService backgroundExecutor;
    private PlaybackEventLogger playbackEventLogger;
    private PlaybackSoakGateEvaluator playbackSoakGateEvaluator;

    private final Handler stateSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable stateSyncRunnable = new Runnable() {
        @Override
        public void run() {
            syncPlaybackStateToNotification();
            stateSyncHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (!PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;

            String action = intent.getStringExtra("action");
            if (action == null) return;

            switch (action) {
                case PlaybackService.ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case PlaybackService.ACTION_PLAY_PAUSE:
                    togglePlayPause();
                    break;
                case PlaybackService.ACTION_PLAY:
                    setPlaybackEnabled(true);
                    break;
                case PlaybackService.ACTION_PAUSE:
                    setPlaybackEnabled(false);
                    break;
                case PlaybackService.ACTION_NEXT:
                    playNext();
                    break;
                case PlaybackService.ACTION_SEEK:
                    long targetPositionMs = Math.max(0L, intent.getLongExtra("position_ms", 0L));
                    seekPlayback(targetPositionMs);
                    break;
                case PlaybackService.ACTION_SET_QUEUE:
                    // Queue ownership stays in native runtime; this keeps contract compatibility.
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        backgroundExecutor = Executors.newSingleThreadExecutor();
        firebaseSongRepository = new FirebaseSongRepository(this);
        homeCatalogRepository = firebaseSongRepository;
        playlistStorageRepository = new PlaylistStorageRepository(this);
        playlistSyncManager = new PlaylistSyncManager(this);
        playbackSessionStore = new PlaybackSessionStore(this);
        playbackEventLogger = new PlaybackEventLogger(this);
        playbackSoakGateEvaluator = new PlaybackSoakGateEvaluator(playbackEventLogger);

        nowPlayingText = findViewById(R.id.now_playing);
        playbackDiagnosticsText = findViewById(R.id.playback_diagnostics);
        trackListStatus = findViewById(R.id.track_list_status);
        accountStatus = findViewById(R.id.account_status);
        searchInput = findViewById(R.id.search_query_input);
        sourceSpinner = findViewById(R.id.search_source_spinner);
        genreSpinner = findViewById(R.id.genre_filter_spinner);
        searchButton = findViewById(R.id.btn_search);
        playPauseButton = findViewById(R.id.btn_play_pause);
        repeatModeButton = findViewById(R.id.btn_repeat_mode);
        queueButton = findViewById(R.id.btn_queue);
        previousButton = findViewById(R.id.btn_previous);
        nextButton = findViewById(R.id.btn_next);
        Button createPlaylistButton = findViewById(R.id.btn_create_playlist);
        Button addToPlaylistButton = findViewById(R.id.btn_add_to_playlist);
        libraryButton = findViewById(R.id.btn_library);
        profileButton = findViewById(R.id.btn_profile);
        Button fullscreenButton = findViewById(R.id.btn_fullscreen);
        Button playbackDiagnosticsButton = findViewById(R.id.btn_playback_diagnostics);
        RecyclerView trackList = findViewById(R.id.track_list);
        menuSection = findViewById(R.id.menu_section);
        songSection = findViewById(R.id.song_section);
        playerSection = findViewById(R.id.player_section);

        userSessionStore = new NativeUserSessionStore(this);
        updateAccountStatusText();
        runPlaylistSyncQuietly();

        setupSourceSpinner();
        setupGenreSpinner();

        trackList.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(this);
        trackList.setAdapter(trackAdapter);

        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        youtubePlayerView = findViewById(R.id.youtube_player_view);
        initializeEmbeddedYouTubePlayerSafely();
        playerView.setPlayer(player);
        applyTvFocusPolish();
        applyRepeatModeToPlayer();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playPauseButton.setText(isPlaying ? "Pause" : "Play");
                updatePlaybackDiagnostics(isPlaying ? "playing" : "paused");
                logPlaybackEvent("is_playing_changed", eventAttrs("is_playing", String.valueOf(isPlaying)));
                syncPlaybackStateToNotification();
            }

            @Override
            public void onEvents(Player player, Player.Events events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    String state = playbackStateLabel(player.getPlaybackState());
                    logPlaybackEvent("playback_state", eventAttrs("state", state));
                    updatePlaybackDiagnostics("State: " + state);
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (mediaItem == null) return;
                String mediaId = mediaItem.mediaId;
                if (mediaId == null || mediaId.isEmpty()) return;
                try {
                    int newIndex = Integer.parseInt(mediaId);
                    currentIndex = newIndex;
                    selectedTrackIndex = newIndex;
                    currentQueueIndex = activeQueueTrackIndexes.indexOf(newIndex);
                } catch (NumberFormatException ignored) {
                    return;
                }
                updateNowPlayingText();
                updatePlaybackDiagnostics("Queue cursor: " + currentQueueIndex);
                syncPlaybackStateToNotification();
                persistPlaybackSession();
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                String message = error == null || error.getMessage() == null ? "Unknown playback error" : error.getMessage();
                updatePlaybackDiagnostics("Playback error: " + message);
                Toast.makeText(MainActivity.this, "Player error: " + message, Toast.LENGTH_LONG).show();
                logPlaybackEvent("player_error", eventAttrs("message", message));
            }
        });

        searchButton.setOnClickListener(v -> runSearchFromInput());
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        previousButton.setOnClickListener(v -> playPrevious());
        nextButton.setOnClickListener(v -> playNext());
        repeatModeButton.setOnClickListener(v -> cycleRepeatMode());
        queueButton.setOnClickListener(v -> showQueueDialog());
        createPlaylistButton.setOnClickListener(v -> showCreatePlaylistDialog());
        addToPlaylistButton.setOnClickListener(v -> showAddToPlaylistDialog());
        libraryButton.setOnClickListener(v -> openLibraryScreen());
        profileButton.setOnClickListener(v -> openProfileScreen());
        fullscreenButton.setOnClickListener(v -> openFullscreenPlayer());
        playbackDiagnosticsButton.setOnClickListener(v -> showPlaybackDiagnosticsDialog());

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }

        Intent stateIntent = new Intent(this, PlaybackService.class);
        stateIntent.setAction(PlaybackService.ACTION_GET_STATE);
        startService(stateIntent);

        logPlaybackEvent("activity_created", eventAttrs("saved_state", String.valueOf(savedInstanceState != null)));
        updatePlaybackDiagnostics("Lifecycle: created");
        updateRepeatModeButtonLabel();

        boolean restoredSession = restorePlaybackSession();
        if (!restoredSession) {
            loadHomeCatalog();
        } else {
            logPlaybackEvent("restore_session", eventAttrs("status", "restored"));
            updatePlaybackDiagnostics("Lifecycle: restored session");
        }
        handlePendingMediaControlAction(getIntent());
        stateSyncHandler.post(stateSyncRunnable);
        focusMenuSection();
    }

    private void initializeEmbeddedYouTubePlayerSafely() {
        if (youtubePlayerView == null) {
            youtubeFallbackActivated = true;
            updatePlaybackDiagnostics("Embedded YouTube player unavailable");
            logPlaybackEvent("youtube_embed_unavailable", eventAttrs("reason", "view_missing"));
            return;
        }

        getLifecycle().addObserver(youtubePlayerView);
        IFramePlayerOptions iFramePlayerOptions = new IFramePlayerOptions.Builder()
                .controls(0)
                .build();

        try {
            youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
                @Override
                public void onReady(YouTubePlayer youTubePlayer) {
                    embeddedYouTubePlayer = youTubePlayer;
                    if (pendingYouTubeVideoId != null && !pendingYouTubeVideoId.isEmpty()) {
                        YouTubePlayerUtils.loadOrCueVideo(embeddedYouTubePlayer, getLifecycle(), pendingYouTubeVideoId, 0f);
                        youtubeIsPlaying = true;
                        playPauseButton.setText("Pause");
                        pendingYouTubeVideoId = null;
                    }
                }

                @Override
                public void onStateChange(YouTubePlayer youTubePlayer, PlayerConstants.PlayerState state) {
                    if (state == null) return;
                    switch (state) {
                        case PLAYING:
                            youtubeIsPlaying = true;
                            playPauseButton.setText("Pause");
                            syncPlaybackStateToNotification();
                            break;
                        case PAUSED:
                        case ENDED:
                            youtubeIsPlaying = false;
                            playPauseButton.setText("Play");
                            syncPlaybackStateToNotification();
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onError(YouTubePlayer youTubePlayer, PlayerConstants.PlayerError error) {
                    String reason = error == null ? "UNKNOWN" : error.name();
                    updatePlaybackDiagnostics("YouTube error: " + reason);
                    logPlaybackEvent("youtube_player_error", eventAttrs("reason", reason));
                    if (activeYouTubeVideoId != null && !activeYouTubeVideoId.isEmpty()) {
                        fallbackToExternalYouTube(activeYouTubeVideoId, reason);
                    }
                }
            }, true, iFramePlayerOptions);
        } catch (RuntimeException runtimeError) {
            youtubeFallbackActivated = true;
            updatePlaybackDiagnostics("Embedded YouTube unavailable: " + runtimeError.getClass().getSimpleName());
            logPlaybackEvent("youtube_embed_unavailable", eventAttrs("reason", runtimeError.getClass().getSimpleName()));
            youtubePlayerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        logPlaybackEvent("new_intent", eventAttrs("has_pending_action", String.valueOf(intent != null && intent.hasExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION))));
        handlePendingMediaControlAction(intent);
    }


    private void handlePendingMediaControlAction(Intent intent) {
        if (intent == null) return;

        String pendingAction = intent.getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION);
        if (pendingAction == null || pendingAction.isEmpty()) {
            return;
        }

        logPlaybackEvent("pending_media_action", eventAttrs("action", pendingAction));
        updatePlaybackDiagnostics("Notification action: " + pendingAction);
        intent.removeExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION);
        clearPendingMediaAction();

        if (PlaybackService.ACTION_PREVIOUS.equals(pendingAction)) {
            playPrevious();
            return;
        }
        if (PlaybackService.ACTION_NEXT.equals(pendingAction)) {
            playNext();
            return;
        }
        if (PlaybackService.ACTION_PLAY_PAUSE.equals(pendingAction)) {
            togglePlayPause();
        }
    }

    private void clearPendingMediaAction() {
        Intent clearIntent = new Intent(this, PlaybackService.class);
        clearIntent.setAction(PlaybackService.ACTION_CLEAR_PENDING_MEDIA_ACTION);
        startService(clearIntent);
    }

    private boolean restorePlaybackSession() {
        PlaybackSessionStore.PlaybackSession session = playbackSessionStore.load();
        if (!session.hasTracks()) {
            return false;
        }

        tracks.clear();
        tracks.addAll(session.getTracks());
        allTracks.clear();
        allTracks.addAll(session.getTracks());
        refreshGenreFilterOptions(allTracks);
        trackAdapter.setTracks(tracks);

        currentIndex = sanitizeIndex(session.getCurrentIndex(), tracks.size());
        selectedTrackIndex = sanitizeIndex(session.getSelectedIndex(), tracks.size());
        repeatMode = sanitizeRepeatMode(session.getRepeatMode());
        applyRepeatModeToPlayer();
        updateRepeatModeButtonLabel();

        if (currentIndex >= 0) {
            Song currentSong = tracks.get(currentIndex);
            if (isYouTubeExternalTrack(currentSong)) {
                nowPlayingText.setText("Last session track ready in in-app YouTube player: " + currentSong.getTitle());
            } else {
                int mediaWindowIndex;
                if (session.hasQueueSnapshot()) {
                    mediaWindowIndex = applyNativeQueueSnapshot(session.getQueueTrackIndexes(), session.getCurrentQueueIndex(), false);
                } else {
                    mediaWindowIndex = buildAndApplyNativeQueue(currentIndex, false);
                }
                if (mediaWindowIndex >= 0) {
                    long positionMs = Math.max(0L, session.getPositionMs());
                    if (positionMs > 0) {
                        player.seekTo(mediaWindowIndex, positionMs);
                    }
                    if (session.isPlaying()) {
                        nowPlayingText.setText("Resume ready (was playing): " + currentSong.getTitle() + " • " + currentSong.getArtist());
                    } else {
                        nowPlayingText.setText("Ready to resume: " + currentSong.getTitle() + " • " + currentSong.getArtist());
                    }
                } else {
                    nowPlayingText.setText("Session restored. Select a track.");
                }
            }
        } else {
            nowPlayingText.setText("Session restored. Select a track.");
        }

        trackListStatus.setVisibility(View.GONE);
        playPauseButton.setText("Play");
        return true;
    }

    private int sanitizeIndex(int index, int size) {
        return (index >= 0 && index < size) ? index : -1;
    }

    private int sanitizeRepeatMode(int mode) {
        if (mode == REPEAT_MODE_ALL || mode == REPEAT_MODE_ONE) {
            return mode;
        }
        return REPEAT_MODE_OFF;
    }

    private void setupSourceSpinner() {
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{SOURCE_FIREBASE, SOURCE_YOUTUBE, SOURCE_YOUTUBE_ALL}
        );
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);
    }

    private void setupGenreSpinner() {
        if (genreSpinner == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{GENRE_ALL}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genreSpinner.setAdapter(adapter);
        genreSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object value = parent.getItemAtPosition(position);
                selectedGenre = value == null ? GENRE_ALL : value.toString();
                applyGenreFilter();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedGenre = GENRE_ALL;
                applyGenreFilter();
            }
        });
    }

    private void refreshGenreFilterOptions(List<Song> sourceTracks) {
        if (genreSpinner == null) return;
        List<String> genres = new ArrayList<>();
        genres.add(GENRE_ALL);

        Map<String, Integer> genreCount = new LinkedHashMap<>();
        for (Song song : sourceTracks) {
            if (song == null) continue;
            String genre = song.getGenre() == null ? "" : song.getGenre().trim();
            if (genre.isEmpty()) genre = "Music";
            genreCount.put(genre, (genreCount.containsKey(genre) ? genreCount.get(genre) : 0) + 1);
        }

        genres.addAll(genreCount.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                genres
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genreSpinner.setAdapter(adapter);

        int selectedIndex = genres.indexOf(selectedGenre);
        if (selectedIndex < 0) {
            selectedGenre = GENRE_ALL;
            selectedIndex = 0;
        }
        genreSpinner.setSelection(selectedIndex, false);
    }

    private void applyGenreFilter() {
        tracks.clear();
        if (GENRE_ALL.equals(selectedGenre)) {
            tracks.addAll(allTracks);
        } else {
            for (Song song : allTracks) {
                if (song == null) continue;
                String genre = song.getGenre() == null ? "" : song.getGenre().trim();
                if (genre.isEmpty()) genre = "Music";
                if (selectedGenre.equalsIgnoreCase(genre)) {
                    tracks.add(song);
                }
            }
        }

        trackAdapter.setTracks(tracks);
        currentIndex = -1;
        selectedTrackIndex = -1;
        if (player != null) {
            player.stop();
        }
        playPauseButton.setText("Play");

        if (tracks.isEmpty()) {
            trackListStatus.setVisibility(View.VISIBLE);
            trackListStatus.setText("No songs found in " + selectedGenre + ".");
            nowPlayingText.setText("No track selected");
        } else {
            trackListStatus.setVisibility(View.GONE);
            nowPlayingText.setText("Loaded " + tracks.size() + " songs • " + selectedGenre);
        }
    }

    private void runPlaylistSyncQuietly() {
        if (backgroundExecutor == null || playlistSyncManager == null) {
            return;
        }
        backgroundExecutor.execute(() -> {
            PlaylistSyncModels.SyncStatus status = playlistSyncManager.syncNow();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateAccountStatusText();
                String base = accountStatus.getText() == null ? "Account" : accountStatus.getText().toString();
                accountStatus.setText(base + " · Sync " + status.state);
            });
        });
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Playlist name");

        new AlertDialog.Builder(this)
                .setTitle("Create playlist")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Playlist name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    playlistStorageRepository.createPlaylist(name);
                    runPlaylistSyncQuietly();
                    Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showProfileDialog() {
        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        if (session.isSignedIn()) {
            showSignedInProfileDialog(session);
            return;
        }
        showSignInDialog();
    }

    private void showSignInDialog() {
        View dialogView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        TextView text1 = dialogView.findViewById(android.R.id.text1);
        TextView text2 = dialogView.findViewById(android.R.id.text2);
        text1.setText("Sign in with your account details");
        text2.setText(firebaseConfigSummary());

        EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        EditText displayNameInput = new EditText(this);
        displayNameInput.setHint("Display name");

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);
        container.addView(dialogView);
        container.addView(emailInput);
        container.addView(displayNameInput);

        new AlertDialog.Builder(this)
                .setTitle("Profile")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
                    String displayName = displayNameInput.getText() == null ? "" : displayNameInput.getText().toString().trim();

                    if (email.isEmpty()) {
                        Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (displayName.isEmpty()) {
                        int atIndex = email.indexOf("@");
                        displayName = atIndex > 0 ? email.substring(0, atIndex) : email;
                    }

                    userSessionStore.signIn(email, displayName);
                    updateAccountStatusText();
                    Toast.makeText(this, "Profile saved locally", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String firebaseConfigSummary() {
        String projectId = safeConfig(BuildConfig.FIREBASE_PROJECT_ID);
        String authDomain = safeConfig(BuildConfig.FIREBASE_AUTH_DOMAIN);
        String appId = safeConfig(BuildConfig.FIREBASE_APP_ID);
        String senderId = safeConfig(BuildConfig.FIREBASE_MESSAGING_SENDER_ID);
        String measurementId = safeConfig(BuildConfig.FIREBASE_MEASUREMENT_ID);

        StringBuilder builder = new StringBuilder("Firebase configured: ");
        builder.append("project=").append(projectId)
                .append(", authDomain=").append(authDomain)
                .append(", appId=").append(appId)
                .append(", sender=").append(senderId);
        if (!measurementId.isEmpty()) {
            builder.append(", measurement=").append(measurementId);
        }
        return builder.toString();
    }

    private String safeConfig(String value) {
        return value == null ? "" : value.trim();
    }

    private void showSignedInProfileDialog(NativeUserSessionStore.UserSession session) {
        EditText displayNameInput = new EditText(this);
        displayNameInput.setHint("Display name");
        displayNameInput.setText(session.getDisplayName());

        new AlertDialog.Builder(this)
                .setTitle("Profile")
                .setMessage("Signed in as " + session.getEmail())
                .setView(displayNameInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String nextDisplayName = displayNameInput.getText() == null
                            ? ""
                            : displayNameInput.getText().toString().trim();
                    if (nextDisplayName.isEmpty()) {
                        Toast.makeText(this, "Display name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userSessionStore.updateDisplayName(nextDisplayName);
                    updateAccountStatusText();
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Sign out", (dialog, which) -> {
                    userSessionStore.signOut();
                    updateAccountStatusText();
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void updateAccountStatusText() {
        if (accountStatus == null || userSessionStore == null) return;
        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        if (!session.isSignedIn()) {
            accountStatus.setText("Account: Guest");
            return;
        }

        String displayName = session.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = session.getEmail();
        }
        accountStatus.setText("Account: " + displayName);
    }

    private void showAddToPlaylistDialog() {
        Song selectedSong = getSelectedSong();
        if (selectedSong == null) {
            Toast.makeText(this, "Select a track first", Toast.LENGTH_SHORT).show();
            return;
        }

        backgroundExecutor.execute(() -> {
            playlistSyncManager.syncNow();
            List<Playlist> playlists = playlistStorageRepository.getPlaylists();
            runOnUiThread(() -> {
                if (playlists.isEmpty()) {
                    Toast.makeText(this, "Create a playlist first", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] names = new String[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    names[i] = playlists.get(i).getName();
                }

                new AlertDialog.Builder(this)
                        .setTitle("Add to playlist")
                        .setItems(names, (dialog, which) -> {
                            Playlist selectedPlaylist = playlists.get(which);
                            boolean added = playlistStorageRepository.addSongToPlaylist(selectedPlaylist.getId(), selectedSong);
                            if (added) {
                                runPlaylistSyncQuietly();
                            }
                            Toast.makeText(
                                    this,
                                    added ? "Track added to " + selectedPlaylist.getName() : "Track already exists in playlist",
                                    Toast.LENGTH_SHORT
                            ).show();
                        })
                        .show();
            });
        });
    }


    private void openLibraryScreen() {
        Intent intent = new Intent(this, LibraryActivity.class);
        startActivityForResult(intent, REQUEST_LIBRARY);
    }

    private void openProfileScreen() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivityForResult(intent, REQUEST_PROFILE);
    }

    private void showLibraryDialog() {
        backgroundExecutor.execute(() -> {
            playlistSyncManager.syncNow();
            List<Playlist> playlists = playlistStorageRepository.getPlaylists();
            runOnUiThread(() -> {
                if (playlists.isEmpty()) {
                    Toast.makeText(this, "No playlists yet", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] names = new String[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    Playlist playlist = playlists.get(i);
                    names[i] = playlist.getName() + " (" + playlist.getSongs().size() + ")";
                }

                new AlertDialog.Builder(this)
                        .setTitle("Library")
                        .setItems(names, (dialog, which) -> showPlaylistDetailDialog(playlists.get(which)))
                        .show();
            });
        });
    }

    private void showPlaylistDetailDialog(Playlist playlist) {
        List<Song> songs = playlist.getSongs();
        if (songs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(playlist.getName())
                    .setMessage("Playlist is empty")
                    .setNeutralButton("Delete playlist", (dialog, which) -> {
                        playlistStorageRepository.deletePlaylist(playlist.getId());
                        runPlaylistSyncQuietly();
                        Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setPositiveButton("Play all", (dialog, which) -> playPlaylist(playlist))
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }

        String[] songItems = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            songItems[i] = song.getTitle() + " • " + song.getArtist();
        }

        new AlertDialog.Builder(this)
                .setTitle(playlist.getName())
                .setItems(songItems, (dialog, which) -> {
                    playPlaylist(playlist);
                    playTrack(which);
                })
                .setPositiveButton("Play all", (dialog, which) -> playPlaylist(playlist))
                .setNeutralButton("Remove track", (dialog, which) -> showRemoveTrackDialog(playlist))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showRemoveTrackDialog(Playlist playlist) {
        List<Song> songs = playlist.getSongs();
        if (songs.isEmpty()) {
            Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] songItems = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            songItems[i] = song.getTitle() + " • " + song.getArtist();
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove from " + playlist.getName())
                .setItems(songItems, (dialog, which) -> {
                    playlistStorageRepository.removeSongFromPlaylist(playlist.getId(), songs.get(which));
                    runPlaylistSyncQuietly();
                    Toast.makeText(this, "Track removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Song getSelectedSong() {
        if (selectedTrackIndex >= 0 && selectedTrackIndex < tracks.size()) {
            return tracks.get(selectedTrackIndex);
        }
        if (currentIndex >= 0 && currentIndex < tracks.size()) {
            return tracks.get(currentIndex);
        }
        return null;
    }

    private void playPlaylist(Playlist playlist) {
        allTracks.clear();
        allTracks.addAll(playlist.getSongs());
        refreshGenreFilterOptions(allTracks);
        selectedGenre = GENRE_ALL;
        applyGenreFilter();
        currentIndex = -1;
        selectedTrackIndex = -1;

        if (tracks.isEmpty()) {
            nowPlayingText.setText("No track selected");
            trackListStatus.setVisibility(View.VISIBLE);
            trackListStatus.setText("Playlist is empty.");
            return;
        }

        trackListStatus.setVisibility(View.GONE);
        nowPlayingText.setText("Playlist loaded. Select a track.");
        playTrack(0);
    }

    private void runSearchFromInput() {
        String query = searchInput.getText() != null ? searchInput.getText().toString().trim() : "";
        if (query.isEmpty()) {
            searchInput.setError("Enter a search query");
            return;
        }

        String selectedSource = sourceSpinner.getSelectedItem() != null
                ? sourceSpinner.getSelectedItem().toString()
                : SOURCE_FIREBASE;

        performSearch(query, selectedSource);
    }

    private void performSearch(String query, String source) {
        setSearchLoadingState(true);
        trackListStatus.setVisibility(View.VISIBLE);
        trackListStatus.setText("Searching for: " + query + " (" + source + ")...");

        backgroundExecutor.execute(() -> {
            try {
                List<SearchResult> searchResults = searchRepository.search(query, DEFAULT_SEARCH_MAX_RESULTS, source);

                runOnUiThread(() -> {
                    allTracks.clear();
                    for (SearchResult result : searchResults) {
                        allTracks.add(result.getSong());
                    }

                    refreshGenreFilterOptions(allTracks);
                    applyGenreFilter();
                    currentIndex = -1;
                    selectedTrackIndex = -1;
                    player.stop();
                    playPauseButton.setText("Play");

                    if (allTracks.isEmpty()) {
                        trackListStatus.setVisibility(View.VISIBLE);
                        trackListStatus.setText("No results found.");
                        nowPlayingText.setText("No track selected");
                    } else {
                        trackListStatus.setVisibility(View.GONE);
                        nowPlayingText.setText("Search loaded. Select a track.");
                    }
                    setSearchLoadingState(false);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    tracks.clear();
                    allTracks.clear();
                    refreshGenreFilterOptions(allTracks);
                    trackAdapter.setTracks(tracks);
                    currentIndex = -1;
                    selectedTrackIndex = -1;
                    trackListStatus.setVisibility(View.VISIBLE);
                    trackListStatus.setText("Search failed. Check API key/network and try again.");
                    nowPlayingText.setText("No track selected");
                    setSearchLoadingState(false);
                    Toast.makeText(
                            MainActivity.this,
                            "Search failed: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void setSearchLoadingState(boolean isLoading) {
        searchButton.setEnabled(!isLoading);
        searchInput.setEnabled(!isLoading);
        sourceSpinner.setEnabled(!isLoading);
        if (genreSpinner != null) genreSpinner.setEnabled(!isLoading);
    }

    private void loadHomeCatalog() {
        trackListStatus.setVisibility(View.VISIBLE);
        trackListStatus.setText("Loading home catalog...");

        backgroundExecutor.execute(() -> {
            try {
                List<Song> fetchedSongs = homeCatalogRepository.loadHomeCatalog(DEFAULT_SEARCH_MAX_RESULTS);

                runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(fetchedSongs);
                    refreshGenreFilterOptions(allTracks);
                    applyGenreFilter();
                    currentIndex = -1;
                    selectedTrackIndex = -1;

                    if (allTracks.isEmpty()) {
                        trackListStatus.setVisibility(View.VISIBLE);
                        trackListStatus.setText("Home catalog is empty.");
                        nowPlayingText.setText("No track selected");
                    } else {
                        trackListStatus.setVisibility(View.GONE);
                        nowPlayingText.setText("Home catalog loaded. Select a track.");
                    }

                    if (player != null) {
                        player.stop();
                        playPauseButton.setText("Play");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    trackListStatus.setVisibility(View.VISIBLE);
                    trackListStatus.setText("Failed to load home catalog.");
                    Toast.makeText(
                            MainActivity.this,
                            "Failed to load home catalog: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FULLSCREEN_PLAYER) {
            if (resultCode == RESULT_OK && data != null
                    && data.getBooleanExtra("request_toggle_play_pause", false)) {
                togglePlayPause();
            }
            return;
        }

        if (requestCode == REQUEST_PROFILE) {
            updateAccountStatusText();
            playlistStorageRepository = new PlaylistStorageRepository(this);
            playlistSyncManager = new PlaylistSyncManager(this);
            runPlaylistSyncQuietly();
            if (resultCode == RESULT_OK) {
                openLibraryScreen();
            }
            return;
        }

        if (requestCode != REQUEST_LIBRARY || resultCode != RESULT_OK || data == null) {
            return;
        }

        String playlistId = data.getStringExtra(LibraryActivity.EXTRA_PLAYLIST_ID);
        int songIndex = data.getIntExtra(LibraryActivity.EXTRA_SONG_INDEX, -1);
        if (playlistId == null || playlistId.isEmpty()) {
            return;
        }

        Playlist playlist = findPlaylistById(playlistId);
        if (playlist == null) {
            Toast.makeText(this, "Playlist no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }

        playPlaylist(playlist);
        if (songIndex >= 0) {
            playTrack(songIndex);
        }
    }

    private Playlist findPlaylistById(String playlistId) {
        List<Playlist> playlists = playlistStorageRepository.getPlaylists();
        for (Playlist playlist : playlists) {
            if (playlist.getId().equals(playlistId)) {
                return playlist;
            }
        }
        return null;
    }

    @Override
    public void onTrackClick(int position) {
        selectedTrackIndex = position;
        playTrack(position);
        focusSongSection();
    }

    private void playTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        logPlaybackEvent("play_track_requested", eventAttrs("track_index", String.valueOf(index)));
        currentIndex = index;
        selectedTrackIndex = index;
        Song track = tracks.get(index);

        String videoId = extractYouTubeVideoId(track.getMediaUrl(), track.getId());
        if (videoId != null) {
            playInEmbeddedYouTubePlayer(videoId, track);
            syncPlaybackStateToNotification();
            persistPlaybackSession();
            return;
        }

        int queueIndex = buildAndApplyNativeQueue(index, true);
        if (queueIndex < 0) {
            Toast.makeText(this, "Track cannot be played natively", Toast.LENGTH_SHORT).show();
            return;
        }
        updateNowPlayingText();
        updatePlaybackDiagnostics("Native queue index: " + queueIndex);
        syncPlaybackStateToNotification();
        persistPlaybackSession();
    }

    private void playInEmbeddedYouTubePlayer(String videoId, Song track) {
        activeYouTubeVideoId = videoId;
        youtubeIsPlaying = true;
        youtubeFallbackActivated = false;
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        showEmbeddedYouTubePlayer();
        if (embeddedYouTubePlayer != null) {
            YouTubePlayerUtils.loadOrCueVideo(embeddedYouTubePlayer, getLifecycle(), videoId, 0f);
        } else {
            pendingYouTubeVideoId = videoId;
        }
        nowPlayingText.setText("Now Playing: " + track.getTitle() + " • " + track.getArtist());
        updatePlaybackDiagnostics("YouTube playback in app player");
        playPauseButton.setText("Pause");
    }

    private void fallbackToExternalYouTube(String videoId, String reason) {
        if (youtubeFallbackActivated || videoId == null || videoId.isEmpty()) {
            return;
        }
        youtubeFallbackActivated = true;
        updatePlaybackDiagnostics("Opening external YouTube (reason: " + reason + ")");

        Intent appIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:" + videoId));
        if (appIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(appIntent);
            Toast.makeText(this, "This video cannot play inline. Opened in YouTube app.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=" + videoId));
        if (webIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(webIntent);
            Toast.makeText(this, "This video cannot play inline. Opened in browser.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Unable to open YouTube externally for this video.", Toast.LENGTH_LONG).show();
    }

    private void showEmbeddedYouTubePlayer() {
        if (youtubePlayerView != null) youtubePlayerView.setVisibility(View.VISIBLE);
        if (playerView != null) playerView.setVisibility(View.GONE);
    }

    private void showNativePlayer() {
        youtubeIsPlaying = false;
        activeYouTubeVideoId = null;
        if (youtubePlayerView != null) youtubePlayerView.setVisibility(View.GONE);
        if (playerView != null) playerView.setVisibility(View.VISIBLE);
    }

    private String extractYouTubeVideoId(String mediaUrl, String fallbackId) {
        String normalized = mediaUrl == null ? "" : mediaUrl.trim();
        if (!normalized.isEmpty()) {
            int watchIndex = normalized.indexOf("v=");
            if (watchIndex >= 0) {
                String candidate = normalized.substring(watchIndex + 2);
                int amp = candidate.indexOf('&');
                if (amp >= 0) candidate = candidate.substring(0, amp);
                if (isLikelyYouTubeVideoId(candidate)) return candidate;
            }
            if (normalized.contains("youtu.be/")) {
                String candidate = normalized.substring(normalized.indexOf("youtu.be/") + 9);
                int q = candidate.indexOf('?');
                if (q >= 0) candidate = candidate.substring(0, q);
                if (isLikelyYouTubeVideoId(candidate)) return candidate;
            }
            String[] markers = new String[] {"youtube.com/embed/", "youtube.com/shorts/"};
            for (String marker : markers) {
                int index = normalized.indexOf(marker);
                if (index < 0) continue;
                String candidate = normalized.substring(index + marker.length());
                int slash = candidate.indexOf('/');
                if (slash >= 0) candidate = candidate.substring(0, slash);
                int q = candidate.indexOf('?');
                if (q >= 0) candidate = candidate.substring(0, q);
                if (isLikelyYouTubeVideoId(candidate)) return candidate;
            }
        }

        if (isLikelyYouTubeVideoId(fallbackId)) return fallbackId.trim();
        return null;
    }

    private boolean isLikelyYouTubeVideoId(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9_-]{11}");
    }

    private void playNext() {
        if (tracks.isEmpty()) return;
        logPlaybackEvent("queue_next", eventAttrs("current_index", String.valueOf(currentIndex)));

        if (repeatMode == REPEAT_MODE_ONE && currentIndex >= 0) {
            playTrack(currentIndex);
            return;
        }

        int nextIndex = findAdjacentPlayableTrackIndex(currentIndex, true, repeatMode == REPEAT_MODE_ALL);
        if (nextIndex < 0) {
            Toast.makeText(this, "Reached end of queue", Toast.LENGTH_SHORT).show();
            return;
        }
        playTrack(nextIndex);
    }

    private void playPrevious() {
        if (tracks.isEmpty()) return;
        logPlaybackEvent("queue_previous", eventAttrs("current_index", String.valueOf(currentIndex)));

        if (repeatMode == REPEAT_MODE_ONE && currentIndex >= 0) {
            playTrack(currentIndex);
            return;
        }

        int prevIndex = findAdjacentPlayableTrackIndex(currentIndex, false, repeatMode == REPEAT_MODE_ALL);
        if (prevIndex < 0) {
            Toast.makeText(this, "Reached start of queue", Toast.LENGTH_SHORT).show();
            return;
        }
        playTrack(prevIndex);
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (tracks.isEmpty()) return;
        logPlaybackEvent("toggle_play_pause", eventAttrs("is_playing", String.valueOf(player.isPlaying())));

        Song currentTrackForToggle = (currentIndex >= 0 && currentIndex < tracks.size()) ? tracks.get(currentIndex) : null;
        if (currentTrackForToggle != null
                && isYouTubeExternalTrack(currentTrackForToggle)
                && youtubePlayerView != null
                && youtubePlayerView.getVisibility() == View.VISIBLE) {
            if (embeddedYouTubePlayer == null) {
                String currentVideoId = extractYouTubeVideoId(currentTrackForToggle.getMediaUrl(), currentTrackForToggle.getId());
                pendingYouTubeVideoId = currentVideoId;
                return;
            }

            if (youtubeIsPlaying) {
                embeddedYouTubePlayer.pause();
                playPauseButton.setText("Play");
                youtubeIsPlaying = false;
            } else {
                embeddedYouTubePlayer.play();
                playPauseButton.setText("Pause");
                youtubeIsPlaying = true;
            }
            syncPlaybackStateToNotification();
            persistPlaybackSession();
            return;
        }

        if (player.isPlaying()) {
            player.pause();
        } else {
            if (currentIndex < 0) {
                int firstPlayableIndex = findAdjacentPlayableTrackIndex(-1, true, repeatMode == REPEAT_MODE_ALL);
                playTrack(firstPlayableIndex >= 0 ? firstPlayableIndex : 0);
                return;
            }
            Song currentTrack = tracks.get(currentIndex);
            String currentVideoId = extractYouTubeVideoId(currentTrack.getMediaUrl(), currentTrack.getId());
            if (currentVideoId != null && playerView.getVisibility() != View.VISIBLE) {
                if (embeddedYouTubePlayer != null) {
                    embeddedYouTubePlayer.play();
                } else {
                    pendingYouTubeVideoId = currentVideoId;
                }
                return;
            }

            if (player.getMediaItemCount() == 0) {
                int queueIndex = buildAndApplyNativeQueue(currentIndex, false);
                if (queueIndex < 0) {
                    Toast.makeText(this, "Track cannot be played natively", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            player.play();
        }
        syncPlaybackStateToNotification();
        persistPlaybackSession();
    }

    private void setPlaybackEnabled(boolean shouldPlay) {
        if (player == null || tracks.isEmpty()) {
            return;
        }
        if (shouldPlay == player.isPlaying()) {
            return;
        }
        togglePlayPause();
    }

    private void seekPlayback(long targetPositionMs) {
        if (player == null || currentIndex < 0 || currentIndex >= tracks.size()) {
            return;
        }
        long boundedPositionMs = Math.max(0L, targetPositionMs);
        long durationMs = Math.max(0L, player.getDuration());
        if (durationMs > 0L) {
            boundedPositionMs = Math.min(durationMs, boundedPositionMs);
        }
        player.seekTo(boundedPositionMs);
        syncPlaybackStateToNotification();
        persistPlaybackSession();
    }

    private boolean isYouTubeExternalTrack(Song song) {
        return song != null && extractYouTubeVideoId(song.getMediaUrl(), song.getId()) != null;
    }

    private int buildAndApplyNativeQueue(int targetTrackIndex, boolean autoplay) {
        if (player == null) return -1;

        List<MediaItem> mediaItems = new ArrayList<>();
        int targetWindowIndex = -1;
        activeQueueTrackIndexes.clear();
        logPlaybackEvent("build_native_queue", eventAttrs("target_track_index", String.valueOf(targetTrackIndex), "autoplay", String.valueOf(autoplay)));

        for (int i = 0; i < tracks.size(); i++) {
            Song song = tracks.get(i);
            if (song == null || isYouTubeExternalTrack(song)) {
                continue;
            }
            String mediaUrl = song.getMediaUrl();
            if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                continue;
            }

            if (i == targetTrackIndex) {
                targetWindowIndex = mediaItems.size();
            }
            activeQueueTrackIndexes.add(i);

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMediaId(String.valueOf(i))
                    .build();
            mediaItems.add(mediaItem);
        }

        if (mediaItems.isEmpty() || targetWindowIndex < 0) {
            return -1;
        }

        currentQueueIndex = targetWindowIndex;
        showNativePlayer();
        player.setMediaItems(mediaItems, targetWindowIndex, C.TIME_UNSET);
        logPlaybackEvent("native_queue_applied", eventAttrs("media_items", String.valueOf(mediaItems.size()), "queue_index", String.valueOf(currentQueueIndex)));
        player.prepare();
        if (autoplay) {
            player.play();
        }
        return targetWindowIndex;
    }

    private int applyNativeQueueSnapshot(List<Integer> queueTrackIndexes, int targetQueueIndex, boolean autoplay) {
        if (player == null || queueTrackIndexes == null || queueTrackIndexes.isEmpty()) return -1;

        List<MediaItem> mediaItems = new ArrayList<>();
        activeQueueTrackIndexes.clear();
        logPlaybackEvent("apply_queue_snapshot", eventAttrs("snapshot_size", String.valueOf(queueTrackIndexes.size()), "target_queue_index", String.valueOf(targetQueueIndex), "autoplay", String.valueOf(autoplay)));

        for (Integer trackIndexObj : queueTrackIndexes) {
            if (trackIndexObj == null) continue;
            int trackIndex = trackIndexObj;
            if (trackIndex < 0 || trackIndex >= tracks.size()) {
                continue;
            }

            Song song = tracks.get(trackIndex);
            if (song == null || isYouTubeExternalTrack(song)) {
                continue;
            }

            String mediaUrl = song.getMediaUrl();
            if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                continue;
            }

            activeQueueTrackIndexes.add(trackIndex);
            mediaItems.add(new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMediaId(String.valueOf(trackIndex))
                    .build());
        }

        if (mediaItems.isEmpty()) {
            currentQueueIndex = -1;
            return -1;
        }

        int safeQueueIndex = targetQueueIndex;
        if (safeQueueIndex < 0 || safeQueueIndex >= mediaItems.size()) {
            safeQueueIndex = 0;
        }

        currentQueueIndex = safeQueueIndex;
        showNativePlayer();
        player.setMediaItems(mediaItems, safeQueueIndex, C.TIME_UNSET);
        player.prepare();
        if (autoplay) {
            player.play();
        }
        return safeQueueIndex;
    }

    private int findAdjacentPlayableTrackIndex(int fromIndex, boolean forward, boolean wrapAround) {
        if (tracks.isEmpty()) return -1;

        int size = tracks.size();
        if (fromIndex < 0) {
            return findFirstPlayableTrackIndex(forward);
        }

        int candidate = fromIndex;
        while (true) {
            candidate += forward ? 1 : -1;

            if (candidate < 0 || candidate >= size) {
                if (!wrapAround) {
                    return -1;
                }
                candidate = forward ? 0 : size - 1;
            }

            if (candidate == fromIndex) {
                return -1;
            }

            Song song = tracks.get(candidate);
            if (song != null && !isYouTubeExternalTrack(song)) {
                String mediaUrl = song.getMediaUrl();
                if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
                    return candidate;
                }
            }
        }
    }

    private int findFirstPlayableTrackIndex(boolean fromStart) {
        if (tracks.isEmpty()) return -1;
        int size = tracks.size();
        for (int i = 0; i < size; i++) {
            int candidate = fromStart ? i : (size - 1 - i);
            Song song = tracks.get(candidate);
            if (song != null && !isYouTubeExternalTrack(song)) {
                String mediaUrl = song.getMediaUrl();
                if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    private void showQueueDialog() {
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> queueLines = new ArrayList<>();
        int selectedQueueLine = -1;

        for (int i = 0; i < tracks.size(); i++) {
            Song song = tracks.get(i);
            if (song == null) {
                continue;
            }

            String mediaUrl = song.getMediaUrl();
            if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                continue;
            }

            StringBuilder line = new StringBuilder();
            if (i == currentIndex) {
                line.append("▶ ");
            } else {
                line.append("   ");
            }

            line.append(song.getTitle()).append(" • ").append(song.getArtist());

            if (isYouTubeExternalTrack(song)) {
                line.append(" (YouTube)");
            } else {
                line.append(" (Native)");
            }

            queueLines.add(line.toString());
            if (i == currentIndex) {
                selectedQueueLine = queueLines.size() - 1;
            }
        }

        if (queueLines.isEmpty()) {
            Toast.makeText(this, "Queue has no playable items", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Active Queue")
                .setNegativeButton("Close", null)
                .setItems(queueLines.toArray(new String[0]), (dialog, which) -> playTrack(resolveQueueTrackIndex(which)));

        if (selectedQueueLine >= 0) {
            builder.setMessage("Current track is marked with ▶");
        }

        builder.show();
    }

    private int resolveQueueTrackIndex(int queueLineIndex) {
        if (queueLineIndex < 0) return -1;

        int lineCursor = -1;
        for (int i = 0; i < tracks.size(); i++) {
            Song song = tracks.get(i);
            if (song == null) {
                continue;
            }
            String mediaUrl = song.getMediaUrl();
            if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                continue;
            }
            lineCursor += 1;
            if (lineCursor == queueLineIndex) {
                return i;
            }
        }

        return -1;
    }

    private void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        applyRepeatModeToPlayer();
        updateRepeatModeButtonLabel();
        Toast.makeText(this, "Repeat mode: " + getRepeatModeLabel(), Toast.LENGTH_SHORT).show();
        logPlaybackEvent("repeat_mode_changed", eventAttrs("repeat_mode", getRepeatModeLabel()));
        persistPlaybackSession();
    }

    private void applyRepeatModeToPlayer() {
        if (player == null) return;
        if (repeatMode == REPEAT_MODE_ONE) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else if (repeatMode == REPEAT_MODE_ALL) {
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    private void updateRepeatModeButtonLabel() {
        if (repeatModeButton == null) return;
        repeatModeButton.setText("Repeat: " + getRepeatModeLabel());
    }

    private String getRepeatModeLabel() {
        if (repeatMode == REPEAT_MODE_ALL) {
            return "All";
        }
        if (repeatMode == REPEAT_MODE_ONE) {
            return "One";
        }
        return "Off";
    }

    private void persistPlaybackSession() {
        if (playbackSessionStore == null) return;
        long positionMs = 0L;
        if (player != null && currentIndex >= 0 && currentIndex < tracks.size()) {
            positionMs = Math.max(0L, player.getCurrentPosition());
        }
        playbackSessionStore.save(tracks, currentIndex, selectedTrackIndex, positionMs, repeatMode, player != null && player.isPlaying(), activeQueueTrackIndexes, currentQueueIndex);
        logPlaybackEvent("session_persisted", eventAttrs("track_count", String.valueOf(tracks.size()), "current_index", String.valueOf(currentIndex), "queue_index", String.valueOf(currentQueueIndex)));
    }

    private void updateNowPlayingText() {
        if (currentIndex >= 0 && currentIndex < tracks.size()) {
            Song track = tracks.get(currentIndex);
            nowPlayingText.setText("Now Playing: " + track.getTitle() + " • " + track.getArtist());
        } else {
            nowPlayingText.setText("Select a track");
        }
    }

    private void openFullscreenPlayer() {
        if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) {
            Toast.makeText(this, "Play a track first to open full-screen player", Toast.LENGTH_SHORT).show();
            return;
        }

        Song currentSong = tracks.get(currentIndex);
        Intent intent = new Intent(this, FullscreenPlayerActivity.class);
        intent.putExtra("track_title", currentSong.getTitle());
        intent.putExtra("track_artist", currentSong.getArtist());
        intent.putExtra("track_thumbnail", currentSong.getThumbnailUrl());
        intent.putExtra("queue_position", currentQueueIndex + 1);
        intent.putExtra("queue_size", activeQueueTrackIndexes.size());
        intent.putExtra("repeat_label", getRepeatModeLabel());
        intent.putExtra("playback_state", player != null && player.isPlaying() ? "Playing" : "Paused");
        intent.putExtra("buffering_state", player != null ? playbackStateLabel(player.getPlaybackState()) : "idle");
        String youtubeVideoId = extractYouTubeVideoId(currentSong.getMediaUrl(), currentSong.getId());
        intent.putExtra("youtube_video_id", youtubeVideoId == null ? "" : youtubeVideoId);
        intent.putExtra("source_type", youtubeVideoId != null ? "YouTube in-app player" : "Native queue");
        startActivityForResult(intent, REQUEST_FULLSCREEN_PLAYER);
    }

    private void showPlaybackDiagnosticsDialog() {
        if (playbackSoakGateEvaluator == null || playbackEventLogger == null) {
            Toast.makeText(this, "Playback diagnostics are unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        PlaybackSoakGateEvaluator.GateResult gateResult = playbackSoakGateEvaluator.evaluate();
        List<String> events = playbackEventLogger.getRecentEvents();
        StringBuilder details = new StringBuilder();
        details.append(gateResult.summary).append("\n\n");
        details.append("Recent events (newest last):\n");

        int start = Math.max(0, events.size() - 25);
        if (events.isEmpty()) {
            details.append("- No events recorded yet");
        } else {
            for (int i = start; i < events.size(); i++) {
                details.append("- ").append(events.get(i)).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Playback Diagnostics")
                .setMessage(details.toString())
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear Logs", (dialog, which) -> {
                    playbackSoakGateEvaluator.clearDiagnostics();
                    updatePlaybackDiagnostics("Diagnostics cleared");
                    Toast.makeText(this, "Playback diagnostics cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void syncPlaybackStateToNotification() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) return;
        Song track = tracks.get(currentIndex);
        Intent serviceIntent = new Intent(this, PlaybackService.class);
        serviceIntent.setAction(PlaybackService.ACTION_UPDATE_STATE);
        serviceIntent.putExtra("title", track.getTitle());
        serviceIntent.putExtra("artist", track.getArtist());

        boolean isPlayingNow = player != null && player.isPlaying();
        serviceIntent.putExtra("playing", isPlayingNow);
        serviceIntent.putExtra("position_ms", player != null ? Math.max(0L, player.getCurrentPosition()) : 0L);
        serviceIntent.putExtra("duration_ms", player != null ? Math.max(0L, player.getDuration()) : 0L);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlayingNow) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (RuntimeException runtimeError) {
            updatePlaybackDiagnostics("Notification sync unavailable: " + runtimeError.getClass().getSimpleName());
            logPlaybackEvent("notification_sync_failed", eventAttrs("reason", runtimeError.getClass().getSimpleName()));
        }
    }


    private boolean hasFocusWithin(View root) {
        if (root == null) return false;
        View focused = getCurrentFocus();
        return focused != null && (focused == root || isDescendantOf(focused, root));
    }

    private boolean isDescendantOf(View child, View potentialAncestor) {
        View current = child;
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (hasFocusWithin(songSection) || hasFocusWithin(playerSection)) {
            focusMenuSection();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        logPlaybackEvent("activity_start", eventAttrs());
        updatePlaybackDiagnostics("Lifecycle: start");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logPlaybackEvent("activity_resume", eventAttrs());
        updatePlaybackDiagnostics("Lifecycle: resume");
    }

    @Override
    protected void onPause() {
        logPlaybackEvent("activity_pause", eventAttrs());
        updatePlaybackDiagnostics("Lifecycle: pause");
        super.onPause();
    }


    @Override
    public void onTrackFocusLeftEdge() {
        focusMenuSection();
    }

    @Override
    public void onTrackFocusBottomEdge() {
        focusPlayerSection();
    }

    private void focusMenuSection() {
        if (libraryButton != null) {
            libraryButton.requestFocus();
        } else if (menuSection != null) {
            menuSection.requestFocus();
        }
    }

    private void focusSongSection() {
        RecyclerView trackList = findViewById(R.id.track_list);
        if (trackList != null && trackList.getLayoutManager() != null && trackAdapter != null && trackAdapter.getItemCount() > 0) {
            trackList.post(() -> {
                RecyclerView.ViewHolder holder = trackList.findViewHolderForAdapterPosition(0);
                if (holder != null) {
                    holder.itemView.requestFocus();
                } else {
                    trackList.scrollToPosition(0);
                    trackList.post(() -> {
                        RecyclerView.ViewHolder delayedHolder = trackList.findViewHolderForAdapterPosition(0);
                        if (delayedHolder != null) delayedHolder.itemView.requestFocus();
                    });
                }
            });
            return;
        }
        if (songSection != null) songSection.requestFocus();
    }

    private void focusPlayerSection() {
        if (playPauseButton != null) {
            playPauseButton.requestFocus();
        } else if (playerSection != null) {
            playerSection.requestFocus();
        }
    }

    private void applyTvFocusPolish() {
        if (searchInput != null) {
            searchInput.setNextFocusDownId(R.id.track_list);
            searchInput.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (libraryButton != null) {
            libraryButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (profileButton != null) {
            profileButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (playPauseButton != null) {
            playPauseButton.setNextFocusUpId(R.id.track_list);
            playPauseButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusSongSection();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    togglePlayPause();
                    return true;
                }
                return false;
            });
        }

        if (queueButton != null) {
            queueButton.setNextFocusUpId(R.id.track_list);
            queueButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (previousButton != null) {
            previousButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (nextButton != null) {
            nextButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusSongSection();
                    return true;
                }
                return false;
            });
        }

        if (playerView != null) {
            playerView.setUseController(true);
            playerView.setControllerAutoShow(true);
        }
    }

    private void updatePlaybackDiagnostics(String message) {
        if (playbackDiagnosticsText != null && message != null && !message.isEmpty()) {
            playbackDiagnosticsText.setText(message);
        }
    }

    private String playbackStateLabel(int state) {
        if (state == Player.STATE_IDLE) return "idle";
        if (state == Player.STATE_BUFFERING) return "buffering";
        if (state == Player.STATE_READY) return "ready";
        if (state == Player.STATE_ENDED) return "ended";
        return "unknown";
    }

    private Map<String, String> eventAttrs(String... keyValues) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (keyValues == null) return attrs;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        attrs.put("current_index", String.valueOf(currentIndex));
        attrs.put("queue_index", String.valueOf(currentQueueIndex));
        attrs.put("repeat_mode", getRepeatModeLabel());
        attrs.put("is_playing", String.valueOf(player != null && player.isPlaying()));
        return attrs;
    }

    private void logPlaybackEvent(String eventName, Map<String, String> attrs) {
        if (playbackEventLogger == null) return;
        playbackEventLogger.log(eventName, attrs);
    }

    @Override
    protected void onStop() {
        logPlaybackEvent("activity_stop", eventAttrs());
        updatePlaybackDiagnostics("Lifecycle: stop");
        super.onStop();
        persistPlaybackSession();
    }

    @Override
    protected void onDestroy() {
        logPlaybackEvent("activity_destroy", eventAttrs());
        updatePlaybackDiagnostics("Lifecycle: destroy");
        super.onDestroy();
        stateSyncHandler.removeCallbacksAndMessages(null);

        try {
            unregisterReceiver(mediaActionReceiver);
        } catch (IllegalArgumentException ignored) {
        }

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }

        persistPlaybackSession();

        if (player != null) {
            player.release();
            player = null;
        }
        if (youtubePlayerView != null) {
            youtubePlayerView.release();
        }
    }
}

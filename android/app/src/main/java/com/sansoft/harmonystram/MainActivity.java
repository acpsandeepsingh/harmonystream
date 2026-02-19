package com.sansoft.harmonystram;

import android.content.ActivityNotFoundException;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TrackAdapter.OnTrackClickListener {

    private static final int REQUEST_LIBRARY = 6001;
    private static final int REQUEST_PROFILE = 6002;

    private static final String SOURCE_YOUTUBE = "youtube";
    private static final String SOURCE_YOUTUBE_ALL = "youtube-all";
    private static final int DEFAULT_SEARCH_MAX_RESULTS = 25;

    private static final int REPEAT_MODE_OFF = 0;
    private static final int REPEAT_MODE_ALL = 1;
    private static final int REPEAT_MODE_ONE = 2;

    private ExoPlayer player;
    private PlayerView playerView;
    private Button playPauseButton;
    private Button repeatModeButton;
    private Button queueButton;
    private TextView nowPlayingText;
    private TextView playbackDiagnosticsText;
    private TextView trackListStatus;
    private TextView accountStatus;
    private EditText searchInput;
    private Spinner sourceSpinner;
    private Button searchButton;
    private EditText songIdInput;

    private final List<Song> tracks = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentIndex = -1;
    private int selectedTrackIndex = -1;
    private int repeatMode = REPEAT_MODE_OFF;
    private final List<Integer> activeQueueTrackIndexes = new ArrayList<>();
    private int currentQueueIndex = -1;

    private final HomeCatalogRepository homeCatalogRepository = new YouTubeHomeCatalogRepository();
    private final SongRepository searchRepository = new YouTubeRepository();
    private PlaylistStorageRepository playlistStorageRepository;
    private PlaylistSyncManager playlistSyncManager;
    private PlaybackSessionStore playbackSessionStore;
    private NativeUserSessionStore userSessionStore;
    private ExecutorService backgroundExecutor;
    private PlaybackEventLogger playbackEventLogger;
    private PlaybackSoakGateEvaluator playbackSoakGateEvaluator;
    private FirebaseSongRemoteDataSource firebaseSongRemoteDataSource;

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
                case PlaybackService.ACTION_NEXT:
                    playNext();
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
        searchButton = findViewById(R.id.btn_search);
        songIdInput = findViewById(R.id.song_id_input);
        playPauseButton = findViewById(R.id.btn_play_pause);
        repeatModeButton = findViewById(R.id.btn_repeat_mode);
        queueButton = findViewById(R.id.btn_queue);
        Button previousButton = findViewById(R.id.btn_previous);
        Button nextButton = findViewById(R.id.btn_next);
        Button createPlaylistButton = findViewById(R.id.btn_create_playlist);
        Button addToPlaylistButton = findViewById(R.id.btn_add_to_playlist);
        Button libraryButton = findViewById(R.id.btn_library);
        Button profileButton = findViewById(R.id.btn_profile);
        Button fullscreenButton = findViewById(R.id.btn_fullscreen);
        Button playbackDiagnosticsButton = findViewById(R.id.btn_playback_diagnostics);
        Button importSongIdButton = findViewById(R.id.btn_import_song_id);
        RecyclerView trackList = findViewById(R.id.track_list);

        userSessionStore = new NativeUserSessionStore(this);
        firebaseSongRemoteDataSource = new FirebaseSongRemoteDataSource();
        updateAccountStatusText();

        setupSourceSpinner();

        trackList.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(this);
        trackList.setAdapter(trackAdapter);

        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
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
        });

        searchButton.setOnClickListener(v -> runSearchFromInput());
        importSongIdButton.setOnClickListener(v -> importSongByIdFromInput());
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
    }

    private void importSongByIdFromInput() {
        if (songIdInput == null) {
            return;
        }

        String raw = songIdInput.getText() == null ? "" : songIdInput.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Enter a YouTube URL or video ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String videoId = extractYouTubeVideoId(raw);
        if (videoId.isEmpty()) {
            Toast.makeText(this, "Invalid YouTube URL or video ID", Toast.LENGTH_SHORT).show();
            return;
        }

        trackListStatus.setVisibility(View.VISIBLE);
        trackListStatus.setText("Importing song by ID...");

        backgroundExecutor.execute(() -> {
            try {
                Song song = searchRepository.getVideoDetails(videoId);
                boolean stored = firebaseSongRemoteDataSource.upsertSong(song, userSessionStore.getSession());

                runOnUiThread(() -> {
                    tracks.add(0, song);
                    trackAdapter.setTracks(tracks);
                    selectedTrackIndex = 0;
                    songIdInput.setText("");
                    trackListStatus.setVisibility(View.GONE);
                    String syncNote = stored ? "Saved to Firebase." : "Added locally only.";
                    Toast.makeText(this, "Imported " + song.getTitle() + ". " + syncNote, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    trackListStatus.setText("Import failed");
                    Toast.makeText(this, "Could not import song: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String extractYouTubeVideoId(String value) {
        if (value == null) return "";
        String input = value.trim();
        if (input.matches("^[a-zA-Z0-9_-]{11}$")) {
            return input;
        }

        try {
            android.net.Uri uri = android.net.Uri.parse(input);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }

            if (host.contains("youtu.be")) {
                String path = uri.getPath();
                if (path == null) return "";
                String id = path.replace("/", "").trim();
                return id.matches("^[a-zA-Z0-9_-]{11}$") ? id : "";
            }

            String id = uri.getQueryParameter("v");
            if (id != null && id.matches("^[a-zA-Z0-9_-]{11}$")) {
                return id;
            }
        } catch (Exception ignored) {
        }
        return "";
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

    private boolean restorePlaybackSession() {
        PlaybackSessionStore.PlaybackSession session = playbackSessionStore.load();
        if (!session.hasTracks()) {
            return false;
        }

        tracks.clear();
        tracks.addAll(session.getTracks());
        trackAdapter.setTracks(tracks);

        currentIndex = sanitizeIndex(session.getCurrentIndex(), tracks.size());
        selectedTrackIndex = sanitizeIndex(session.getSelectedIndex(), tracks.size());
        repeatMode = sanitizeRepeatMode(session.getRepeatMode());
        applyRepeatModeToPlayer();
        updateRepeatModeButtonLabel();

        if (currentIndex >= 0) {
            Song currentSong = tracks.get(currentIndex);
            if (isYouTubeExternalTrack(currentSong)) {
                nowPlayingText.setText("Last session track opens in YouTube app: " + currentSong.getTitle());
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
                new String[]{SOURCE_YOUTUBE, SOURCE_YOUTUBE_ALL}
        );
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);
    }

    private void runPlaylistSyncQuietly() {
        PlaylistSyncModels.SyncStatus status = playlistSyncManager.syncNow();
        updateAccountStatusText();
        String base = accountStatus.getText() == null ? "Account" : accountStatus.getText().toString();
        accountStatus.setText(base + " · Sync " + status.state);
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

        List<Playlist> playlists = playlistStorageRepository.getPlaylists();
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
        List<Playlist> playlists = playlistStorageRepository.getPlaylists();
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
        tracks.clear();
        tracks.addAll(playlist.getSongs());
        trackAdapter.setTracks(tracks);
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
                : SOURCE_YOUTUBE;

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
                    tracks.clear();
                    for (SearchResult result : searchResults) {
                        tracks.add(result.getSong());
                    }

                    trackAdapter.setTracks(tracks);
                    currentIndex = -1;
                    selectedTrackIndex = -1;
                    player.stop();
                    playPauseButton.setText("Play");

                    if (tracks.isEmpty()) {
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
    }

    private void loadHomeCatalog() {
        trackListStatus.setVisibility(View.VISIBLE);
        trackListStatus.setText("Loading home catalog...");

        backgroundExecutor.execute(() -> {
            try {
                List<Song> fetchedSongs = homeCatalogRepository.loadHomeCatalog(DEFAULT_SEARCH_MAX_RESULTS);

                runOnUiThread(() -> {
                    tracks.clear();
                    tracks.addAll(fetchedSongs);
                    trackAdapter.setTracks(tracks);
                    currentIndex = -1;
                    selectedTrackIndex = -1;

                    if (tracks.isEmpty()) {
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
        if (requestCode == REQUEST_PROFILE) {
            updateAccountStatusText();
            playlistStorageRepository = new PlaylistStorageRepository(this);
            playlistSyncManager = new PlaylistSyncManager(this);
            runPlaylistSyncQuietly();
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
    }

    private void playTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        logPlaybackEvent("play_track_requested", eventAttrs("track_index", String.valueOf(index)));
        currentIndex = index;
        selectedTrackIndex = index;
        Song track = tracks.get(index);

        if (track.getMediaUrl().contains("youtube.com/watch")) {
            openYouTubeVideo(track.getMediaUrl());
            nowPlayingText.setText("Opened in in-app player: " + track.getTitle());
            updatePlaybackDiagnostics("Web playback: in-app YouTube view");
            syncPlaybackStateToNotification();
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

    private void openYouTubeVideo(String videoUrl) {
        Intent intent = new Intent(this, WebAppActivity.class);
        intent.putExtra(WebAppActivity.EXTRA_START_URL, videoUrl);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Unable to open player", Toast.LENGTH_SHORT).show();
        }
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

        if (player.isPlaying()) {
            player.pause();
        } else {
            if (currentIndex < 0) {
                int firstPlayableIndex = findAdjacentPlayableTrackIndex(-1, true, repeatMode == REPEAT_MODE_ALL);
                playTrack(firstPlayableIndex >= 0 ? firstPlayableIndex : 0);
                return;
            }
            Song currentTrack = tracks.get(currentIndex);
            if (currentTrack.getMediaUrl().contains("youtube.com/watch")) {
                openYouTubeVideo(currentTrack.getMediaUrl());
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

    private boolean isYouTubeExternalTrack(Song song) {
        return song != null && song.getMediaUrl() != null && song.getMediaUrl().contains("youtube.com/watch");
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
        intent.putExtra("source_type", isYouTubeExternalTrack(currentSong) ? "YouTube app" : "Native queue");
        startActivity(intent);
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
        serviceIntent.putExtra("playing", player != null && player.isPlaying());
        serviceIntent.putExtra("position_ms", player != null ? Math.max(0L, player.getCurrentPosition()) : 0L);
        serviceIntent.putExtra("duration_ms", player != null ? Math.max(0L, player.getDuration()) : 0L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
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

    private void applyTvFocusPolish() {
        if (searchInput != null) searchInput.setNextFocusDownId(R.id.track_list);
        if (searchButton != null) searchButton.setNextFocusDownId(R.id.track_list);
        if (playPauseButton != null) playPauseButton.setNextFocusUpId(R.id.track_list);
        if (queueButton != null) queueButton.setNextFocusUpId(R.id.track_list);

        if (playPauseButton != null) {
            playPauseButton.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    togglePlayPause();
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
    }
}

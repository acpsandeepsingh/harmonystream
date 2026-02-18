package com.sansoft.harmonystram;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;
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
    private TextView nowPlayingText;
    private TextView trackListStatus;
    private TextView accountStatus;
    private EditText searchInput;
    private Spinner sourceSpinner;
    private Button searchButton;

    private final List<Song> tracks = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentIndex = -1;
    private int selectedTrackIndex = -1;
    private int repeatMode = REPEAT_MODE_OFF;

    private final HomeCatalogRepository homeCatalogRepository = new YouTubeHomeCatalogRepository();
    private final SongRepository searchRepository = new YouTubeRepository();
    private PlaylistStorageRepository playlistStorageRepository;
    private PlaybackSessionStore playbackSessionStore;
    private NativeUserSessionStore userSessionStore;
    private ExecutorService backgroundExecutor;

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
        playbackSessionStore = new PlaybackSessionStore(this);

        nowPlayingText = findViewById(R.id.now_playing);
        trackListStatus = findViewById(R.id.track_list_status);
        accountStatus = findViewById(R.id.account_status);
        searchInput = findViewById(R.id.search_query_input);
        sourceSpinner = findViewById(R.id.search_source_spinner);
        searchButton = findViewById(R.id.btn_search);
        playPauseButton = findViewById(R.id.btn_play_pause);
        repeatModeButton = findViewById(R.id.btn_repeat_mode);
        Button previousButton = findViewById(R.id.btn_previous);
        Button nextButton = findViewById(R.id.btn_next);
        Button createPlaylistButton = findViewById(R.id.btn_create_playlist);
        Button addToPlaylistButton = findViewById(R.id.btn_add_to_playlist);
        Button libraryButton = findViewById(R.id.btn_library);
        Button profileButton = findViewById(R.id.btn_profile);
        RecyclerView trackList = findViewById(R.id.track_list);

        userSessionStore = new NativeUserSessionStore(this);
        updateAccountStatusText();

        setupSourceSpinner();

        trackList.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(this);
        trackList.setAdapter(trackAdapter);

        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        applyRepeatModeToPlayer();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playPauseButton.setText(isPlaying ? "Pause" : "Play");
                syncPlaybackStateToNotification();
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
                } catch (NumberFormatException ignored) {
                    return;
                }
                updateNowPlayingText();
                syncPlaybackStateToNotification();
            }
        });

        searchButton.setOnClickListener(v -> runSearchFromInput());
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        previousButton.setOnClickListener(v -> playPrevious());
        nextButton.setOnClickListener(v -> playNext());
        repeatModeButton.setOnClickListener(v -> cycleRepeatMode());
        createPlaylistButton.setOnClickListener(v -> showCreatePlaylistDialog());
        addToPlaylistButton.setOnClickListener(v -> showAddToPlaylistDialog());
        libraryButton.setOnClickListener(v -> openLibraryScreen());
        profileButton.setOnClickListener(v -> openProfileScreen());

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }

        updateRepeatModeButtonLabel();

        boolean restoredSession = restorePlaybackSession();
        if (!restoredSession) {
            loadHomeCatalog();
        }
        stateSyncHandler.post(stateSyncRunnable);
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
                int mediaWindowIndex = buildAndApplyNativeQueue(currentIndex, false);
                if (mediaWindowIndex >= 0) {
                    long positionMs = Math.max(0L, session.getPositionMs());
                    if (positionMs > 0) {
                        player.seekTo(mediaWindowIndex, positionMs);
                    }
                    nowPlayingText.setText("Ready to resume: " + currentSong.getTitle() + " • " + currentSong.getArtist());
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
        text1.setText("Add a profile identity for native session");
        text2.setText("Firebase auth wiring is planned next.");

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
        currentIndex = index;
        selectedTrackIndex = index;
        Song track = tracks.get(index);

        if (track.getMediaUrl().contains("youtube.com/watch")) {
            openYouTubeVideo(track.getMediaUrl());
            nowPlayingText.setText("Opened in YouTube app: " + track.getTitle());
            syncPlaybackStateToNotification();
            return;
        }

        int queueIndex = buildAndApplyNativeQueue(index, true);
        if (queueIndex < 0) {
            Toast.makeText(this, "Track cannot be played natively", Toast.LENGTH_SHORT).show();
            return;
        }
        updateNowPlayingText();
        syncPlaybackStateToNotification();
        persistPlaybackSession();
    }

    private void openYouTubeVideo(String videoUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open YouTube link", Toast.LENGTH_SHORT).show();
        }
    }

    private void playNext() {
        if (tracks.isEmpty()) return;

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

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMediaId(String.valueOf(i))
                    .build();
            mediaItems.add(mediaItem);
        }

        if (mediaItems.isEmpty() || targetWindowIndex < 0) {
            return -1;
        }

        player.setMediaItems(mediaItems, targetWindowIndex, C.TIME_UNSET);
        player.prepare();
        if (autoplay) {
            player.play();
        }
        return targetWindowIndex;
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

    private void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        applyRepeatModeToPlayer();
        updateRepeatModeButtonLabel();
        Toast.makeText(this, "Repeat mode: " + getRepeatModeLabel(), Toast.LENGTH_SHORT).show();
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
        playbackSessionStore.save(tracks, currentIndex, selectedTrackIndex, positionMs, repeatMode);
    }

    private void updateNowPlayingText() {
        if (currentIndex >= 0 && currentIndex < tracks.size()) {
            Song track = tracks.get(currentIndex);
            nowPlayingText.setText("Now Playing: " + track.getTitle() + " • " + track.getArtist());
        } else {
            nowPlayingText.setText("Select a track");
        }
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
    protected void onDestroy() {
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

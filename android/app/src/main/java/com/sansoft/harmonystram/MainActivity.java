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

    private static final String SOURCE_YOUTUBE = "youtube";
    private static final String SOURCE_YOUTUBE_ALL = "youtube-all";
    private static final int DEFAULT_SEARCH_MAX_RESULTS = 25;

    private ExoPlayer player;
    private PlayerView playerView;
    private Button playPauseButton;
    private TextView nowPlayingText;
    private TextView trackListStatus;
    private EditText searchInput;
    private Spinner sourceSpinner;
    private Button searchButton;

    private final List<Song> tracks = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentIndex = -1;

    private final HomeCatalogRepository homeCatalogRepository = new YouTubeHomeCatalogRepository();
    private final SongRepository searchRepository = new YouTubeRepository();
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

        nowPlayingText = findViewById(R.id.now_playing);
        trackListStatus = findViewById(R.id.track_list_status);
        searchInput = findViewById(R.id.search_query_input);
        sourceSpinner = findViewById(R.id.search_source_spinner);
        searchButton = findViewById(R.id.btn_search);
        playPauseButton = findViewById(R.id.btn_play_pause);
        Button previousButton = findViewById(R.id.btn_previous);
        Button nextButton = findViewById(R.id.btn_next);
        RecyclerView trackList = findViewById(R.id.track_list);

        setupSourceSpinner();

        trackList.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(this);
        trackList.setAdapter(trackAdapter);

        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);

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
                int newIndex = Integer.parseInt(mediaId);
                currentIndex = newIndex;
                updateNowPlayingText();
                syncPlaybackStateToNotification();
            }
        });

        searchButton.setOnClickListener(v -> runSearchFromInput());
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        previousButton.setOnClickListener(v -> playPrevious());
        nextButton.setOnClickListener(v -> playNext());

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }

        loadHomeCatalog();
        stateSyncHandler.post(stateSyncRunnable);
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
    public void onTrackClick(int position) {
        playTrack(position);
    }

    private void playTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        currentIndex = index;
        Song track = tracks.get(index);

        if (track.getMediaUrl().contains("youtube.com/watch")) {
            openYouTubeVideo(track.getMediaUrl());
            nowPlayingText.setText("Opened in YouTube app: " + track.getTitle());
            syncPlaybackStateToNotification();
            return;
        }

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(track.getMediaUrl())
                .setMediaId(String.valueOf(index))
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        updateNowPlayingText();
        syncPlaybackStateToNotification();
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
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % tracks.size();
        playTrack(nextIndex);
    }

    private void playPrevious() {
        if (tracks.isEmpty()) return;
        int prevIndex = currentIndex <= 0 ? tracks.size() - 1 : currentIndex - 1;
        playTrack(prevIndex);
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (tracks.isEmpty()) return;

        if (player.isPlaying()) {
            player.pause();
        } else {
            if (currentIndex < 0) {
                playTrack(0);
                return;
            }
            Song currentTrack = tracks.get(currentIndex);
            if (currentTrack.getMediaUrl().contains("youtube.com/watch")) {
                openYouTubeVideo(currentTrack.getMediaUrl());
                return;
            }
            player.play();
        }
        syncPlaybackStateToNotification();
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

        if (player != null) {
            player.release();
            player = null;
        }
    }
}

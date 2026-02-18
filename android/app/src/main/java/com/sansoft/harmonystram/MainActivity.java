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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TrackAdapter.OnTrackClickListener {

    private ExoPlayer player;
    private PlayerView playerView;
    private Button playPauseButton;
    private TextView nowPlayingText;

    private final List<Song> tracks = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentIndex = -1;

    private final YouTubeRepository youTubeRepository = new YouTubeRepository();
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
        playPauseButton = findViewById(R.id.btn_play_pause);
        Button previousButton = findViewById(R.id.btn_previous);
        Button nextButton = findViewById(R.id.btn_next);
        RecyclerView trackList = findViewById(R.id.track_list);

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

        playPauseButton.setOnClickListener(v -> togglePlayPause());
        previousButton.setOnClickListener(v -> playPrevious());
        nextButton.setOnClickListener(v -> playNext());

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }

        seedFallbackTrackCatalog();
        loadInitialSongsFromYouTube();

        if (!tracks.isEmpty()) {
            playTrack(0);
        }
        stateSyncHandler.post(stateSyncRunnable);
    }

    private void seedFallbackTrackCatalog() {
        tracks.clear();
        tracks.add(new Song(
                "demo-1",
                "SoundHelix Song 1",
                "Demo Stream",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                ""
        ));
        tracks.add(new Song(
                "demo-2",
                "SoundHelix Song 2",
                "Demo Stream",
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                ""
        ));
        tracks.add(new Song(
                "demo-3",
                "Big Buck Bunny (Video)",
                "Demo Video",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                ""
        ));
        trackAdapter.setTracks(tracks);
    }

    private void loadInitialSongsFromYouTube() {
        backgroundExecutor.execute(() -> {
            try {
                List<Song> fetchedSongs = youTubeRepository.searchSongs("trending music", 25);
                if (fetchedSongs.isEmpty()) return;

                runOnUiThread(() -> {
                    tracks.clear();
                    tracks.addAll(fetchedSongs);
                    trackAdapter.setTracks(tracks);
                    currentIndex = -1;
                    nowPlayingText.setText("YouTube list loaded. Select a track.");
                    if (player != null) {
                        player.stop();
                        playPauseButton.setText("Play");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Using fallback tracks. YouTube fetch failed: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
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
        if (player.isPlaying()) {
            player.pause();
        } else {
            if (currentIndex < 0 && !tracks.isEmpty()) {
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

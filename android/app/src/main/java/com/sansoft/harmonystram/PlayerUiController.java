package com.sansoft.harmonystram;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class PlayerUiController {

    interface Actions {
        void sendServiceIntent(@NonNull Intent intent);
        void dispatchToWeb(@NonNull String js);
        void onModeToggleRequested(boolean enabled);
    }

    private final WebAppActivity activity;
    private final Actions actions;
    private final FrameLayout playerContainer;

    private TextView title;
    private TextView artist;
    private TextView currentTime;
    private TextView durationTime;
    private ImageView thumb;
    private ImageButton play;
    private ImageButton next;
    private ImageButton previous;
    private ImageButton mode;
    private ImageButton like;
    private ImageButton queue;
    private ImageButton add;
    private ImageButton share;
    private SeekBar seekBar;
    private SeekBar volumeBar;

    private boolean isSeeking;
    private int artworkRequestId;
    @Nullable private String currentThumbnailUrl;

    PlayerUiController(@NonNull WebAppActivity activity,
                       @NonNull FrameLayout playerContainer,
                       @NonNull Actions actions) {
        this.activity = activity;
        this.playerContainer = playerContainer;
        this.actions = actions;
    }

    void init() {
        playerContainer.removeAllViews();
        activity.getLayoutInflater().inflate(R.layout.view_native_player, playerContainer, true);

        title = playerContainer.findViewById(R.id.title);
        artist = playerContainer.findViewById(R.id.artist);
        currentTime = playerContainer.findViewById(R.id.timeCurrent);
        durationTime = playerContainer.findViewById(R.id.timeDuration);
        thumb = playerContainer.findViewById(R.id.thumb);

        play = playerContainer.findViewById(R.id.btnPlay);
        next = playerContainer.findViewById(R.id.btnNext);
        previous = playerContainer.findViewById(R.id.btnPrev);
        mode = playerContainer.findViewById(R.id.btnMode);
        like = playerContainer.findViewById(R.id.btnLike);
        queue = playerContainer.findViewById(R.id.btnQueue);
        add = playerContainer.findViewById(R.id.btnAdd);
        share = playerContainer.findViewById(R.id.btnShare);
        seekBar = playerContainer.findViewById(R.id.seekBar);
        volumeBar = playerContainer.findViewById(R.id.volumeBar);

        setupControls();
        showEmptyState();
    }

    private void setupControls() {
        if (play != null) {
            play.setOnClickListener(v -> {
                Intent i = new Intent(activity, PlaybackService.class);
                i.setAction(PlaybackService.ACTION_PLAY_PAUSE);
                actions.sendServiceIntent(i);
            });
        }
        if (next != null) {
            next.setOnClickListener(v -> {
                Intent i = new Intent(activity, PlaybackService.class);
                i.setAction(PlaybackService.ACTION_NEXT);
                actions.sendServiceIntent(i);
            });
        }
        if (previous != null) {
            previous.setOnClickListener(v -> {
                Intent i = new Intent(activity, PlaybackService.class);
                i.setAction(PlaybackService.ACTION_PREVIOUS);
                actions.sendServiceIntent(i);
            });
        }
        if (mode != null) {
            mode.setOnClickListener(v -> actions.onModeToggleRequested(!v.isSelected()));
        }
        if (like != null) {
            like.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeToggleLike'))"));
        }
        if (queue != null) {
            queue.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeOpenQueue'))"));
        }
        if (add != null) {
            add.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeAddToPlaylist'))"));
        }
        if (share != null) {
            share.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeShareTrack'))"));
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && currentTime != null) {
                        currentTime.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    isSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    isSeeking = false;
                    Intent i = new Intent(activity, PlaybackService.class);
                    i.setAction(PlaybackService.ACTION_SEEK);
                    i.putExtra("position_ms", (long) bar.getProgress());
                    actions.sendServiceIntent(i);
                }
            });
        }

        if (volumeBar != null) {
            volumeBar.setMax(100);
            volumeBar.setProgress(100);
            volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    Intent i = new Intent(activity, PlaybackService.class);
                    i.setAction(PlaybackService.ACTION_SET_VOLUME);
                    i.putExtra("volume", progress / 100f);
                    actions.sendServiceIntent(i);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    void showEmptyState() {
        updateUi("No song selected", "-", null, false, 0L, 0L, false, false);
    }

    void updateFromState(@Nullable Intent stateIntent) {
        if (stateIntent == null) return;
        updateUi(
                stateIntent.getStringExtra("title"),
                stateIntent.getStringExtra("artist"),
                stateIntent.getStringExtra("thumbnailUrl"),
                stateIntent.getBooleanExtra("playing", false),
                stateIntent.getLongExtra("position_ms", 0L),
                stateIntent.getLongExtra("duration_ms", 0L),
                stateIntent.getBooleanExtra("video_mode", false),
                stateIntent.getBooleanExtra("liked", false)
        );
    }

    void updateFromSnapshot(@NonNull PlaybackService.PlaybackSnapshot snapshot) {
        updateUi(
                snapshot.title,
                snapshot.artist,
                snapshot.thumbnailUrl,
                snapshot.playing,
                snapshot.positionMs,
                snapshot.durationMs,
                snapshot.videoMode,
                false
        );
    }

    private void updateUi(String rawTitle,
                          String rawArtist,
                          @Nullable String thumbnailUrl,
                          boolean playing,
                          long positionMs,
                          long durationMs,
                          boolean videoModeEnabled,
                          boolean liked) {
        String safeTitle = (rawTitle == null || rawTitle.trim().isEmpty()) ? "No song selected" : rawTitle;
        String safeArtist = (rawArtist == null || rawArtist.trim().isEmpty()) ? "-" : rawArtist;
        boolean hasMedia = !"No song selected".equals(safeTitle);

        if (title != null) title.setText(safeTitle);
        if (artist != null) artist.setText(safeArtist);
        if (play != null) {
            play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            play.setContentDescription(playing ? "Pause" : "Play");
            play.setEnabled(hasMedia);
            play.setAlpha(hasMedia ? 1f : 0.5f);
        }
        if (next != null) {
            next.setEnabled(hasMedia);
            next.setAlpha(hasMedia ? 1f : 0.5f);
        }
        if (previous != null) {
            previous.setEnabled(hasMedia);
            previous.setAlpha(hasMedia ? 1f : 0.5f);
        }
        if (mode != null) {
            mode.setSelected(videoModeEnabled);
            mode.setImageResource(videoModeEnabled ? R.drawable.ic_videocam : R.drawable.ic_music_note);
            mode.setContentDescription(videoModeEnabled ? "Switch to audio" : "Switch to video");
            mode.setBackgroundResource(videoModeEnabled
                    ? R.drawable.player_button_bg_active
                    : R.drawable.player_button_bg);
            mode.setEnabled(hasMedia);
            mode.setAlpha(hasMedia ? 1f : 0.5f);
        }
        if (like != null) {
            like.setEnabled(hasMedia);
            like.setAlpha(hasMedia ? 1f : 0.5f);
            like.setImageResource(liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            like.setBackgroundResource(liked ? R.drawable.player_button_bg_active : R.drawable.player_button_bg);
        }
        if (share != null) {
            share.setEnabled(hasMedia);
            share.setAlpha(hasMedia ? 1f : 0.5f);
        }

        long safeDuration = Math.max(0L, durationMs);
        long safePosition = Math.max(0L, positionMs);

        if (seekBar != null) {
            seekBar.setEnabled(hasMedia && safeDuration > 0L);
            seekBar.setMax((int) Math.min(Integer.MAX_VALUE, safeDuration));
            if (!isSeeking) {
                seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, safePosition));
            }
        }
        if (currentTime != null && !isSeeking) currentTime.setText(formatTime(safePosition));
        if (durationTime != null) durationTime.setText(formatTime(safeDuration));

        updateThumb(thumbnailUrl);
        playerContainer.setVisibility(View.VISIBLE);
    }

    private void updateThumb(@Nullable String thumbnailUrl) {
        if (thumb == null) return;
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) {
            currentThumbnailUrl = null;
            thumb.setImageResource(R.drawable.ic_music_note);
            return;
        }

        String normalized = thumbnailUrl.trim();
        if (normalized.equals(currentThumbnailUrl)) {
            return;
        }

        currentThumbnailUrl = normalized;
        final int req = ++artworkRequestId;
        thumb.setImageResource(R.drawable.ic_music_note);

        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(normalized);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(7000);
                connection.setDoInput(true);
                connection.connect();
                try (InputStream stream = connection.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            Bitmap resolved = bmp;
            activity.runOnUiThread(() -> {
                if (thumb == null || req != artworkRequestId) return;
                if (resolved != null) {
                    thumb.setImageBitmap(resolved);
                } else {
                    thumb.setImageResource(R.drawable.ic_music_note);
                }
            });
        }).start();
    }

    private String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }
}

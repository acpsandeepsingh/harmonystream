package com.sansoft.harmonystram;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private ImageButton play;
    private ImageButton next;
    private ImageButton previous;
    private ImageButton mode;
    private ImageButton queue;
    private ImageButton add;
    private SeekBar seekBar;
    private SeekBar volumeBar;

    private boolean isSeeking;

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

        play = playerContainer.findViewById(R.id.btnPlay);
        next = playerContainer.findViewById(R.id.btnNext);
        previous = playerContainer.findViewById(R.id.btnPrev);
        mode = playerContainer.findViewById(R.id.btnMode);
        queue = playerContainer.findViewById(R.id.btnQueue);
        add = playerContainer.findViewById(R.id.btnAdd);
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
        if (queue != null) {
            queue.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeOpenQueue'));"));
        }
        if (add != null) {
            add.setOnClickListener(v -> actions.dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeAddToPlaylist'));"));
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && currentTime != null) currentTime.setText(formatTime(progress));
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
        updateUi("No song selected", "-", false, 0L, 0L, false);
    }

    void updateFromState(@Nullable Intent stateIntent) {
        if (stateIntent == null) return;
        updateUi(
                stateIntent.getStringExtra("title"),
                stateIntent.getStringExtra("artist"),
                stateIntent.getBooleanExtra("playing", false),
                stateIntent.getLongExtra("position_ms", 0L),
                stateIntent.getLongExtra("duration_ms", 0L),
                stateIntent.getBooleanExtra("video_mode", false)
        );
    }

    void updateFromSnapshot(@NonNull PlaybackService.PlaybackSnapshot snapshot) {
        updateUi(snapshot.title, snapshot.artist, snapshot.playing, snapshot.positionMs, snapshot.durationMs, false);
    }

    private void updateUi(String rawTitle, String rawArtist, boolean playing, long positionMs, long durationMs, boolean videoModeEnabled) {
        String safeTitle = (rawTitle == null || rawTitle.trim().isEmpty()) ? "No song selected" : rawTitle;
        String safeArtist = (rawArtist == null || rawArtist.trim().isEmpty()) ? "-" : rawArtist;
        boolean hasMedia = !"No song selected".equals(safeTitle);

        if (title != null) title.setText(safeTitle);
        if (artist != null) artist.setText(safeArtist);
        if (play != null) {
            play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
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
        }

        long safeDuration = Math.max(0L, durationMs);
        long safePosition = Math.max(0L, positionMs);

        if (seekBar != null) {
            seekBar.setEnabled(hasMedia);
            seekBar.setMax((int) Math.min(Integer.MAX_VALUE, safeDuration));
            if (!isSeeking) {
                seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, safePosition));
            }
        }
        if (currentTime != null && !isSeeking) currentTime.setText(formatTime(safePosition));
        if (durationTime != null) durationTime.setText(formatTime(safeDuration));

        playerContainer.setVisibility(View.VISIBLE);
    }

    private String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }
}

package com.sansoft.harmonystram;

import android.content.Intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PlaybackViewModel extends ViewModel {

    public static class PlaybackUiState {
        public final String title;
        public final String artist;
        public final boolean playing;
        public final long positionMs;
        public final long durationMs;

        PlaybackUiState(String title, String artist, boolean playing, long positionMs, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.playing = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
        }
    }

    private final MutableLiveData<PlaybackUiState> state = new MutableLiveData<>(
            new PlaybackUiState("HarmonyStream", "", false, 0L, 0L)
    );

    public LiveData<PlaybackUiState> getState() {
        return state;
    }

    public void setSnapshot(PlaybackService.PlaybackSnapshot snapshot) {
        if (snapshot == null) return;
        state.postValue(new PlaybackUiState(
                snapshot.title,
                snapshot.artist,
                snapshot.playing,
                snapshot.positionMs,
                snapshot.durationMs
        ));
    }

    public void updateFromBroadcast(Intent intent) {
        if (intent == null) return;
        state.postValue(new PlaybackUiState(
                intent.getStringExtra("title") == null ? "HarmonyStream" : intent.getStringExtra("title"),
                intent.getStringExtra("artist") == null ? "" : intent.getStringExtra("artist"),
                intent.getBooleanExtra("playing", false),
                Math.max(0L, intent.getLongExtra("position_ms", 0L)),
                Math.max(0L, intent.getLongExtra("duration_ms", 0L))
        ));
    }
}

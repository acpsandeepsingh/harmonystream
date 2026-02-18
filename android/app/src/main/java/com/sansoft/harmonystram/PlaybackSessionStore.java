package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PlaybackSessionStore {
    private static final String PREFS_NAME = "harmonystream_playback_session";
    private static final String KEY_TRACKS = "tracks";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final String KEY_SELECTED_INDEX = "selected_index";
    private static final String KEY_POSITION_MS = "position_ms";
    private static final String KEY_REPEAT_MODE = "repeat_mode";

    private final SharedPreferences sharedPreferences;

    public PlaybackSessionStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(List<Song> tracks, int currentIndex, int selectedIndex, long positionMs, int repeatMode) {
        JSONArray tracksArray = new JSONArray();
        for (Song song : tracks) {
            JSONObject songJson = new JSONObject();
            songJson.put("id", safeValue(song.getId()));
            songJson.put("title", safeValue(song.getTitle()));
            songJson.put("artist", safeValue(song.getArtist()));
            songJson.put("mediaUrl", safeValue(song.getMediaUrl()));
            songJson.put("thumbnailUrl", safeValue(song.getThumbnailUrl()));
            songJson.put("durationMs", Math.max(0L, song.getDurationMs()));
            tracksArray.put(songJson);
        }

        sharedPreferences.edit()
                .putString(KEY_TRACKS, tracksArray.toString())
                .putInt(KEY_CURRENT_INDEX, currentIndex)
                .putInt(KEY_SELECTED_INDEX, selectedIndex)
                .putLong(KEY_POSITION_MS, Math.max(0L, positionMs))
                .putInt(KEY_REPEAT_MODE, repeatMode)
                .apply();
    }

    public PlaybackSession load() {
        String tracksJson = sharedPreferences.getString(KEY_TRACKS, null);
        if (tracksJson == null || tracksJson.isEmpty()) {
            return PlaybackSession.empty();
        }

        List<Song> sessionTracks = new ArrayList<>();
        try {
            JSONArray tracksArray = new JSONArray(tracksJson);
            for (int i = 0; i < tracksArray.length(); i++) {
                JSONObject item = tracksArray.optJSONObject(i);
                if (item == null) continue;
                Song song = new Song(
                        item.optString("id", ""),
                        item.optString("title", ""),
                        item.optString("artist", ""),
                        item.optString("mediaUrl", ""),
                        item.optString("thumbnailUrl", ""),
                        Math.max(0L, item.optLong("durationMs", 0L))
                );
                if (song.getMediaUrl().isEmpty()) continue;
                sessionTracks.add(song);
            }
        } catch (Exception ignored) {
            return PlaybackSession.empty();
        }

        if (sessionTracks.isEmpty()) {
            return PlaybackSession.empty();
        }

        int currentIndex = sharedPreferences.getInt(KEY_CURRENT_INDEX, -1);
        int selectedIndex = sharedPreferences.getInt(KEY_SELECTED_INDEX, -1);
        long positionMs = Math.max(0L, sharedPreferences.getLong(KEY_POSITION_MS, 0L));
        int repeatMode = sharedPreferences.getInt(KEY_REPEAT_MODE, 0);

        return new PlaybackSession(sessionTracks, currentIndex, selectedIndex, positionMs, repeatMode);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    public static class PlaybackSession {
        private final List<Song> tracks;
        private final int currentIndex;
        private final int selectedIndex;
        private final long positionMs;
        private final int repeatMode;

        private PlaybackSession(List<Song> tracks, int currentIndex, int selectedIndex, long positionMs, int repeatMode) {
            this.tracks = tracks;
            this.currentIndex = currentIndex;
            this.selectedIndex = selectedIndex;
            this.positionMs = positionMs;
            this.repeatMode = repeatMode;
        }

        public static PlaybackSession empty() {
            return new PlaybackSession(new ArrayList<>(), -1, -1, 0L, 0);
        }

        public boolean hasTracks() {
            return !tracks.isEmpty();
        }

        public List<Song> getTracks() {
            return tracks;
        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        public long getPositionMs() {
            return positionMs;
        }

        public int getRepeatMode() {
            return repeatMode;
        }
    }
}

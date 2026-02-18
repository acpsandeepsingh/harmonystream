package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlaylistStorageRepository {
    private static final String PREFS_NAME = "harmonystream_playlists";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_PLAYLISTS_BY_ACCOUNT = "playlists_by_account";
    private static final String KEY_LEGACY_MIGRATED = "legacy_playlists_migrated";
    private static final String ACCOUNT_GUEST = "guest";

    private final SharedPreferences prefs;
    private final NativeUserSessionStore userSessionStore;

    public PlaylistStorageRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.userSessionStore = new NativeUserSessionStore(context.getApplicationContext());
    }

    public synchronized List<Playlist> getPlaylists() {
        return readPlaylists();
    }

    public synchronized Playlist createPlaylist(String name) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Playlist name cannot be empty");
        }

        List<Playlist> playlists = readPlaylists();
        Playlist playlist = new Playlist(UUID.randomUUID().toString(), trimmedName, new ArrayList<>());
        playlists.add(playlist);
        writePlaylists(playlists);
        return playlist;
    }

    public synchronized void deletePlaylist(String playlistId) {
        List<Playlist> playlists = readPlaylists();
        List<Playlist> updated = new ArrayList<>();
        for (Playlist playlist : playlists) {
            if (!playlist.getId().equals(playlistId)) {
                updated.add(playlist);
            }
        }
        writePlaylists(updated);
    }

    public synchronized boolean addSongToPlaylist(String playlistId, Song song) {
        List<Playlist> playlists = readPlaylists();
        List<Playlist> updated = new ArrayList<>();
        boolean didAdd = false;

        for (Playlist playlist : playlists) {
            if (!playlist.getId().equals(playlistId)) {
                updated.add(playlist);
                continue;
            }

            List<Song> songs = new ArrayList<>(playlist.getSongs());
            boolean exists = false;
            for (Song existingSong : songs) {
                if (sameSong(existingSong, song)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                songs.add(song);
                didAdd = true;
            }

            updated.add(new Playlist(playlist.getId(), playlist.getName(), songs));
        }

        writePlaylists(updated);
        return didAdd;
    }

    public synchronized void removeSongFromPlaylist(String playlistId, Song song) {
        List<Playlist> playlists = readPlaylists();
        List<Playlist> updated = new ArrayList<>();

        for (Playlist playlist : playlists) {
            if (!playlist.getId().equals(playlistId)) {
                updated.add(playlist);
                continue;
            }

            List<Song> songs = new ArrayList<>();
            for (Song existingSong : playlist.getSongs()) {
                if (!sameSong(existingSong, song)) {
                    songs.add(existingSong);
                }
            }

            updated.add(new Playlist(playlist.getId(), playlist.getName(), songs));
        }

        writePlaylists(updated);
    }

    private boolean sameSong(Song left, Song right) {
        if (left == null || right == null) return false;
        String leftId = safe(left.getId());
        String rightId = safe(right.getId());
        if (!leftId.isEmpty() && !rightId.isEmpty()) {
            return leftId.equals(rightId);
        }
        return safe(left.getMediaUrl()).equals(safe(right.getMediaUrl()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<Playlist> readPlaylists() {
        migrateLegacyPlaylistsIfNeeded();
        JSONObject playlistsByAccount = readPlaylistsByAccountObject();
        String raw = "[]";
        JSONArray scopeArray = playlistsByAccount.optJSONArray(getCurrentAccountKey());
        if (scopeArray != null) {
            raw = scopeArray.toString();
        }

        List<Playlist> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject playlistObj = array.optJSONObject(i);
                if (playlistObj == null) continue;

                String id = playlistObj.optString("id", "");
                String name = playlistObj.optString("name", "Untitled Playlist");
                JSONArray songsArray = playlistObj.optJSONArray("songs");

                List<Song> songs = new ArrayList<>();
                if (songsArray != null) {
                    for (int j = 0; j < songsArray.length(); j++) {
                        JSONObject songObj = songsArray.optJSONObject(j);
                        if (songObj == null) continue;
                        songs.add(new Song(
                                songObj.optString("id", ""),
                                songObj.optString("title", "Unknown title"),
                                songObj.optString("artist", "Unknown artist"),
                                songObj.optString("mediaUrl", ""),
                                songObj.optString("thumbnailUrl", ""),
                                songObj.optLong("durationMs", 0L)
                        ));
                    }
                }

                if (!id.isEmpty()) {
                    result.add(new Playlist(id, name, songs));
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private void writePlaylists(List<Playlist> playlists) {
        migrateLegacyPlaylistsIfNeeded();
        JSONArray playlistArray = new JSONArray();

        for (Playlist playlist : playlists) {
            JSONObject playlistObj = new JSONObject();
            JSONArray songsArray = new JSONArray();

            try {
                playlistObj.put("id", playlist.getId());
                playlistObj.put("name", playlist.getName());

                for (Song song : playlist.getSongs()) {
                    JSONObject songObj = new JSONObject();
                    songObj.put("id", song.getId());
                    songObj.put("title", song.getTitle());
                    songObj.put("artist", song.getArtist());
                    songObj.put("mediaUrl", song.getMediaUrl());
                    songObj.put("thumbnailUrl", song.getThumbnailUrl());
                    songObj.put("durationMs", song.getDurationMs());
                    songsArray.put(songObj);
                }

                playlistObj.put("songs", songsArray);
                playlistArray.put(playlistObj);
            } catch (JSONException ignored) {
            }
        }

        JSONObject playlistsByAccount = readPlaylistsByAccountObject();
        try {
            playlistsByAccount.put(getCurrentAccountKey(), playlistArray);
        } catch (JSONException ignored) {
        }

        prefs.edit().putString(KEY_PLAYLISTS_BY_ACCOUNT, playlistsByAccount.toString()).apply();
    }

    private JSONObject readPlaylistsByAccountObject() {
        String raw = prefs.getString(KEY_PLAYLISTS_BY_ACCOUNT, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private String getCurrentAccountKey() {
        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        if (session == null || !session.isSignedIn()) {
            return ACCOUNT_GUEST;
        }

        String email = safe(session.getEmail());
        return email.isEmpty() ? ACCOUNT_GUEST : "user:" + email;
    }

    private void migrateLegacyPlaylistsIfNeeded() {
        boolean alreadyMigrated = prefs.getBoolean(KEY_LEGACY_MIGRATED, false);
        if (alreadyMigrated) {
            return;
        }

        String legacyRaw = prefs.getString(KEY_PLAYLISTS, null);
        JSONObject playlistsByAccount = readPlaylistsByAccountObject();
        if (legacyRaw != null) {
            try {
                playlistsByAccount.put(getCurrentAccountKey(), new JSONArray(legacyRaw));
            } catch (JSONException ignored) {
            }
        }

        prefs.edit()
                .putString(KEY_PLAYLISTS_BY_ACCOUNT, playlistsByAccount.toString())
                .putBoolean(KEY_LEGACY_MIGRATED, true)
                .remove(KEY_PLAYLISTS)
                .apply();
    }
}

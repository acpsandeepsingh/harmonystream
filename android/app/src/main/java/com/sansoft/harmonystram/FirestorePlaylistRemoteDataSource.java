package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FirestorePlaylistRemoteDataSource {
    private static final String PREFS_NAME = "playlist_sync_remote_cache";
    private static final String KEY_USERS = "users";

    private final SharedPreferences prefs;

    public FirestorePlaylistRemoteDataSource(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized PlaylistSyncModels.PlaylistSnapshot pull(String accountKey) {
        JSONObject users = readUsers();
        JSONObject account = users.optJSONObject(accountKey);
        if (account == null) {
            return new PlaylistSyncModels.PlaylistSnapshot(new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
        }

        List<PlaylistSyncModels.PlaylistRecord> playlists = new ArrayList<>();
        JSONArray playlistArray = account.optJSONArray("playlists");
        if (playlistArray != null) {
            for (int i = 0; i < playlistArray.length(); i++) {
                JSONObject playlistObj = playlistArray.optJSONObject(i);
                if (playlistObj == null) continue;
                Playlist parsed = parsePlaylist(playlistObj);
                if (parsed == null) continue;
                long updatedAt = playlistObj.optLong("updatedAtMs", 0L);
                playlists.add(new PlaylistSyncModels.PlaylistRecord(parsed, updatedAt));
            }
        }

        List<String> tombstones = new ArrayList<>();
        JSONArray tombArray = account.optJSONArray("deletedPlaylistIds");
        if (tombArray != null) {
            for (int i = 0; i < tombArray.length(); i++) {
                String id = tombArray.optString(i, "");
                if (!id.isEmpty()) tombstones.add(id);
            }
        }

        return new PlaylistSyncModels.PlaylistSnapshot(playlists, tombstones, account.optLong("generatedAtMs", System.currentTimeMillis()));
    }

    public synchronized void push(String accountKey, PlaylistSyncModels.PlaylistSnapshot snapshot) {
        JSONObject users = readUsers();
        JSONObject account = new JSONObject();

        JSONArray playlistArray = new JSONArray();
        for (PlaylistSyncModels.PlaylistRecord record : snapshot.playlists) {
            if (record == null || record.playlist == null) continue;
            JSONObject playlistObj = serializePlaylist(record.playlist);
            try {
                playlistObj.put("updatedAtMs", record.updatedAtMs);
                playlistArray.put(playlistObj);
            } catch (JSONException ignored) {
            }
        }

        JSONArray tombArray = new JSONArray();
        for (String id : snapshot.deletedPlaylistIds) {
            tombArray.put(id);
        }

        try {
            account.put("generatedAtMs", snapshot.generatedAtMs);
            account.put("playlists", playlistArray);
            account.put("deletedPlaylistIds", tombArray);
            users.put(accountKey, account);
        } catch (JSONException ignored) {
        }

        prefs.edit().putString(KEY_USERS, users.toString()).apply();
    }

    private JSONObject readUsers() {
        String raw = prefs.getString(KEY_USERS, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private JSONObject serializePlaylist(Playlist playlist) {
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
        } catch (JSONException ignored) {
        }
        return playlistObj;
    }

    private Playlist parsePlaylist(JSONObject playlistObj) {
        String id = playlistObj.optString("id", "");
        if (id.isEmpty()) return null;
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
        return new Playlist(id, name, songs);
    }
}

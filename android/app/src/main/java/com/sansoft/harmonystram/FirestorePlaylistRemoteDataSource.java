package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FirestorePlaylistRemoteDataSource {
    private static final String TAG = "FirestoreRemoteSync";
    private static final String PREFS_NAME = "playlist_sync_remote_cache";
    private static final String KEY_USERS = "users";

    private final SharedPreferences prefs;

    public FirestorePlaylistRemoteDataSource(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized PlaylistSyncModels.PlaylistSnapshot pull(String accountKey) {
        PlaylistSyncModels.PlaylistSnapshot cloudSnapshot = pullFromFirestore(accountKey);
        if (cloudSnapshot != null) {
            cacheSnapshot(accountKey, cloudSnapshot);
            return cloudSnapshot;
        }
        return pullFromLocalCache(accountKey);
    }

    public synchronized void push(String accountKey, PlaylistSyncModels.PlaylistSnapshot snapshot) {
        boolean pushed = pushToFirestore(accountKey, snapshot);
        if (!pushed) {
            Log.w(TAG, "Firestore push failed, persisting to local cache fallback");
        }
        cacheSnapshot(accountKey, snapshot);
    }

    private PlaylistSyncModels.PlaylistSnapshot pullFromFirestore(String accountKey) {
        if (!isFirebaseConfigured()) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            String endpoint = firestoreDocumentEndpoint(accountKey);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return new PlaylistSyncModels.PlaylistSnapshot(new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
            }
            if (status < 200 || status >= 300) {
                Log.w(TAG, "Firestore pull failed with HTTP " + status);
                return null;
            }

            JSONObject body = new JSONObject(readFully(connection.getInputStream()));
            return parseFirestoreSnapshot(body);
        } catch (Exception error) {
            Log.w(TAG, "Firestore pull error", error);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean pushToFirestore(String accountKey, PlaylistSyncModels.PlaylistSnapshot snapshot) {
        if (!isFirebaseConfigured()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            String endpoint = firestoreDocumentEndpoint(accountKey);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("PATCH");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JSONObject payload = buildFirestorePayload(accountKey, snapshot);
            try (OutputStream output = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write(payload.toString());
                writer.flush();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String response = readFully(connection.getErrorStream());
                Log.w(TAG, "Firestore push failed with HTTP " + status + " body=" + response);
                return false;
            }
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Firestore push error", error);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private PlaylistSyncModels.PlaylistSnapshot parseFirestoreSnapshot(JSONObject response) {
        JSONObject fields = response.optJSONObject("fields");
        if (fields == null) {
            return new PlaylistSyncModels.PlaylistSnapshot(new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
        }

        long generatedAt = parseLongField(fields.optJSONObject("generatedAtMs"), System.currentTimeMillis());

        List<String> tombstones = new ArrayList<>();
        JSONObject deletedField = fields.optJSONObject("deletedPlaylistIds");
        if (deletedField != null) {
            JSONArray values = deletedField.optJSONObject("arrayValue") != null
                    ? deletedField.optJSONObject("arrayValue").optJSONArray("values")
                    : null;
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i);
                    if (value == null) continue;
                    String id = value.optString("stringValue", "");
                    if (!id.isEmpty()) tombstones.add(id);
                }
            }
        }

        List<PlaylistSyncModels.PlaylistRecord> playlists = new ArrayList<>();
        JSONObject playlistsField = fields.optJSONObject("playlists");
        if (playlistsField != null) {
            JSONArray values = playlistsField.optJSONObject("arrayValue") != null
                    ? playlistsField.optJSONObject("arrayValue").optJSONArray("values")
                    : null;
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    JSONObject wrapped = values.optJSONObject(i);
                    if (wrapped == null) continue;
                    JSONObject mapValue = wrapped.optJSONObject("mapValue");
                    if (mapValue == null) continue;
                    JSONObject playlistFields = mapValue.optJSONObject("fields");
                    if (playlistFields == null) continue;
                    Playlist playlist = parseFirestorePlaylist(playlistFields);
                    if (playlist == null) continue;
                    long updatedAt = parseLongField(playlistFields.optJSONObject("updatedAtMs"), 0L);
                    playlists.add(new PlaylistSyncModels.PlaylistRecord(playlist, updatedAt));
                }
            }
        }

        return new PlaylistSyncModels.PlaylistSnapshot(playlists, tombstones, generatedAt);
    }

    private Playlist parseFirestorePlaylist(JSONObject fields) {
        String id = parseStringField(fields.optJSONObject("id"), "");
        if (id.isEmpty()) return null;
        String name = parseStringField(fields.optJSONObject("name"), "Untitled Playlist");

        List<Song> songs = new ArrayList<>();
        JSONObject songsField = fields.optJSONObject("songs");
        if (songsField != null && songsField.optJSONObject("arrayValue") != null) {
            JSONArray values = songsField.optJSONObject("arrayValue").optJSONArray("values");
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    JSONObject wrappedSong = values.optJSONObject(i);
                    if (wrappedSong == null) continue;
                    JSONObject songMap = wrappedSong.optJSONObject("mapValue");
                    if (songMap == null) continue;
                    JSONObject songFields = songMap.optJSONObject("fields");
                    if (songFields == null) continue;
                    songs.add(new Song(
                            parseStringField(songFields.optJSONObject("id"), ""),
                            parseStringField(songFields.optJSONObject("title"), "Unknown title"),
                            parseStringField(songFields.optJSONObject("artist"), "Unknown artist"),
                            parseStringField(songFields.optJSONObject("mediaUrl"), ""),
                            parseStringField(songFields.optJSONObject("thumbnailUrl"), ""),
                            parseLongField(songFields.optJSONObject("durationMs"), 0L)
                    ));
                }
            }
        }

        return new Playlist(id, name, songs);
    }

    private JSONObject buildFirestorePayload(String accountKey, PlaylistSyncModels.PlaylistSnapshot snapshot) throws JSONException {
        JSONObject fields = new JSONObject();
        fields.put("accountKey", stringField(accountKey));
        fields.put("generatedAtMs", integerField(snapshot.generatedAtMs));

        JSONArray playlistValues = new JSONArray();
        for (PlaylistSyncModels.PlaylistRecord record : snapshot.playlists) {
            if (record == null || record.playlist == null) continue;
            JSONObject playlistFields = new JSONObject();
            playlistFields.put("id", stringField(record.playlist.getId()));
            playlistFields.put("name", stringField(record.playlist.getName()));
            playlistFields.put("updatedAtMs", integerField(record.updatedAtMs));

            JSONArray songValues = new JSONArray();
            for (Song song : record.playlist.getSongs()) {
                JSONObject songFields = new JSONObject();
                songFields.put("id", stringField(song.getId()));
                songFields.put("title", stringField(song.getTitle()));
                songFields.put("artist", stringField(song.getArtist()));
                songFields.put("mediaUrl", stringField(song.getMediaUrl()));
                songFields.put("thumbnailUrl", stringField(song.getThumbnailUrl()));
                songFields.put("durationMs", integerField(song.getDurationMs()));

                JSONObject songMapValue = new JSONObject();
                songMapValue.put("fields", songFields);

                JSONObject wrappedSong = new JSONObject();
                wrappedSong.put("mapValue", songMapValue);
                songValues.put(wrappedSong);
            }

            JSONObject songsArrayField = new JSONObject();
            songsArrayField.put("values", songValues);
            playlistFields.put("songs", arrayField(songsArrayField));

            JSONObject playlistMapValue = new JSONObject();
            playlistMapValue.put("fields", playlistFields);

            JSONObject wrappedPlaylist = new JSONObject();
            wrappedPlaylist.put("mapValue", playlistMapValue);
            playlistValues.put(wrappedPlaylist);
        }

        JSONObject playlistsArrayField = new JSONObject();
        playlistsArrayField.put("values", playlistValues);
        fields.put("playlists", arrayField(playlistsArrayField));

        JSONArray tombstoneValues = new JSONArray();
        for (String id : snapshot.deletedPlaylistIds) {
            tombstoneValues.put(stringField(id));
        }
        JSONObject tombstonesArrayField = new JSONObject();
        tombstonesArrayField.put("values", tombstoneValues);
        fields.put("deletedPlaylistIds", arrayField(tombstonesArrayField));

        JSONObject payload = new JSONObject();
        payload.put("fields", fields);
        return payload;
    }

    private void cacheSnapshot(String accountKey, PlaylistSyncModels.PlaylistSnapshot snapshot) {
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

    private PlaylistSyncModels.PlaylistSnapshot pullFromLocalCache(String accountKey) {
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

    private boolean isFirebaseConfigured() {
        return !safe(BuildConfig.FIREBASE_PROJECT_ID).isEmpty() && !safe(BuildConfig.FIREBASE_API_KEY).isEmpty();
    }

    private String firestoreDocumentEndpoint(String accountKey) {
        String encodedAccountKey = UriEncoder.encode(accountKey);
        return "https://firestore.googleapis.com/v1/projects/"
                + BuildConfig.FIREBASE_PROJECT_ID
                + "/databases/(default)/documents/native_sync/"
                + encodedAccountKey
                + "?key=" + BuildConfig.FIREBASE_API_KEY;
    }

    private String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private JSONObject stringField(String value) throws JSONException {
        JSONObject field = new JSONObject();
        field.put("stringValue", safe(value));
        return field;
    }

    private JSONObject integerField(long value) throws JSONException {
        JSONObject field = new JSONObject();
        field.put("integerValue", String.valueOf(value));
        return field;
    }

    private JSONObject arrayField(JSONObject arrayValue) throws JSONException {
        JSONObject field = new JSONObject();
        field.put("arrayValue", arrayValue);
        return field;
    }

    private String parseStringField(JSONObject field, String fallback) {
        if (field == null) return fallback;
        String value = field.optString("stringValue", fallback);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private long parseLongField(JSONObject field, long fallback) {
        if (field == null) return fallback;
        String raw = field.optString("integerValue", "");
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class UriEncoder {
        static String encode(String value) {
            if (value == null || value.isEmpty()) {
                return "guest";
            }
            return value.replace("%", "%25")
                    .replace("/", "%2F")
                    .replace("?", "%3F")
                    .replace("#", "%23")
                    .replace(" ", "%20");
        }
    }
}

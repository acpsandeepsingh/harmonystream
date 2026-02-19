package com.sansoft.harmonystram;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FirebaseSongRepository implements SongRepository, HomeCatalogRepository {

    private static final String SOURCE_FIREBASE = "firebase";

    @Override
    public List<SearchResult> search(String query, int maxResults, String source) throws Exception {
        String normalizedSource = source == null || source.trim().isEmpty() ? SOURCE_FIREBASE : source.trim().toLowerCase(Locale.US);
        if (!SOURCE_FIREBASE.equals(normalizedSource)) {
            return new ArrayList<>();
        }

        List<Song> songs = fetchSongs(maxResults);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalizedQuery.isEmpty()) {
            List<SearchResult> all = new ArrayList<>();
            for (Song song : songs) {
                all.add(new SearchResult(song, SOURCE_FIREBASE));
            }
            return all;
        }

        List<SearchResult> matches = new ArrayList<>();
        for (Song song : songs) {
            if (matches.size() >= maxResults) break;
            String title = song.getTitle() == null ? "" : song.getTitle().toLowerCase(Locale.US);
            String artist = song.getArtist() == null ? "" : song.getArtist().toLowerCase(Locale.US);
            String id = song.getId() == null ? "" : song.getId().toLowerCase(Locale.US);

            if (title.contains(normalizedQuery) || artist.contains(normalizedQuery) || id.contains(normalizedQuery)) {
                matches.add(new SearchResult(song, SOURCE_FIREBASE));
            }
        }
        return matches;
    }

    @Override
    public Song getVideoDetails(String videoId) throws Exception {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new IllegalArgumentException("videoId is required");
        }

        String endpoint = baseDocumentsEndpoint() + "/" + encode(videoId.trim()) + "?key=" + BuildConfig.FIREBASE_API_KEY;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Firestore request failed with HTTP " + status);
            }
            String payload = readFully(connection.getInputStream());
            JSONObject document = new JSONObject(payload);
            Song song = mapDocumentToSong(document);
            if (song == null) {
                throw new IllegalStateException("Song not found for id: " + videoId);
            }
            return song;
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public List<Song> loadHomeCatalog(int maxResults) throws Exception {
        return fetchSongs(maxResults);
    }

    private List<Song> fetchSongs(int maxResults) throws Exception {
        int pageSize = Math.max(1, Math.min(maxResults, 50));
        String endpoint = baseDocumentsEndpoint() + "?pageSize=" + pageSize + "&orderBy=title_lowercase&key=" + BuildConfig.FIREBASE_API_KEY;

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Firestore request failed with HTTP " + status);
            }

            String payload = readFully(connection.getInputStream());
            JSONObject response = new JSONObject(payload);
            JSONArray docs = response.optJSONArray("documents");
            List<Song> songs = new ArrayList<>();
            if (docs == null) {
                return songs;
            }

            for (int i = 0; i < docs.length(); i++) {
                JSONObject document = docs.optJSONObject(i);
                Song song = mapDocumentToSong(document);
                if (song != null) {
                    songs.add(song);
                }
            }
            return songs;
        } finally {
            connection.disconnect();
        }
    }

    private Song mapDocumentToSong(JSONObject document) {
        if (document == null) return null;

        JSONObject fields = document.optJSONObject("fields");
        if (fields == null) return null;

        String title = stringField(fields, "title", "Untitled");
        String artist = stringField(fields, "artist", "Unknown Artist");
        String thumbnail = stringField(fields, "thumbnailUrl", "");
        String videoId = stringField(fields, "videoId", "");
        String id = stringField(fields, "id", videoId);
        if (id.isEmpty()) {
            id = parseIdFromName(document.optString("name", ""));
        }
        if (videoId.isEmpty()) {
            videoId = id;
        }
        if (id.isEmpty() || videoId.isEmpty()) {
            return null;
        }

        long durationSeconds = longField(fields, "duration", 0L);
        long durationMs = durationSeconds > 0 ? durationSeconds * 1000L : longField(fields, "durationMs", 0L);
        String mediaUrl = "https://www.youtube.com/watch?v=" + videoId;

        return new Song(id, title, artist, mediaUrl, thumbnail, durationMs);
    }

    private String parseIdFromName(String name) {
        if (name == null || name.isEmpty()) return "";
        int slash = name.lastIndexOf('/');
        if (slash < 0 || slash >= name.length() - 1) return "";
        return name.substring(slash + 1);
    }

    private String stringField(JSONObject fields, String key, String fallback) {
        JSONObject field = fields.optJSONObject(key);
        if (field == null) return fallback;
        String value = field.optString("stringValue", "").trim();
        return value.isEmpty() ? fallback : value;
    }

    private long longField(JSONObject fields, String key, long fallback) {
        JSONObject field = fields.optJSONObject(key);
        if (field == null) return fallback;
        String value = field.optString("integerValue", "").trim();
        if (value.isEmpty()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String baseDocumentsEndpoint() {
        return "https://firestore.googleapis.com/v1/projects/"
                + BuildConfig.FIREBASE_PROJECT_ID
                + "/databases/(default)/documents/songs";
    }

    private String encode(String value) {
        return value.replace("%", "%25")
                .replace("/", "%2F")
                .replace("?", "%3F")
                .replace("#", "%23")
                .replace(" ", "%20");
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}

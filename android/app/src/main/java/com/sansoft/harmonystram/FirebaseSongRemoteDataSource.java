package com.sansoft.harmonystram;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FirebaseSongRemoteDataSource {

    public boolean upsertSong(Song song, NativeUserSessionStore.UserSession session) {
        if (song == null || song.getId() == null || song.getId().trim().isEmpty()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            String endpoint = "https://firestore.googleapis.com/v1/projects/"
                    + BuildConfig.FIREBASE_PROJECT_ID
                    + "/databases/(default)/documents/songs/"
                    + encode(song.getId())
                    + "?key=" + BuildConfig.FIREBASE_API_KEY;

            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("PATCH");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            if (session != null && session.getIdToken() != null && !session.getIdToken().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + session.getIdToken());
            }

            String resolvedVideoId = extractYouTubeVideoId(song.getMediaUrl(), song.getId());
            JSONObject fields = new JSONObject();
            fields.put("id", stringField(song.getId()));
            fields.put("songId", stringField(song.getId()));
            fields.put("videoId", stringField(resolvedVideoId));
            fields.put("title", stringField(song.getTitle()));
            fields.put("artist", stringField(song.getArtist()));
            fields.put("album", stringField(""));
            fields.put("mediaUrl", stringField(song.getMediaUrl()));
            fields.put("audioUrl", stringField(song.getMediaUrl()));
            fields.put("thumbnailUrl", stringField(song.getThumbnailUrl()));
            fields.put("duration", integerField(song.getDurationMs() / 1000L));
            fields.put("durationMs", integerField(song.getDurationMs()));
            fields.put("genre", stringField(safe(song.getGenre()).isEmpty() ? "Music" : song.getGenre()));
            fields.put("year", integerField(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)));
            fields.put("title_lowercase", stringField(safe(song.getTitle()).toLowerCase()));
            fields.put("title_keywords", stringArrayField(safe(song.getTitle()).toLowerCase().split("\\s+")));
            fields.put("search_keywords", stringArrayField(buildSearchKeywords(song)));

            JSONObject payload = new JSONObject();
            payload.put("fields", fields);

            try (OutputStream output = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write(payload.toString());
                writer.flush();
            }

            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String[] buildSearchKeywords(Song song) {
        String title = safe(song.getTitle()).toLowerCase();
        String artist = safe(song.getArtist()).toLowerCase();
        java.util.LinkedHashSet<String> keywords = new java.util.LinkedHashSet<>();
        addKeywordTokens(keywords, title);
        addKeywordTokens(keywords, artist);
        return keywords.toArray(new String[0]);
    }

    private void addKeywordTokens(java.util.LinkedHashSet<String> target, String value) {
        if (value == null || value.trim().isEmpty()) return;
        String[] words = value.replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            target.add(word);
            for (int i = 2; i < word.length(); i++) {
                target.add(word.substring(0, i));
            }
        }
    }

    private JSONObject stringField(String value) throws Exception {
        JSONObject field = new JSONObject();
        field.put("stringValue", safe(value));
        return field;
    }

    private JSONObject integerField(long value) throws Exception {
        JSONObject field = new JSONObject();
        field.put("integerValue", String.valueOf(value));
        return field;
    }

    private JSONObject stringArrayField(String[] values) throws Exception {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) continue;
                JSONObject item = new JSONObject();
                item.put("stringValue", value.trim());
                array.put(item);
            }
        }
        JSONObject arrayValue = new JSONObject();
        arrayValue.put("values", array);
        JSONObject field = new JSONObject();
        field.put("arrayValue", arrayValue);
        return field;
    }

    private String extractYouTubeVideoId(String mediaUrl, String fallbackId) {
        String normalized = safe(mediaUrl);
        if (!normalized.isEmpty()) {
            int watchIndex = normalized.indexOf("v=");
            if (watchIndex >= 0) {
                String candidate = normalized.substring(watchIndex + 2);
                int amp = candidate.indexOf('&');
                if (amp >= 0) candidate = candidate.substring(0, amp);
                if (isLikelyYouTubeVideoId(candidate)) return candidate;
            }

            String[] markers = new String[]{"youtu.be/", "youtube.com/embed/", "youtube.com/shorts/"};
            for (String marker : markers) {
                int idx = normalized.indexOf(marker);
                if (idx < 0) continue;
                String candidate = normalized.substring(idx + marker.length());
                int slash = candidate.indexOf('/');
                if (slash >= 0) candidate = candidate.substring(0, slash);
                int q = candidate.indexOf('?');
                if (q >= 0) candidate = candidate.substring(0, q);
                if (isLikelyYouTubeVideoId(candidate)) return candidate;
            }
        }

        String fallback = safe(fallbackId);
        return isLikelyYouTubeVideoId(fallback) ? fallback : "";
    }

    private boolean isLikelyYouTubeVideoId(String value) {
        return value != null && value.trim().matches("[A-Za-z0-9_-]{11}");
    }

    private String encode(String value) {
        if (value == null || value.isEmpty()) {
            return "song";
        }
        return value.replace("%", "%25")
                .replace("/", "%2F")
                .replace("?", "%3F")
                .replace("#", "%23")
                .replace(" ", "%20");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

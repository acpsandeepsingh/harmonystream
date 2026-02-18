package com.sansoft.harmonystram;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class YouTubeRepository {

    private static final String YOUTUBE_SEARCH_API_URL = "https://www.googleapis.com/youtube/v3/search";

    public interface SongFetchCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    public List<Song> searchSongs(String query, int maxResults) throws Exception {
        String apiKey = BuildConfig.YOUTUBE_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || "YOUR_YOUTUBE_API_KEY_HERE".equals(apiKey)) {
            throw new IllegalStateException("Missing YouTube API key. Set YOUTUBE_API_KEY in local.properties or env.");
        }

        StringBuilder urlBuilder = new StringBuilder(YOUTUBE_SEARCH_API_URL)
                .append("?part=snippet")
                .append("&maxResults=").append(Math.max(1, Math.min(maxResults, 50)))
                .append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()))
                .append("&type=video")
                .append("&videoCategoryId=10")
                .append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name()));

        HttpURLConnection connection = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        int responseCode = connection.getResponseCode();
        InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        String responseBody = readStream(stream);

        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("YouTube API error (" + responseCode + "): " + responseBody);
        }

        JSONObject json = new JSONObject(responseBody);
        JSONArray items = json.optJSONArray("items");
        List<Song> songs = new ArrayList<>();

        if (items == null) {
            return songs;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject idObj = item.optJSONObject("id");
            JSONObject snippet = item.optJSONObject("snippet");
            if (idObj == null || snippet == null) continue;

            String videoId = idObj.optString("videoId", "");
            if (videoId.isEmpty()) continue;

            String title = snippet.optString("title", "Untitled");
            String artist = snippet.optString("channelTitle", "Unknown Artist");

            JSONObject thumbnails = snippet.optJSONObject("thumbnails");
            String thumbnailUrl = "";
            if (thumbnails != null) {
                JSONObject high = thumbnails.optJSONObject("high");
                if (high != null) {
                    thumbnailUrl = high.optString("url", "");
                }
            }

            // For Phase 1 we keep a valid playable URL field placeholder.
            // Full official YouTube-native playback handling is implemented in later phases.
            String mediaUrl = "https://www.youtube.com/watch?v=" + videoId;
            songs.add(new Song(videoId, title, artist, mediaUrl, thumbnailUrl));
        }

        return songs;
    }

    private String readStream(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}

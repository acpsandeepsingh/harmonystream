package com.sansoft.harmonystram;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class YouTubeRepository implements SongRepository {

    private final YouTubeApiClient apiClient;

    public YouTubeRepository() {
        this(new YouTubeApiClient());
    }

    public YouTubeRepository(YouTubeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws Exception {
        JSONObject json = apiClient.searchVideos(query, maxResults);
        JSONArray items = json.optJSONArray("items");
        List<SearchResult> results = new ArrayList<>();

        if (items == null) {
            return results;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            JSONObject idObj = item.optJSONObject("id");
            JSONObject snippet = item.optJSONObject("snippet");
            if (idObj == null || snippet == null) continue;

            String videoId = idObj.optString("videoId", "");
            if (videoId.isEmpty()) continue;

            String title = snippet.optString("title", "Untitled");
            String artist = snippet.optString("channelTitle", "Unknown Artist");
            String thumbnailUrl = extractThumbnailUrl(snippet.optJSONObject("thumbnails"));

            Song song = new Song(
                    videoId,
                    title,
                    artist,
                    "https://www.youtube.com/watch?v=" + videoId,
                    thumbnailUrl,
                    0L
            );
            results.add(new SearchResult(song, "youtube"));
        }

        return results;
    }

    @Override
    public Song getVideoDetails(String videoId) throws Exception {
        JSONObject json = apiClient.getVideoDetails(videoId);
        JSONArray items = json.optJSONArray("items");
        if (items == null || items.length() == 0) {
            throw new IllegalStateException("No video details found for id: " + videoId);
        }

        JSONObject video = items.getJSONObject(0);
        JSONObject snippet = video.optJSONObject("snippet");
        JSONObject contentDetails = video.optJSONObject("contentDetails");

        String title = snippet != null ? snippet.optString("title", "Untitled") : "Untitled";
        String artist = snippet != null ? snippet.optString("channelTitle", "Unknown Artist") : "Unknown Artist";
        String thumbnailUrl = snippet != null ? extractThumbnailUrl(snippet.optJSONObject("thumbnails")) : "";
        long durationMs = parseDurationMs(contentDetails != null ? contentDetails.optString("duration", "") : "");

        return new Song(
                videoId,
                title,
                artist,
                "https://www.youtube.com/watch?v=" + videoId,
                thumbnailUrl,
                durationMs
        );
    }

    private String extractThumbnailUrl(JSONObject thumbnails) {
        if (thumbnails == null) return "";

        JSONObject maxres = thumbnails.optJSONObject("maxres");
        if (maxres != null && !maxres.optString("url", "").isEmpty()) {
            return maxres.optString("url", "");
        }

        JSONObject high = thumbnails.optJSONObject("high");
        if (high != null && !high.optString("url", "").isEmpty()) {
            return high.optString("url", "");
        }

        JSONObject medium = thumbnails.optJSONObject("medium");
        if (medium != null && !medium.optString("url", "").isEmpty()) {
            return medium.optString("url", "");
        }

        JSONObject def = thumbnails.optJSONObject("default");
        return def != null ? def.optString("url", "") : "";
    }

    private long parseDurationMs(String isoDuration) {
        if (isoDuration == null || isoDuration.isEmpty() || !isoDuration.startsWith("PT")) {
            return 0L;
        }

        long hours = 0L;
        long minutes = 0L;
        long seconds = 0L;

        String value = isoDuration.substring(2);
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
                continue;
            }
            if (number.length() == 0) continue;

            long parsed = Long.parseLong(number.toString());
            if (c == 'H') {
                hours = parsed;
            } else if (c == 'M') {
                minutes = parsed;
            } else if (c == 'S') {
                seconds = parsed;
            }
            number.setLength(0);
        }

        return ((hours * 60L + minutes) * 60L + seconds) * 1000L;
    }
}

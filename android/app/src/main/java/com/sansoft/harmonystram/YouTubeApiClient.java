package com.sansoft.harmonystram;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class YouTubeApiClient {

    private static final String YOUTUBE_SEARCH_API_URL = "https://www.googleapis.com/youtube/v3/search";
    private static final String YOUTUBE_VIDEOS_API_URL = "https://www.googleapis.com/youtube/v3/videos";

    public JSONObject searchVideos(String query, int maxResults) throws Exception {
        String requestUrl = new StringBuilder(YOUTUBE_SEARCH_API_URL)
                .append("?part=snippet")
                .append("&maxResults=").append(Math.max(1, Math.min(maxResults, 50)))
                .append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()))
                .append("&type=video")
                .append("&videoCategoryId=10")
                .append("&key=").append(URLEncoder.encode(getApiKey(), StandardCharsets.UTF_8.name()))
                .toString();

        return executeRequest(requestUrl);
    }

    public JSONObject getVideoDetails(String videoId) throws Exception {
        String requestUrl = new StringBuilder(YOUTUBE_VIDEOS_API_URL)
                .append("?part=snippet,contentDetails")
                .append("&id=").append(URLEncoder.encode(videoId, StandardCharsets.UTF_8.name()))
                .append("&key=").append(URLEncoder.encode(getApiKey(), StandardCharsets.UTF_8.name()))
                .toString();

        return executeRequest(requestUrl);
    }

    private JSONObject executeRequest(String requestUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
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

        return new JSONObject(responseBody);
    }

    private String getApiKey() {
        String apiKey = BuildConfig.YOUTUBE_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || "YOUR_YOUTUBE_API_KEY_HERE".equals(apiKey)) {
            throw new IllegalStateException("Missing YouTube API key. Set YOUTUBE_API_KEY in local.properties or env.");
        }
        return apiKey;
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

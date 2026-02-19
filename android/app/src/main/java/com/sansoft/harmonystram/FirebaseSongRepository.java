package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FirebaseSongRepository implements SongRepository, HomeCatalogRepository {

    private static final String SOURCE_FIREBASE = "firebase";
    private static final Object HOME_CACHE_LOCK = new Object();
    private static final List<Song> HOME_CACHE = new ArrayList<>();
    private static final String PREFS_NAME = "firebase_song_repository";
    private static final String KEY_HOME_CACHE = "home_cache";
    private static final String[] HOME_GENRES = new String[] {"New Songs", "Bollywood", "Punjabi", "Indi-Pop", "Classical", "Sufi", "Ghazal"};

    private final SharedPreferences prefs;

    public FirebaseSongRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        warmMemoryCacheFromDisk();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults, String source) throws Exception {
        String normalizedSource = source == null || source.trim().isEmpty() ? SOURCE_FIREBASE : source.trim().toLowerCase(Locale.US);
        if (!SOURCE_FIREBASE.equals(normalizedSource)) {
            return new ArrayList<>();
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalizedQuery.isEmpty()) {
            List<Song> songs = fetchSongs(maxResults);
            cacheHomeSongs(songs);
            List<SearchResult> all = new ArrayList<>();
            for (Song song : songs) {
                all.add(new SearchResult(song, SOURCE_FIREBASE));
            }
            return all;
        }

        int limit = Math.max(1, Math.min(maxResults, 50));
        List<String> searchTerms = extractSearchTerms(normalizedQuery);
        if (searchTerms.isEmpty()) {
            return new ArrayList<>();
        }

        List<SearchCandidate> candidates = runKeywordSearchQuery(searchTerms, 50);
        Collections.sort(candidates, new Comparator<SearchCandidate>() {
            @Override
            public int compare(SearchCandidate left, SearchCandidate right) {
                if (left.matchScore != right.matchScore) {
                    return Integer.compare(right.matchScore, left.matchScore);
                }

                String leftTitle = left.song.getTitle() == null ? "" : left.song.getTitle();
                String rightTitle = right.song.getTitle() == null ? "" : right.song.getTitle();
                return leftTitle.compareToIgnoreCase(rightTitle);
            }
        });

        List<SearchResult> ranked = new ArrayList<>();
        for (SearchCandidate candidate : candidates) {
            if (ranked.size() >= limit) {
                break;
            }
            ranked.add(new SearchResult(candidate.song, SOURCE_FIREBASE));
        }
        return ranked;
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
        List<Song> cached = snapshotHomeCache();
        if (!cached.isEmpty()) {
            return limitSongs(cached, maxResults);
        }

        try {
            List<Song> fetchedFromGenreCache = fetchSongsFromGenreCache(Math.max(maxResults, 50));
            if (!fetchedFromGenreCache.isEmpty()) {
                cacheHomeSongs(fetchedFromGenreCache);
                return limitSongs(fetchedFromGenreCache, maxResults);
            }

            List<Song> fetched = fetchSongs(Math.max(maxResults, 50));
            cacheHomeSongs(fetched);
            return limitSongs(fetched, maxResults);
        } catch (Exception networkError) {
            cached = snapshotHomeCache();
            if (!cached.isEmpty()) {
                return limitSongs(cached, maxResults);
            }
            throw networkError;
        }
    }

    private List<Song> limitSongs(List<Song> songs, int maxResults) {
        if (songs == null || songs.isEmpty()) return new ArrayList<>();
        int limit = Math.max(1, maxResults);
        List<Song> filtered = new ArrayList<>();
        for (Song song : songs) {
            filtered.add(song);
            if (filtered.size() >= limit) {
                break;
            }
        }
        return filtered;
    }

    private List<Song> fetchSongsFromGenreCache(int maxResults) {
        int limit = Math.max(1, maxResults);
        Map<String, Song> uniqueSongs = new LinkedHashMap<>();

        for (String genre : HOME_GENRES) {
            for (String docId : buildCandidateGenreDocIds(genre)) {
                try {
                    SongCacheBatch batch = fetchGenreCacheDocument(docId);
                    if (batch.songs.isEmpty()) {
                        continue;
                    }

                    for (Song song : batch.songs) {
                        if (song == null || song.getId() == null || song.getId().trim().isEmpty()) continue;
                        String normalizedGenre = song.getGenre() == null || song.getGenre().trim().isEmpty()
                                ? genre
                                : song.getGenre().trim();
                        uniqueSongs.put(song.getId(), new Song(
                                song.getId(),
                                song.getTitle(),
                                song.getArtist(),
                                song.getMediaUrl(),
                                song.getThumbnailUrl(),
                                song.getDurationMs(),
                                normalizedGenre
                        ));
                        if (uniqueSongs.size() >= limit) {
                            return new ArrayList<>(uniqueSongs.values());
                        }
                    }
                    break;
                } catch (Exception ignored) {
                    // Try next candidate document id.
                }
            }
        }

        return new ArrayList<>(uniqueSongs.values());
    }

    private List<String> buildCandidateGenreDocIds(String genre) {
        String normalized = genre == null ? "" : genre.trim().toLowerCase(Locale.US);
        String kebab = normalized.replace("&", "and").replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        String compact = normalized.replaceAll("[^a-z0-9]", "");

        List<String> candidates = new ArrayList<>();
        candidates.add("genre-" + kebab);
        candidates.add("genre-" + compact);
        candidates.add(kebab);
        candidates.add(compact);
        return candidates;
    }

    private SongCacheBatch fetchGenreCacheDocument(String docId) throws Exception {
        String endpoint = baseApiCacheEndpoint() + "/" + encode(docId) + "?key=" + BuildConfig.FIREBASE_API_KEY;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);

        try {
            int status = connection.getResponseCode();
            if (status == 404) {
                return new SongCacheBatch(docId, new ArrayList<>());
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Genre cache request failed with HTTP " + status);
            }

            String payload = readFully(connection.getInputStream());
            JSONObject document = new JSONObject(payload);
            return parseSongCacheBatch(document, docId);
        } finally {
            connection.disconnect();
        }
    }

    private SongCacheBatch parseSongCacheBatch(JSONObject document, String fallbackDocId) {
        JSONObject fields = document == null ? null : document.optJSONObject("fields");
        if (fields == null) {
            return new SongCacheBatch(fallbackDocId, new ArrayList<>());
        }

        String query = stringField(fields, "query", fallbackDocId);
        JSONObject songsField = fields.optJSONObject("songs");
        if (songsField == null) {
            return new SongCacheBatch(query, new ArrayList<>());
        }

        JSONObject arrayValue = songsField.optJSONObject("arrayValue");
        JSONArray values = arrayValue == null ? null : arrayValue.optJSONArray("values");
        List<Song> songs = new ArrayList<>();
        if (values == null) {
            return new SongCacheBatch(query, songs);
        }

        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            JSONObject mapValue = item.optJSONObject("mapValue");
            JSONObject songFields = mapValue == null ? null : mapValue.optJSONObject("fields");
            if (songFields == null) continue;

            Song song = mapSongFromCacheFields(songFields);
            if (song != null) {
                songs.add(song);
            }
        }

        return new SongCacheBatch(query, songs);
    }

    private Song mapSongFromCacheFields(JSONObject fields) {
        String id = firstNonEmptyField(fields, "", "id", "videoId", "songId", "trackId");
        String videoId = firstNonEmptyField(fields, "", "videoId", "youtubeId", "ytId");
        if (id.isEmpty()) {
            id = videoId;
        }
        if (id.isEmpty()) {
            return null;
        }

        String title = firstNonEmptyField(fields, "Untitled", "title", "name", "trackTitle");
        String artist = firstNonEmptyField(fields, "Unknown Artist", "artist", "artistName", "singer");
        String genre = firstNonEmptyField(fields, "Music", "genre", "category");
        String thumbnail = firstNonEmptyField(fields, "", "thumbnailUrl", "imageUrl", "artworkUrl", "coverUrl");
        String mediaUrl = firstNonEmptyField(fields, "", "mediaUrl", "streamUrl", "audioUrl", "url");
        if (mediaUrl.isEmpty()) {
            String resolvedVideoId = videoId.isEmpty() ? (isLikelyYouTubeVideoId(id) ? id : "") : videoId;
            if (!resolvedVideoId.isEmpty()) {
                mediaUrl = "https://www.youtube.com/watch?v=" + resolvedVideoId;
            }
        }

        if (thumbnail.isEmpty()) {
            String resolvedVideoId = videoId.isEmpty() ? (isLikelyYouTubeVideoId(id) ? id : "") : videoId;
            if (!resolvedVideoId.isEmpty()) {
                thumbnail = "https://i.ytimg.com/vi/" + resolvedVideoId + "/hqdefault.jpg";
            }
        }

        long durationSeconds = longField(fields, "duration", 0L);
        long durationMs = durationSeconds > 0 ? durationSeconds * 1000L : longField(fields, "durationMs", 0L);

        return new Song(id, title, artist, mediaUrl, thumbnail, durationMs, genre);
    }

    private void cacheHomeSongs(List<Song> songs) {
        synchronized (HOME_CACHE_LOCK) {
            HOME_CACHE.clear();
            if (songs != null) {
                HOME_CACHE.addAll(songs);
            }
        }
        cacheHomeSongsToDisk(songs);
    }

    private List<Song> snapshotHomeCache() {
        synchronized (HOME_CACHE_LOCK) {
            if (!HOME_CACHE.isEmpty()) {
                return new ArrayList<>(HOME_CACHE);
            }
        }
        List<Song> diskCached = readHomeSongsFromDisk();
        if (!diskCached.isEmpty()) {
            synchronized (HOME_CACHE_LOCK) {
                HOME_CACHE.clear();
                HOME_CACHE.addAll(diskCached);
            }
        }
        return diskCached;
    }

    private void warmMemoryCacheFromDisk() {
        synchronized (HOME_CACHE_LOCK) {
            if (!HOME_CACHE.isEmpty()) {
                return;
            }
            HOME_CACHE.addAll(readHomeSongsFromDisk());
        }
    }

    private void cacheHomeSongsToDisk(List<Song> songs) {
        if (prefs == null) {
            return;
        }

        JSONArray cacheArray = new JSONArray();
        if (songs != null) {
            for (Song song : songs) {
                if (song == null) continue;
                try {
                    JSONObject songObj = new JSONObject();
                    songObj.putOpt("id", song.getId());
                    songObj.putOpt("title", song.getTitle());
                    songObj.putOpt("artist", song.getArtist());
                    songObj.putOpt("mediaUrl", song.getMediaUrl());
                    songObj.putOpt("thumbnailUrl", song.getThumbnailUrl());
                    songObj.putOpt("durationMs", song.getDurationMs());
                    songObj.putOpt("genre", song.getGenre());
                    cacheArray.put(songObj);
                } catch (JSONException ignored) {
                    // Skip malformed entries so cache writes never break playback flows.
                }
            }
        }

        prefs.edit().putString(KEY_HOME_CACHE, cacheArray.toString()).apply();
    }

    private List<Song> readHomeSongsFromDisk() {
        List<Song> songs = new ArrayList<>();
        if (prefs == null) {
            return songs;
        }

        String raw = prefs.getString(KEY_HOME_CACHE, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject songObj = array.optJSONObject(i);
                if (songObj == null) continue;
                String id = songObj.optString("id", "");
                if (id.isEmpty()) continue;
                songs.add(new Song(
                        id,
                        songObj.optString("title", "Untitled"),
                        songObj.optString("artist", "Unknown Artist"),
                        songObj.optString("mediaUrl", ""),
                        songObj.optString("thumbnailUrl", ""),
                        songObj.optLong("durationMs", 0L),
                        songObj.optString("genre", "Music")
                ));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return songs;
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

    private List<SearchCandidate> runKeywordSearchQuery(List<String> terms, int limit) throws Exception {
        String endpoint = "https://firestore.googleapis.com/v1/projects/"
                + BuildConfig.FIREBASE_PROJECT_ID
                + "/databases/(default)/documents:runQuery?key="
                + BuildConfig.FIREBASE_API_KEY;

        JSONObject requestBody = new JSONObject();
        JSONObject structuredQuery = new JSONObject();
        structuredQuery.put("from", new JSONArray().put(new JSONObject().put("collectionId", "songs")));
        structuredQuery.put("limit", limit);
        structuredQuery.put("where", new JSONObject().put("fieldFilter", new JSONObject()
                .put("field", new JSONObject().put("fieldPath", "search_keywords"))
                .put("op", "ARRAY_CONTAINS_ANY")
                .put("value", new JSONObject().put("arrayValue", new JSONObject().put("values", stringArrayValues(terms))))));
        requestBody.put("structuredQuery", structuredQuery);

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.getOutputStream().write(requestBody.toString().getBytes("UTF-8"));

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Firestore runQuery failed with HTTP " + status);
            }

            String payload = readFully(connection.getInputStream());
            JSONArray response = new JSONArray(payload);
            List<SearchCandidate> matches = new ArrayList<>();
            for (int i = 0; i < response.length(); i++) {
                JSONObject row = response.optJSONObject(i);
                if (row == null) continue;
                Song song = mapDocumentToSong(row.optJSONObject("document"));
                if (song == null) continue;

                Set<String> songKeywords = extractSearchKeywordsFromDocument(row.optJSONObject("document"));
                int score = computeRelevanceScore(songKeywords, terms);
                if (score > 0) {
                    matches.add(new SearchCandidate(song, score));
                }
            }
            return matches;
        } finally {
            connection.disconnect();
        }
    }

    private List<String> extractSearchTerms(String normalizedQuery) {
        String[] rawTerms = normalizedQuery.split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String term : rawTerms) {
            String clean = term == null ? "" : term.trim();
            if (clean.isEmpty()) continue;
            if (!terms.contains(clean)) {
                terms.add(clean);
            }
            if (terms.size() >= 10) {
                break;
            }
        }
        return terms;
    }

    private JSONArray stringArrayValues(List<String> terms) throws JSONException {
        JSONArray values = new JSONArray();
        if (terms == null) return values;
        for (String term : terms) {
            if (term == null || term.trim().isEmpty()) continue;
            values.put(new JSONObject().put("stringValue", term.trim()));
        }
        return values;
    }

    private Set<String> extractSearchKeywordsFromDocument(JSONObject document) {
        Set<String> keywords = new HashSet<>();
        if (document == null) return keywords;
        JSONObject fields = document.optJSONObject("fields");
        if (fields == null) return keywords;
        JSONObject keywordField = fields.optJSONObject("search_keywords");
        JSONObject arrayValue = keywordField == null ? null : keywordField.optJSONObject("arrayValue");
        JSONArray values = arrayValue == null ? null : arrayValue.optJSONArray("values");
        if (values == null) return keywords;

        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String value = item.optString("stringValue", "").trim().toLowerCase(Locale.US);
            if (!value.isEmpty()) {
                keywords.add(value);
            }
        }
        return keywords;
    }

    private int computeRelevanceScore(Set<String> songKeywords, List<String> searchTerms) {
        if (songKeywords == null || songKeywords.isEmpty() || searchTerms == null || searchTerms.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String term : searchTerms) {
            if (songKeywords.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private Song mapDocumentToSong(JSONObject document) {
        if (document == null) return null;

        JSONObject fields = document.optJSONObject("fields");
        if (fields == null) return null;

        String title = firstNonEmptyField(fields, "Untitled", "title", "name", "trackTitle");
        String artist = firstNonEmptyField(fields, "Unknown Artist", "artist", "artistName", "singer");
        String thumbnail = firstNonEmptyField(fields, "", "thumbnailUrl", "imageUrl", "artworkUrl", "coverUrl");
        String mediaUrl = firstNonEmptyField(fields, "", "streamUrl", "audioUrl", "mediaUrl", "url");
        String videoId = firstNonEmptyField(fields, "", "videoId", "youtubeId", "ytId");
        String id = firstNonEmptyField(fields, videoId, "id", "songId", "trackId");
        String genre = firstNonEmptyField(fields, "Music", "genre", "category");
        if (id.isEmpty()) {
            id = parseIdFromName(document.optString("name", ""));
        }
        if (videoId.isEmpty()) {
            videoId = extractYouTubeVideoIdFromUrl(mediaUrl);
        }
        if (videoId.isEmpty()) {
            videoId = isLikelyYouTubeVideoId(id) ? id : "";
        }
        if (id.isEmpty()) {
            return null;
        }

        long durationSeconds = longField(fields, "duration", 0L);
        long durationMs = durationSeconds > 0 ? durationSeconds * 1000L : longField(fields, "durationMs", 0L);
        if (durationMs <= 0) {
            durationMs = longField(fields, "lengthMs", 0L);
        }
        if (mediaUrl.isEmpty() && !videoId.isEmpty()) {
            mediaUrl = "https://www.youtube.com/watch?v=" + videoId;
        }
        if (thumbnail.isEmpty() && !videoId.isEmpty()) {
            thumbnail = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        }
        if (mediaUrl.isEmpty()) {
            return null;
        }

        return new Song(id, title, artist, mediaUrl, thumbnail, durationMs, genre);
    }

    private String parseIdFromName(String name) {
        if (name == null || name.isEmpty()) return "";
        int slash = name.lastIndexOf('/');
        if (slash < 0 || slash >= name.length() - 1) return "";
        return name.substring(slash + 1);
    }

    private boolean isLikelyYouTubeVideoId(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9_-]{11}");
    }

    private String extractYouTubeVideoIdFromUrl(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) return "";
        String normalized = mediaUrl.trim();

        int watchIndex = normalized.indexOf("v=");
        if (watchIndex >= 0) {
            String candidate = normalized.substring(watchIndex + 2);
            int amp = candidate.indexOf('&');
            if (amp >= 0) candidate = candidate.substring(0, amp);
            return isLikelyYouTubeVideoId(candidate) ? candidate : "";
        }

        String[] markers = new String[] {"youtu.be/", "youtube.com/embed/", "youtube.com/shorts/"};
        for (String marker : markers) {
            int index = normalized.indexOf(marker);
            if (index < 0) continue;
            String candidate = normalized.substring(index + marker.length());
            int slash = candidate.indexOf('/');
            if (slash >= 0) candidate = candidate.substring(0, slash);
            int q = candidate.indexOf('?');
            if (q >= 0) candidate = candidate.substring(0, q);
            return isLikelyYouTubeVideoId(candidate) ? candidate : "";
        }
        return "";
    }

    private String stringField(JSONObject fields, String key, String fallback) {
        JSONObject field = fields.optJSONObject(key);
        if (field == null) return fallback;
        if (field.has("nullValue")) return fallback;
        String value = field.optString("stringValue", "").trim();
        if (value.isEmpty()) {
            value = field.optString("integerValue", "").trim();
        }
        if (value.isEmpty()) {
            value = field.optString("doubleValue", "").trim();
        }
        if (value.isEmpty() && field.has("booleanValue")) {
            value = String.valueOf(field.optBoolean("booleanValue", false));
        }
        return value.isEmpty() ? fallback : value;
    }

    private long longField(JSONObject fields, String key, long fallback) {
        JSONObject field = fields.optJSONObject(key);
        if (field == null) return fallback;
        if (field.has("nullValue")) return fallback;
        String value = field.optString("integerValue", "").trim();
        if (value.isEmpty()) {
            value = field.optString("doubleValue", "").trim();
        }
        if (value.isEmpty()) {
            value = field.optString("stringValue", "").trim();
        }
        if (value.isEmpty()) return fallback;
        try {
            return (long) Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String firstNonEmptyField(JSONObject fields, String fallback, String... keys) {
        if (fields == null || keys == null) return fallback;
        for (String key : keys) {
            String value = stringField(fields, key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    private static class SongCacheBatch {
        final String query;
        final List<Song> songs;

        SongCacheBatch(String query, List<Song> songs) {
            this.query = query == null ? "" : query;
            this.songs = songs == null ? new ArrayList<>() : songs;
        }
    }

    private static class SearchCandidate {
        final Song song;
        final int matchScore;

        SearchCandidate(Song song, int matchScore) {
            this.song = song;
            this.matchScore = matchScore;
        }
    }

    private String baseApiCacheEndpoint() {
        return "https://firestore.googleapis.com/v1/projects/"
                + BuildConfig.FIREBASE_PROJECT_ID
                + "/databases/(default)/documents/api_cache";
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

package com.sansoft.harmonystram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight structured event logging for playback lifecycle diagnostics.
 */
public class PlaybackEventLogger {

    private static final String TAG = "PlaybackEvent";
    private static final String PREFS_NAME = "playback_event_logs";
    private static final String KEY_RECENT_EVENTS = "recent_events";
    private static final String ENTRY_DELIMITER = "\n";
    private static final int MAX_EVENTS = 200;

    private final SharedPreferences sharedPreferences;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);

    public PlaybackEventLogger(Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void log(String eventName, Map<String, String> attributes) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("event", safeValue(eventName));
        payload.put("ts", timestampFormat.format(new Date()));
        if (attributes != null) {
            payload.putAll(attributes);
        }

        String line = toStructuredLine(payload);
        Log.i(TAG, line);
        persist(line);
    }

    private String toStructuredLine(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(" ");
            }
            builder.append(entry.getKey()).append("=").append(safeValue(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private void persist(String line) {
        String existing = sharedPreferences.getString(KEY_RECENT_EVENTS, "");
        List<String> events = new ArrayList<>();
        if (existing != null && !existing.isEmpty()) {
            String[] parts = existing.split(ENTRY_DELIMITER);
            for (String part : parts) {
                if (!part.isEmpty()) {
                    events.add(part);
                }
            }
        }

        events.add(line);
        if (events.size() > MAX_EVENTS) {
            events = events.subList(events.size() - MAX_EVENTS, events.size());
        }

        StringBuilder builder = new StringBuilder();
        for (String event : events) {
            builder.append(event).append(ENTRY_DELIMITER);
        }

        sharedPreferences.edit().putString(KEY_RECENT_EVENTS, builder.toString()).apply();
    }

    private String safeValue(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return value.replace(" ", "_");
    }
}


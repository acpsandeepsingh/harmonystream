package com.sansoft.harmonystram;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates persisted playback diagnostics to provide a lightweight soak-test gate summary.
 */
public class PlaybackSoakGateEvaluator {

    public static class GateResult {
        public final boolean pass;
        public final String summary;

        GateResult(boolean pass, String summary) {
            this.pass = pass;
            this.summary = summary;
        }
    }

    private final PlaybackEventLogger logger;

    public PlaybackSoakGateEvaluator(PlaybackEventLogger logger) {
        this.logger = logger;
    }

    public GateResult evaluate() {
        List<String> events = logger.getRecentEvents();
        if (events.isEmpty()) {
            return new GateResult(false, "Soak gate: no playback diagnostics yet");
        }

        int eventCount = events.size();
        boolean hasPause = containsEvent(events, "activity_pause");
        boolean hasResume = containsEvent(events, "activity_resume");
        boolean hasPersistedSession = containsEvent(events, "session_persisted");
        boolean hasQueueTransition = containsEvent(events, "queue_next") || containsEvent(events, "queue_previous");
        boolean hasNotificationAction = containsEvent(events, "pending_media_action");

        int score = 0;
        if (eventCount >= 25) score++;
        if (hasPause && hasResume) score++;
        if (hasPersistedSession) score++;
        if (hasQueueTransition) score++;
        if (hasNotificationAction) score++;

        boolean pass = score >= 4;
        List<String> checkpoints = new ArrayList<>();
        checkpoints.add("events=" + eventCount);
        checkpoints.add("bg_resume=" + passFail(hasPause && hasResume));
        checkpoints.add("session_save=" + passFail(hasPersistedSession));
        checkpoints.add("queue_nav=" + passFail(hasQueueTransition));
        checkpoints.add("notif_resume=" + passFail(hasNotificationAction));

        String label = pass ? "Soak gate: pass" : "Soak gate: in progress";
        return new GateResult(pass, label + " · " + join(checkpoints));
    }

    public void clearDiagnostics() {
        logger.clear();
    }

    private boolean containsEvent(List<String> events, String eventName) {
        String marker = "event=" + eventName;
        for (String line : events) {
            if (line != null && line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String passFail(boolean value) {
        return value ? "ok" : "pending";
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }
}

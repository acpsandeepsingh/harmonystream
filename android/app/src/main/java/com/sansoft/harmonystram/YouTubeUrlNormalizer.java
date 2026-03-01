package com.sansoft.harmonystram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YouTubeUrlNormalizer {
    private static final Pattern SHORT_URL = Pattern.compile("(?:https?://)?(?:www\\.)?youtu\\.be/([A-Za-z0-9_-]{6,})");
    private static final Pattern EMBED_URL = Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/embed/([A-Za-z0-9_-]{6,})");

    private YouTubeUrlNormalizer() {}

    static String normalizeWatchUrl(String videoIdOrUrl) {
        if (videoIdOrUrl == null) return "";

        String input = videoIdOrUrl.trim();
        if (input.isEmpty()) return "";

        if (input.startsWith("http://") || input.startsWith("https://")) {
            Matcher shortMatcher = SHORT_URL.matcher(input);
            if (shortMatcher.find()) {
                return "https://www.youtube.com/watch?v=" + shortMatcher.group(1);
            }

            Matcher embedMatcher = EMBED_URL.matcher(input);
            if (embedMatcher.find()) {
                return "https://www.youtube.com/watch?v=" + embedMatcher.group(1);
            }

            return input;
        }

        return "https://www.youtube.com/watch?v=" + input;
    }
}

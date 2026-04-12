package com.sansoft.harmonystram;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Standalone stream extraction helper to keep playback service focused on playback state.
 */
final class YouTubeStreamExtractor {

    static final String EXTRACTOR_USER_AGENT =
            "com.google.android.youtube/19.09.37 (Linux; U; Android 12) gzip";

    static final class ExtractionResult {
        final String streamUrl;
        @Nullable final String audioStreamUrl;
        @Nullable final String videoStreamUrl;

        ExtractionResult(String streamUrl, @Nullable String audioStreamUrl, @Nullable String videoStreamUrl) {
            this.streamUrl = streamUrl;
            this.audioStreamUrl = audioStreamUrl;
            this.videoStreamUrl = videoStreamUrl;
        }
    }

    ExtractionResult extract(String videoId, boolean preferVideo, int attempt) throws Exception {
        if (isDirectStreamUrl(videoId)) {
            String direct = videoId == null ? null : videoId.trim();
            return new ExtractionResult(direct, direct, direct);
        }

        StreamingService yt = ServiceList.YouTube;
        StreamInfo info = resolveStreamInfo(yt, videoId);

        List<AudioStream> audioStreams = info.getAudioStreams();
        List<VideoStream> videoStreams = info.getVideoStreams();

        String audioCandidate = pickPreferredAudioStream(audioStreams);
        String videoCandidate = pickPreferredVideoStream(videoStreams);
        String hlsCandidate = pickHlsStream(info);

        String selected = preferVideo
                ? firstPlayable(videoCandidate, audioCandidate, hlsCandidate)
                : firstPlayable(audioCandidate, hlsCandidate, videoCandidate);

        if (!isLikelyPlayableUrl(selected)) {
            throw new IllegalStateException("Extractor returned an invalid stream URL"
                    + " [attempt=" + attempt
                    + ", audioStreams=" + (audioStreams == null ? 0 : audioStreams.size())
                    + ", videoStreams=" + (videoStreams == null ? 0 : videoStreams.size())
                    + ", hls=" + (hlsCandidate != null)
                    + ", preferVideo=" + preferVideo + "]");
        }

        return new ExtractionResult(selected, audioCandidate, videoCandidate);
    }

    private StreamInfo resolveStreamInfo(StreamingService yt, String videoIdOrUrl) throws Exception {
        String normalized = YouTubeUrlNormalizer.normalizeWatchUrl(videoIdOrUrl);
        try {
            return StreamInfo.getInfo(yt, normalized);
        } catch (Throwable normalizedFailure) {
            if (normalized.equals(videoIdOrUrl)) {
                throw normalizedFailure;
            }
            // Fallback to the original source in case upstream extractor logic
            // is stricter for specific URL variants.
            return StreamInfo.getInfo(yt, videoIdOrUrl);
        }
    }

    @Nullable
    private String pickPreferredVideoStream(@Nullable List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) return null;
        String nonThrottled = null;
        String fallback = null;
        for (VideoStream stream : videoStreams) {
            if (stream == null || stream.getContent() == null) continue;
            String url = stream.getContent();
            if (!isLikelyPlayableUrl(url)) continue;

            // Prefer progressive video+audio stream URLs for reliability with ExoPlayer.
            if (stream.isVideoOnly()) {
                if (fallback == null) fallback = url;
                continue;
            }

            // Prefer non-throttled stream URLs when possible.
            if (!isPotentiallyThrottledStream(url)) {
                nonThrottled = url;
                break;
            }

            if (fallback == null) {
                fallback = url;
            }
        }
        return nonThrottled != null ? nonThrottled : fallback;
    }

    @Nullable
    private String pickPreferredAudioStream(@Nullable List<AudioStream> streams) {
        if (streams == null || streams.isEmpty()) return null;

        List<AudioStream> ranked = new ArrayList<>(streams);
        Collections.sort(ranked, Comparator.comparingInt(this::audioPreferenceScore));
        for (AudioStream stream : ranked) {
            if (stream == null) continue;
            String url = stream.getContent();
            if (isLikelyPlayableUrl(url)) return url;
        }
        return null;
    }

    @Nullable
    private String pickHlsStream(@Nullable StreamInfo info) {
        if (info == null) return null;
        try {
            Method m = info.getClass().getMethod("getHlsUrl");
            Object value = m.invoke(info);
            if (value instanceof String && isLikelyPlayableUrl((String) value)) {
                return (String) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int audioPreferenceScore(@Nullable AudioStream stream) {
        if (stream == null) return Integer.MAX_VALUE;
        String url = stream.getContent();
        if (!isLikelyPlayableUrl(url)) return Integer.MAX_VALUE - 1;

        int score = 100;
        int itag = stream.getItag();
        if (itag == 251) score -= 30; // opus webm
        if (itag == 140) score -= 25; // m4a fallback

        String format = safeLower(stream.getFormat() != null ? stream.getFormat().name() : null);
        if (format.contains("webm")) score -= 10;
        if (format.contains("m4a")) score -= 8;

        long bitrate = stream.getAverageBitrate();
        if (bitrate > 0) {
            long bitrateDelta = Math.abs(bitrate - 128_000L);
            score += (int) Math.min(40L, bitrateDelta / 8_000L);
        }

        if (isPotentiallyThrottledStream(url)) score += 15;
        return score;
    }

    private boolean isPotentiallyThrottledStream(@Nullable String streamUrl) {
        if (streamUrl == null) return true;
        String value = streamUrl.toLowerCase();
        return value.contains("&n=") || value.contains("?n=") || value.contains("&c=web") || value.contains("?c=web");
    }

    @Nullable
    private String firstPlayable(@Nullable String... candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            if (isLikelyPlayableUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isDirectStreamUrl(@Nullable String source) {
        if (!isLikelyPlayableUrl(source)) return false;
        String value = source.trim();
        try {
            URI uri = URI.create(value);
            String host = safeLower(uri.getHost());
            String path = safeLower(uri.getPath());
            if (host.contains("googlevideo.com") && path.contains("videoplayback")) return true;
            if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4") || path.endsWith(".m4a")) {
                return true;
            }
            // Non-YouTube hosts may already point to a playable media stream.
            return !host.contains("youtube.com") && !host.contains("youtu.be");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isLikelyPlayableUrl(@Nullable String streamUrl) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) return false;
        String url = streamUrl.trim().toLowerCase();
        return url.startsWith("https://") || url.startsWith("http://");
    }

    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase();
    }
}

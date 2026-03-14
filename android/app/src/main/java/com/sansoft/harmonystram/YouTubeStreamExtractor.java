package com.sansoft.harmonystram;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

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
        StreamingService yt = ServiceList.YouTube;
        StreamInfo info = resolveStreamInfo(yt, videoId);

        List<AudioStream> audioStreams = info.getAudioStreams();
        List<VideoStream> videoStreams = info.getVideoStreams();

        String audioCandidate = pickPreferredAudioStream(audioStreams);
        String videoCandidate = pickPreferredVideoStream(videoStreams);

        String selected = preferVideo
                ? firstPlayable(videoCandidate, audioCandidate)
                : firstPlayable(audioCandidate, videoCandidate);

        if (!isLikelyPlayableUrl(selected)) {
            throw new IllegalStateException("Extractor returned an invalid stream URL"
                    + " [attempt=" + attempt
                    + ", audioStreams=" + (audioStreams == null ? 0 : audioStreams.size())
                    + ", videoStreams=" + (videoStreams == null ? 0 : videoStreams.size())
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

            // Prefer non-progressive/adaptive streams that avoid throttling markers when possible.
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
        for (AudioStream stream : streams) {
            if (stream == null) continue;
            if (stream.getItag() == 140 && isLikelyPlayableUrl(stream.getContent())) {
                return stream.getContent();
            }
        }
        for (AudioStream stream : streams) {
            if (stream != null && isLikelyPlayableUrl(stream.getContent())) {
                return stream.getContent();
            }
        }
        return null;
    }

    private boolean isPotentiallyThrottledStream(@Nullable String streamUrl) {
        if (streamUrl == null) return true;
        String value = streamUrl.toLowerCase();
        return value.contains("&n=") || value.contains("?n=") || value.contains("&c=web") || value.contains("?c=web");
    }

    @Nullable
    private String firstPlayable(@Nullable String primary, @Nullable String fallback) {
        if (isLikelyPlayableUrl(primary)) {
            return primary;
        }
        if (isLikelyPlayableUrl(fallback)) {
            return fallback;
        }
        return null;
    }

    private boolean isLikelyPlayableUrl(@Nullable String streamUrl) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) return false;
        String url = streamUrl.trim().toLowerCase();
        return url.startsWith("https://") || url.startsWith("http://");
    }
}

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
        String videoCandidate = pickPlayableVideo(videoStreams);

        String selected = preferVideo ? firstPlayable(videoCandidate, audioCandidate)
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
        try {
            return StreamInfo.getInfo(yt, videoIdOrUrl);
        } catch (Throwable directFailure) {
            String normalized = YouTubeUrlNormalizer.normalizeWatchUrl(videoIdOrUrl);
            if (normalized.equals(videoIdOrUrl)) throw directFailure;
            return StreamInfo.getInfo(yt, normalized);
        }
    }

    @Nullable
    private String pickPlayableVideo(@Nullable List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) return null;
        for (VideoStream stream : videoStreams) {
            if (stream == null || stream.getContent() == null) continue;
            if (isLikelyPlayableUrl(stream.getContent())) {
                return stream.getContent();
            }
        }
        return null;
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

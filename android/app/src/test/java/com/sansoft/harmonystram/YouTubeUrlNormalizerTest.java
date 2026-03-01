package com.sansoft.harmonystram;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YouTubeUrlNormalizerTest {

    @Test
    public void normalizeWatchUrl_keepsWatchUrl() {
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        assertEquals(url, YouTubeUrlNormalizer.normalizeWatchUrl(url));
    }

    @Test
    public void normalizeWatchUrl_convertsVideoId() {
        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                YouTubeUrlNormalizer.normalizeWatchUrl("dQw4w9WgXcQ"));
    }

    @Test
    public void normalizeWatchUrl_convertsShortUrl() {
        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                YouTubeUrlNormalizer.normalizeWatchUrl("https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    public void normalizeWatchUrl_convertsEmbedUrl() {
        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                YouTubeUrlNormalizer.normalizeWatchUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"));
    }
}

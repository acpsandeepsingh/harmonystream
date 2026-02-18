package com.sansoft.harmonystram;

public class SearchResult {
    private final Song song;
    private final String source;

    public SearchResult(Song song, String source) {
        this.song = song;
        this.source = source;
    }

    public Song getSong() {
        return song;
    }

    public String getSource() {
        return source;
    }
}

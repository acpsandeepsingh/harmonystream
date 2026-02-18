package com.sansoft.harmonystram;

public class Song {
    private final String id;
    private final String title;
    private final String artist;
    private final String mediaUrl;
    private final String thumbnailUrl;

    public Song(String id, String title, String artist, String mediaUrl, String thumbnailUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.mediaUrl = mediaUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}

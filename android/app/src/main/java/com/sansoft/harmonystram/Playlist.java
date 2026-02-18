package com.sansoft.harmonystram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Playlist {
    private final String id;
    private final String name;
    private final List<Song> songs;

    public Playlist(String id, String name, List<Song> songs) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>(songs);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Song> getSongs() {
        return Collections.unmodifiableList(songs);
    }
}

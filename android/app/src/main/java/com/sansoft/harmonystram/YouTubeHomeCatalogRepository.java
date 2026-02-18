package com.sansoft.harmonystram;

import java.util.ArrayList;
import java.util.List;

public class YouTubeHomeCatalogRepository implements HomeCatalogRepository {

    private static final String DEFAULT_HOME_QUERY = "trending music";

    private final SongRepository songRepository;

    public YouTubeHomeCatalogRepository() {
        this(new YouTubeRepository());
    }

    public YouTubeHomeCatalogRepository(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Override
    public List<Song> loadHomeCatalog(int maxResults) throws Exception {
        List<SearchResult> searchResults = songRepository.search(DEFAULT_HOME_QUERY, maxResults, "youtube");
        List<Song> songs = new ArrayList<>(searchResults.size());

        for (SearchResult result : searchResults) {
            songs.add(result.getSong());
        }

        return songs;
    }
}

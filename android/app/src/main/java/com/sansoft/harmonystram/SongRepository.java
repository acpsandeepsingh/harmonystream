package com.sansoft.harmonystram;

import java.util.List;

public interface SongRepository {
    List<SearchResult> search(String query, int maxResults, String source) throws Exception;
    Song getVideoDetails(String videoId) throws Exception;
}

package com.sansoft.harmonystram;

import java.util.List;

public interface SongRepository {
    List<SearchResult> search(String query, int maxResults) throws Exception;
    Song getVideoDetails(String videoId) throws Exception;
}

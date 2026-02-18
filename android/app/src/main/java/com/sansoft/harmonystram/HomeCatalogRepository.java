package com.sansoft.harmonystram;

import java.util.List;

public interface HomeCatalogRepository {
    List<Song> loadHomeCatalog(int maxResults) throws Exception;
}

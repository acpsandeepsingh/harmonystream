export interface Song {
  id: string;
  title: string;
  artist: string;
  album: string;
  year: number;
  duration: number; // in seconds
  genre: string;
  thumbnailUrl: string;
  videoId: string;
  title_lowercase?: string;
  title_keywords?: string[];
  search_keywords?: string[];
}

export interface Playlist {
  id: string;
  name: string;
  description: string;
  songs: Song[];
  userId?: string;
}

export interface SearchResult {
  id: {
    videoId: string;
  };
  snippet: {
    publishedAt: string;
    title: string;
    channelTitle: string;
    thumbnails: {
      high: {
        url: string;
      };
    };
  };
}

export interface ApiCacheEntry {
  query: string;
  timestamp: string; // ISO 8601 string
  songs: Song[];
}

declare global {
    interface Window {
        YT: any;
    }
}

import type { Song, SearchResult } from '@/lib/types';

const YOUTUBE_SEARCH_API_URL = 'https://www.googleapis.com/youtube/v3/search';
const YOUTUBE_VIDEOS_API_URL = 'https://www.googleapis.com/youtube/v3/videos';

// This interface is for the results from the /videos endpoint
interface VideoDetailsResult {
  id: string;
  snippet: {
    publishedAt: string;
    title: string;
    channelTitle: string;
    thumbnails: {
      high: {
        url: string;
      };
    };
    categoryId: string;
  };
  contentDetails: {
    duration: string; // ISO 8601 duration
  };
}

/**
 * Parses an ISO 8601 duration string (e.g., "PT2M30S") into seconds.
 * @param duration The ISO 8601 duration string.
 * @returns The total duration in seconds.
 */
function parseISO8601Duration(duration: string): number {
    const match = duration.match(/PT(\d+H)?(\d+M)?(\d+S)?/);
    if (!match) return 0;

    match.shift(); // Remove full match

    const [hours, minutes, seconds] = match.map(part => parseInt(part) || 0);

    return hours * 3600 + minutes * 60 + seconds;
}


export async function searchYoutube(
    query: string, 
    genreForMetadata: string = '',
    orderBy?: 'viewCount' | 'relevance' | 'date'
): Promise<Song[]> {
    const apiKey = process.env.NEXT_PUBLIC_YOUTUBE_API_KEY;
    if (!apiKey || apiKey === 'YOUR_YOUTUBE_API_KEY_HERE') {
      throw new Error('YouTube API key is not set on the client. Please add it to your .env file.');
    }

    // Step 1: Use the Search API to get a list of video IDs.
    const searchParams = new URLSearchParams({
        part: 'snippet',
        maxResults: '50',
        q: query,
        type: 'video',
        videoCategoryId: '10', // Music Category
        key: apiKey,
    });

    if (orderBy) {
        searchParams.set('order', orderBy);
    }
    
    const searchResponse = await fetch(`${YOUTUBE_SEARCH_API_URL}?${searchParams.toString()}`);
    
    if (!searchResponse.ok) {
        const errorData = await searchResponse.json().catch(() => ({ message: searchResponse.statusText }));
        const errorMessage = errorData.error?.message || `HTTP error! status: ${searchResponse.status}`;
        if (errorMessage.includes('quota')) {
            throw new Error('YouTube API quota has been exceeded. Search is temporarily unavailable. Please try again later.');
        }
        throw new Error(errorMessage);
    }

    const searchData = await searchResponse.json();
    const videoIds = searchData.items
        ?.map((item: SearchResult) => item.id.videoId)
        .filter(Boolean);
        
    if (!videoIds || videoIds.length === 0) {
        return [];
    }

    // Step 2: Use the Videos API to get detailed information for the found IDs.
    const videoParams = new URLSearchParams({
        part: 'snippet,contentDetails',
        id: videoIds.join(','),
        key: apiKey,
    });

    const videoResponse = await fetch(`${YOUTUBE_VIDEOS_API_URL}?${videoParams.toString()}`);
    
    if (!videoResponse.ok) {
        const errorData = await videoResponse.json().catch(() => ({ message: videoResponse.statusText }));
        const errorMessage = errorData.error?.message || `HTTP error! status: ${videoResponse.status}`;
        throw new Error(errorMessage);
    }
    
    const videoData = await videoResponse.json();

    // Step 3: Map the detailed results to our Song objects.
    let fetchedSongs: Song[] = [];
    if (videoData.items) {
      fetchedSongs = videoData.items
        .map((item: VideoDetailsResult) => {
            const originalTitle = item.snippet.title || 'Untitled';
            
            // Attempt to parse "Artist - Title" from the video title for better metadata
            let artist = item.snippet.channelTitle || 'Unknown Artist';
            let title = originalTitle;
            
            const titleParts = originalTitle.split(' - ');
            if (titleParts.length === 2) {
                artist = titleParts[0].trim();
                title = titleParts[1].trim();
            } else {
                // Fallback for titles that don't match the pattern
                // Often the artist is in parentheses or brackets
                const artistMatch = originalTitle.match(/\((.*?)\)|\[(.*?)\]/);
                if(artistMatch) {
                    const potentialArtist = artistMatch[1] || artistMatch[2];
                    if(potentialArtist && potentialArtist.length < 30) { // Avoid long descriptions
                        artist = potentialArtist.trim();
                        title = originalTitle.replace(artistMatch[0], '').trim();
                    }
                }
            }


            const titleLower = title.toLowerCase();
            const keywords = titleLower.split(' ').filter(w => w);

            const searchKeywords = new Set<string>();
            const createSearchKeywords = (text: string) => {
                if (!text) return;
                const words = text.toLowerCase().replace(/[^a-z0-9\s]/gi, '').split(/\s+/).filter(w => w.length > 0);

                words.forEach(word => {
                    // Add the full word itself
                    searchKeywords.add(word);

                    // Generate prefixes for words longer than 2 characters.
                    // e.g., "nacho" -> "na", "nac", "nach"
                    if (word.length > 2) {
                        for (let i = 2; i < word.length; i++) {
                            searchKeywords.add(word.substring(0, i));
                        }
                    }
                });
            };

            createSearchKeywords(title);
            createSearchKeywords(artist);
            createSearchKeywords(genreForMetadata);

            return {
              id: item.id,
              videoId: item.id,
              title: title,
              artist: artist,
              thumbnailUrl: item.snippet.thumbnails.high.url,
              album: '',
              duration: parseISO8601Duration(item.contentDetails.duration),
              genre: genreForMetadata,
              year: item.snippet.publishedAt ? new Date(item.snippet.publishedAt).getFullYear() : new Date().getFullYear(),
              title_lowercase: titleLower,
              title_keywords: keywords,
              search_keywords: Array.from(searchKeywords),
            }
        }).filter((song: Song) => {
            // Filter out songs shorter than 90 seconds
            if (song.duration < 90) {
              return false;
            }
            // Filter out songs with Malayalam characters in the title.
            // The Unicode range for Malayalam is U+0D00 to U+0D7F.
            const malayalamRegex = /[\u0D00-\u0D7F]/;
            return !malayalamRegex.test(song.title);
        });
    }
    return fetchedSongs;
}

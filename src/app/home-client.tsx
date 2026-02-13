'use client';

import { useState, useEffect } from 'react';
import type { Song, ApiCacheEntry } from '@/lib/types';
import { Button } from '@/components/ui/button';
import { SongCard } from '@/components/song-card';
import { genres, songs as fallbackSongs } from '@/lib/data';
import { Skeleton } from '@/components/ui/skeleton';
import { usePlayer } from '@/contexts/player-context';
import { Play, Heart } from 'lucide-react';
import { useFirestore, useUser } from '@/firebase';
import { doc, setDoc, getDoc } from 'firebase/firestore';
import { searchYoutube } from '@/lib/youtube';
import { useToast } from '@/hooks/use-toast';
import { usePlaylists } from '@/contexts/playlist-context';
import { LIKED_SONGS_PLAYLIST_ID } from '@/lib/constants';

export default function HomePage() {
  const [selectedGenre, setSelectedGenre] = useState<string>('New Songs');
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);
  const { playPlaylist } = usePlayer();
  const { playlists } = usePlaylists();
  const firestore = useFirestore();
  const { user, isUserLoading } = useUser();
  const { toast } = useToast();

  const likedSongsPlaylist = playlists.find(p => p.id === LIKED_SONGS_PLAYLIST_ID);
  const likedSongs = likedSongsPlaylist?.songs || [];

  useEffect(() => {
    if (isUserLoading) {
      return;
    }

    const fetchPopularSongs = async () => {
      setLoading(true);
      setSongs([]); // Clear previous songs
      
      const query = selectedGenre === 'New Songs' 
        ? `latest indian songs`
        : `latest ${selectedGenre} songs`;
        
      try {
        const cacheDocId = `genre-${selectedGenre.toLowerCase().replace(/\s+/g, '-')}`;
        const cacheRef = doc(firestore, 'api_cache', cacheDocId);
        const cacheSnap = await getDoc(cacheRef);

        if (cacheSnap.exists()) {
          const cacheData = cacheSnap.data() as ApiCacheEntry;
          // For simplicity, we'll just check if the cache exists. A real app might check a timestamp.
          setSongs(cacheData.songs);
          setLoading(false);
          return;
        }
        
        const fetchedSongs = await searchYoutube(query, selectedGenre);
        setSongs(fetchedSongs);

        // Persist to cache and global songs collection only if user is signed in
        if (firestore && user) {
            // Save to cache
            const cacheEntry: ApiCacheEntry = { query: query, timestamp: new Date().toISOString(), songs: fetchedSongs };
            await setDoc(cacheRef, cacheEntry);

            // Save individual songs
            fetchedSongs.forEach(song => {
                const songRef = doc(firestore, 'songs', song.id);
                setDoc(songRef, song, { merge: true }).catch(e => {
                    console.warn(`Could not save song ${song.id} to the global collection`, e);
                });
            });
        }

      } catch (error: any) {
        console.error('Error fetching popular songs:', error);
        toast({
          variant: "destructive",
          title: "Could not fetch songs from YouTube",
          description: error.message || "Showing a selection of fallback songs instead.",
        });
        setSongs(fallbackSongs);
      } finally {
        setLoading(false);
      }
    };

    fetchPopularSongs();
  }, [selectedGenre, firestore, isUserLoading, user, toast]);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Top Music</h1>
        <p className="text-muted-foreground">Discover the most popular tracks of the week</p>
      </div>

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-headline font-semibold">Filter by Genre</h2>
            <Button 
                variant="default" 
                disabled={loading || songs.length === 0}
                onClick={() => playPlaylist(songs)}
            >
                <Play className="mr-2 h-4 w-4" />
                Play All
            </Button>
            <Button
              variant="outline"
              disabled={likedSongs.length === 0}
              onClick={() => playPlaylist(likedSongs)}
            >
              <Heart className="mr-2 h-4 w-4" />
              Play Liked
            </Button>
        </div>
        <div className="flex flex-wrap gap-2">
          {genres.map((genre) => (
            <Button
              key={genre}
              variant={selectedGenre === genre ? 'default' : 'secondary'}
              onClick={() => setSelectedGenre(genre)}
              disabled={loading}
            >
              {genre}
            </Button>
          ))}
        </div>
      </div>
      
      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {Array.from({ length: 50 }).map((_, index) => (
             <div key={index} className="space-y-2">
                <Skeleton className="aspect-square w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
            </div>
          ))}
        </div>
      ) : null}
      
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {songs.length > 0 ? songs.map((song) => (
          <SongCard key={song.id} song={song} onPlay={() => playPlaylist(songs, song.id)}/>
        )) : !loading ? <p>No songs found for this genre.</p> : null}
      </div>
    </div>
  );
}

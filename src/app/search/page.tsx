'use client';

import { useSearchParams } from 'next/navigation';
import { useState, useEffect, Suspense } from 'react';
import type { Song } from '@/lib/types';
import { SongCard } from '@/components/song-card';
import { Skeleton } from '@/components/ui/skeleton';
import { usePlayer } from '@/contexts/player-context';
import { useFirestore, useUser } from '@/firebase';
import { doc, setDoc, getDoc, collection, query as firestoreQuery, where, getDocs, limit } from 'firebase/firestore';
import { Button } from '@/components/ui/button';
import { Play, Plus, ListMusic, PlusCircle } from 'lucide-react';
import { usePlaylists } from '@/contexts/playlist-context';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuPortal,
} from '@/components/ui/dropdown-menu';
import { CreatePlaylistDialog } from '@/components/create-playlist-dialog';
import { searchYoutube } from '@/lib/youtube';

function SearchPageContent() {
  const searchParams = useSearchParams();
  const q = searchParams.get('q') || '';
  const source = searchParams.get('source') || 'database';
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { playPlaylist } = usePlayer();
  const { playlists, addSongsToPlaylist } = usePlaylists();
  const firestore = useFirestore();
  const { isUserLoading } = useUser();

  useEffect(() => {
    if (isUserLoading || !firestore) {
      return;
    }

    const fetchSearchResults = async () => {
      if (!q) {
        setSongs([]);
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      setSongs([]);

      // This helper function queries Firestore.
      const querySongs = async (searchableTerms: string[]) => {
        if (!firestore || searchableTerms.length === 0) return [];
        
        // Search will be performed on the `search_keywords` field for comprehensive results.
        const dbQuery = firestoreQuery(
            collection(firestore, 'songs'),
            where('search_keywords', 'array-contains-any', searchableTerms.slice(0, 10)),
            limit(50)
        );
        const snapshot = await getDocs(dbQuery);
        const dbSongs = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Song));

        // Score and sort results based on relevance.
        const scoredSongs = dbSongs.map(song => {
            const score = searchableTerms.reduce((acc, keyword) => {
                if (song.search_keywords?.includes(keyword)) {
                    return acc + 1;
                }
                return acc;
            }, 0);
            return { ...song, score };
        }).sort((a, b) => (b.score || 0) - (a.score || 0));
    
        return scoredSongs;
      }

      if (source === 'database') {
        try {
            const searchTerms = q.toLowerCase().split(' ').filter(w => w.length > 0);
            if (searchTerms.length === 0) {
                setSongs([]);
                setLoading(false);
                return;
            }
            const foundSongs = await querySongs(searchTerms);
            setSongs(foundSongs);
        } catch (error: any) {
            console.error('Error searching database:', error);
            setError(`Could not search the database. Details: ${error.message}`);
        } finally {
            setLoading(false);
        }
      } else if (source === 'youtube') {
        try {
          const fetchedSongs = await searchYoutube(q, '');
          setSongs(fetchedSongs);

          // Persist to global songs collection to enrich database
          if (firestore) {
            fetchedSongs.forEach(song => {
                const songRef = doc(firestore, 'songs', song.id);
                // Use setDoc with merge to avoid overwriting and to handle non-blocking errors
                setDoc(songRef, song, { merge: true }).catch(e => {
                    console.warn(`Could not save song ${song.id} to the global collection from search`, e);
                });
            });
          }

        } catch (error: any) {
          console.error('Error fetching search results:', error);
          setError(`Could not fetch search results. Details: ${error.message}`);
          setSongs([]);
        } finally {
          setLoading(false);
        }
      } else {
        // Fallback for any other source value
        setLoading(false);
        setSongs([]);
      }
    };

    fetchSearchResults();
  }, [q, source, firestore, isUserLoading]);
  
  const sourceNameMap: { [key: string]: string } = {
    database: 'in Database',
    youtube: 'on YouTube',
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-4xl font-headline font-bold text-foreground">
          Search Results for "{q}"
          <span className="text-lg font-normal text-muted-foreground ml-2">({sourceNameMap[source] || 'in Database'})</span>
        </h1>
        <div className="flex items-center gap-2">
          <Button onClick={() => playPlaylist(songs)} disabled={loading || songs.length === 0}>
              <Play className="mr-2 h-4 w-4" />
              Play All
          </Button>
          <DropdownMenu>
              <DropdownMenuTrigger asChild>
                  <Button variant="outline" disabled={loading || songs.length === 0}>
                      <Plus className="mr-2 h-4 w-4" />
                      Add to Playlist
                  </Button>
              </DropdownMenuTrigger>
              <DropdownMenuPortal>
                  <DropdownMenuContent align="end">
                      <DropdownMenuLabel>Add all to...</DropdownMenuLabel>
                      <DropdownMenuSeparator />
                      {playlists.map((playlist) => (
                      <DropdownMenuItem
                          key={playlist.id}
                          onClick={() => addSongsToPlaylist(playlist.id, songs)}
                      >
                          <ListMusic className="mr-2 h-4 w-4" />
                          <span>{playlist.name}</span>
                      </DropdownMenuItem>
                      ))}
                      {playlists.length > 0 && <DropdownMenuSeparator />}
                      <CreatePlaylistDialog>
                          <DropdownMenuItem onSelect={(e: Event) => e.preventDefault()}>
                              <PlusCircle className="mr-2 h-4 w-4" />
                              Create new playlist
                          </DropdownMenuItem>
                      </CreatePlaylistDialog>
                  </DropdownMenuContent>
              </DropdownMenuPortal>
          </DropdownMenu>
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
      ) : error ? (
        <div className="text-destructive bg-destructive/10 p-4 rounded-lg">{error}</div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {songs.length > 0 ? songs.map((song) => (
            <SongCard key={song.id} song={song} />
          )) : <p>No songs found for your search.</p>}
        </div>
      )}
    </div>
  );
}

export default function SearchPage() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <SearchPageContent />
        </Suspense>
    )
}

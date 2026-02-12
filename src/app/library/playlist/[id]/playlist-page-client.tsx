'use client';

import { useRouter } from 'next/navigation';
import { usePlaylists } from '@/contexts/playlist-context';
import { usePlayer } from '@/contexts/player-context';
import { SongCard } from '@/components/song-card';
import { Button } from '@/components/ui/button';
import { Play, Trash2, Heart } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { Playlist } from '@/lib/types';
import Image from 'next/image';
import { DeletePlaylistDialog } from '@/components/delete-playlist-dialog';
import { LIKED_SONGS_PLAYLIST_ID } from '@/lib/constants';

interface PlaylistPageClientProps {
  id: string;
}

export default function PlaylistPageClient({ id }: PlaylistPageClientProps) {
  const router = useRouter();
  const { playlists, deletePlaylist } = usePlaylists();
  const { playPlaylist } = usePlayer();
  const [playlist, setPlaylist] = useState<Playlist | null>(null);

  useEffect(() => {
    if (typeof id !== 'string') return;
    
    const foundPlaylist = playlists.find((p) => p.id === id);
    
    if (!foundPlaylist && id === LIKED_SONGS_PLAYLIST_ID) {
      // This handles the case where Liked Songs is empty and not yet in the main `playlists` array from context
      const likedSongsPlaylist = playlists.find(p => p.id === LIKED_SONGS_PLAYLIST_ID);
       setPlaylist(likedSongsPlaylist || { id: LIKED_SONGS_PLAYLIST_ID, name: 'Liked Songs', description: 'Your favorite tracks.', songs: [] });
    } else {
      setPlaylist(foundPlaylist || null);
    }
  }, [id, playlists, router]);

  useEffect(() => {
    // If the playlist is deleted (e.g. from another tab), it will become null
    if (!playlist && id) {
        const stillExists = playlists.some(p => p.id === id);
        if (!stillExists) {
            router.replace('/library');
        }
    }
  }, [playlists, id, playlist, router]);

  if (!playlist) {
    return (
      <div className="text-center py-16">
        <h1 className="text-2xl font-bold">Loading playlist...</h1>
        <p className="text-muted-foreground">This playlist might have been deleted or the link is incorrect.</p>
      </div>
    );
  }

  const handlePlay = () => {
    if (playlist.songs.length > 0) {
      playPlaylist(playlist.songs);
    }
  };

  const handleDelete = () => {
    if (playlist) {
      deletePlaylist(playlist.id);
      router.push('/library');
    }
  };

  const isLikedSongsPlaylist = playlist.id === LIKED_SONGS_PLAYLIST_ID;

  return (
    <div className="space-y-8">
      <div className="flex flex-col md:flex-row items-start gap-8">
         <div className="relative w-full md:w-48 md:h-48 aspect-square rounded-lg overflow-hidden shadow-lg">
           <Image 
             src={playlist.songs[0]?.thumbnailUrl || 'https://picsum.photos/seed/playlist/300/300'}
             alt={playlist.name}
             fill
             className="object-cover"
             data-ai-hint="playlist cover"
           />
         </div>
         <div className="flex-1 space-y-2">
            <p className="text-sm font-bold uppercase tracking-wider text-muted-foreground">Playlist</p>
            <h1 className="text-4xl lg:text-6xl font-headline font-bold text-foreground break-words flex items-center gap-4">
              {isLikedSongsPlaylist && <Heart className="w-10 h-10 lg:w-12 lg:h-12 text-red-500 fill-red-500" />}
              {playlist.name}
            </h1>
            <p className="text-muted-foreground">
                {isLikedSongsPlaylist && playlist.songs.length === 0 
                    ? 'Songs you like will appear here.' 
                    : playlist.description
                }
            </p>
            <p className="text-sm text-muted-foreground">{playlist.songs.length} songs</p>
            <div className="flex items-center gap-2">
                <Button onClick={handlePlay} disabled={playlist.songs.length === 0}>
                    <Play className="mr-2 h-4 w-4" />
                    Play
                </Button>
                {!isLikedSongsPlaylist && (
                    <DeletePlaylistDialog onConfirm={handleDelete} playlistName={playlist.name}>
                      <Button variant="destructive" size="icon">
                          <Trash2 className="h-4 w-4" />
                          <span className="sr-only">Delete Playlist</span>
                      </Button>
                    </DeletePlaylistDialog>
                )}
            </div>
         </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {playlist.songs.length > 0 ? (
          playlist.songs.map((song) => (
            <SongCard 
              key={song.id} 
              song={song} 
              onPlay={() => playPlaylist(playlist.songs, song.id)}
              playlistContext={{ playlistId: playlist.id }}
            />
          ))
        ) : (
          <div className="col-span-full text-center py-12">
            <p className="text-muted-foreground">This playlist is empty.</p>
            <p className="text-sm text-muted-foreground">Add some songs to get started.</p>
          </div>
        )}
      </div>
    </div>
  );
}

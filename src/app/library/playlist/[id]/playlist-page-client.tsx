'use client';

import { useMemo } from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { Heart, Play, Trash2 } from 'lucide-react';

import { usePlaylists } from '@/contexts/playlist-context';
import { usePlayer } from '@/contexts/player-context';
import { LIKED_SONGS_PLAYLIST_ID } from '@/lib/constants';
import { SongCard } from '@/components/song-card';
import { Button } from '@/components/ui/button';
import { Play, Trash2, Heart } from 'lucide-react';
import { useMemo } from 'react';
import Image from 'next/image';
import { DeletePlaylistDialog } from '@/components/delete-playlist-dialog';
import { Button } from '@/components/ui/button';

interface PlaylistPageClientProps {
  id: string;
}

export default function PlaylistPageClient({ id }: PlaylistPageClientProps) {
  const router = useRouter();
  const { playlists, deletePlaylist, isPlaylistsLoading } = usePlaylists();
  const { playPlaylist } = usePlayer();

  const playlist = useMemo(() => {
    if (!id) {
      return null;
    }

    const foundPlaylist = playlists.find((p) => p.id === id);
    if (foundPlaylist) {
      return foundPlaylist;
    }

    if (id === LIKED_SONGS_PLAYLIST_ID) {
      return {
        id: LIKED_SONGS_PLAYLIST_ID,
        name: 'Liked Songs',
        description: 'Your favorite tracks.',
        songs: [],
      };

    if (!foundPlaylist && id === LIKED_SONGS_PLAYLIST_ID) {
      return {
        id: LIKED_SONGS_PLAYLIST_ID,
        name: 'Liked Songs',
        description: 'Your favorite tracks.',
        songs: [],
      };
    }

  useEffect(() => {
    if (!id || playlist || id === LIKED_SONGS_PLAYLIST_ID) {
      return;
    }

    // Avoid redirecting during initial data hydration on refresh.
    if (playlists.length === 0) {
      return;
    }

    const stillExists = playlists.some((p) => p.id === id);
    if (!stillExists) {
      router.replace('/library');
    }

    return null;
  }, [id, playlists]);

  if (!id) {
    return (
      <div className="py-16 text-center">
         <h1 className="text-2xl font-bold">Playlist link is invalid.</h1>
        <p className="text-muted-foreground">Please open a playlist again from Your Library.</p>
      </div>
    );
  }

  if (isPlaylistsLoading) {
    return (
      <div className="py-16 text-center">
        <h1 className="text-2xl font-bold">Loading playlist...</h1>
      </div>
    );
  }

  if (!playlist) {
    return (
      <div className="text-center py-16 space-y-2">
        <h1 className="text-2xl font-bold">Playlist not found.</h1>
        <p className="text-muted-foreground">This playlist may have been deleted or is not available in this account.</p>
      </div>
    );
  }

  if (!playlist) {
    return (
      <div className="space-y-2 py-16 text-center">
        <h1 className="text-2xl font-bold">Playlist not found.</h1>
        <p className="text-muted-foreground">
          This playlist may have been deleted or is not available in this account.
        </p>
      </div>
    );
  }

  const isLikedSongsPlaylist = playlist.id === LIKED_SONGS_PLAYLIST_ID;

  const handlePlay = () => {
    if (playlist.songs.length > 0) {
      playPlaylist(playlist.songs);
    }
  };

  const handleDelete = () => {
    deletePlaylist(playlist.id);
    router.push('/library');
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-col items-start gap-8 md:flex-row">
        <div className="relative aspect-square w-full overflow-hidden rounded-lg shadow-lg md:h-48 md:w-48">
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

          <h1 className="flex items-center gap-4 break-words font-headline text-4xl font-bold text-foreground lg:text-6xl">
            {isLikedSongsPlaylist && (
              <Heart className="h-10 w-10 fill-red-500 text-red-500 lg:h-12 lg:w-12" />
            )}
            {playlist.name}
          </h1>

          <p className="text-muted-foreground">
            {isLikedSongsPlaylist && playlist.songs.length === 0
              ? 'Songs you like will appear here.'
              : playlist.description}
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

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
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
          <div className="col-span-full py-12 text-center">
            <p className="text-muted-foreground">This playlist is empty.</p>
            <p className="text-sm text-muted-foreground">Add some songs to get started.</p>
          </div>
        )}
      </div>
    </div>
  );
}

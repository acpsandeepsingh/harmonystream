'use client';

import Image from 'next/image';
import type { Song } from '@/lib/types';
import { usePlayer } from '@/contexts/player-context';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Play, MoreHorizontal, ListMusic, Plus, Trash2, PlusCircle, History } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuPortal,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { usePlaylists } from '@/contexts/playlist-context';
import { CreatePlaylistDialog } from './create-playlist-dialog';
import { cn } from '@/lib/utils';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

interface SongCardProps {
  song: Song;
  onPlay?: () => void;
  playlistContext?: {
    playlistId: string;
  };
}

export function SongCard({ song, onPlay, playlistContext }: SongCardProps) {
  const { currentTrack, isPlaying, playTrack, history } = usePlayer();
  const { playlists, addSongToPlaylist, removeSongFromPlaylist, isSongLiked } = usePlaylists();
  const isActive = currentTrack?.id === song.id && isPlaying;

  const hasBeenPlayed = history.some((playedSong) => playedSong.id === song.id);
  const isLiked = isSongLiked(song.id);

  const handlePlay = () => {
    if (onPlay) {
      onPlay();
    } else {
      playTrack(song);
    }
  };

  const handlePlayButtonClick = (e: React.MouseEvent) => {
    e.stopPropagation(); // Stop the click from bubbling up to the container
    handlePlay();
  };
  
  const handleOptionsClick = (e: React.MouseEvent) => {
    e.stopPropagation(); // Stop the click from bubbling up to the container
  };

  const renderMenu = () => {
    if (playlistContext) {
      return (
        <DropdownMenuItem onClick={() => removeSongFromPlaylist(playlistContext.playlistId, song.id)}>
          <Trash2 className="mr-2 h-4 w-4" />
          <span>Remove from playlist</span>
        </DropdownMenuItem>
      );
    }
    return (
      <DropdownMenuSub>
        <DropdownMenuSubTrigger>
          <ListMusic className="mr-2 h-4 w-4" />
          <span>Add to Playlist</span>
        </DropdownMenuSubTrigger>
        <DropdownMenuPortal>
          <DropdownMenuSubContent>
            {playlists.map((playlist) => (
              <DropdownMenuItem
                key={playlist.id}
                onClick={() => addSongToPlaylist(playlist.id, song)}
              >
                <Plus className="mr-2 h-4 w-4" />
                <span>{playlist.name}</span>
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <CreatePlaylistDialog>
                <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
                    <PlusCircle className="mr-2 h-4 w-4" />
                    Create new playlist
                </DropdownMenuItem>
            </CreatePlaylistDialog>
          </DropdownMenuSubContent>
        </DropdownMenuPortal>
      </DropdownMenuSub>
    );
  };

  return (
    <Card className="group relative overflow-hidden rounded-lg shadow-md transition-all hover:shadow-xl">
      <CardContent className="p-0">
        <div className="aspect-square relative cursor-pointer" onClick={handlePlay}>
          <Image
            src={song.thumbnailUrl}
            alt={song.title}
            fill
            className="object-cover transition-transform group-hover:scale-105"
            data-ai-hint="album art"
          />
          <div className="absolute inset-0 bg-black/20 group-hover:bg-black/40 transition-colors pointer-events-none" />
          
          <Button
            size="icon"
            variant={isActive ? 'default' : 'secondary'}
            className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 scale-0 group-hover:scale-100 transition-transform duration-300 rounded-full h-12 w-12"
            onClick={handlePlayButtonClick}
          >
            <Play className={`h-6 w-6 ${!isActive && 'ml-1'}`} />
          </Button>
          
          <div className="absolute top-2 right-2 z-10" onClick={handleOptionsClick}>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 rounded-full bg-black/30 text-white hover:bg-black/50 hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <MoreHorizontal className="h-4 w-4" />
                  <span className="sr-only">More options</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuPortal>
                <DropdownMenuContent align="end">{renderMenu()}</DropdownMenuContent>
              </DropdownMenuPortal>
            </DropdownMenu>
          </div>
        </div>
        <div className="p-3">
          <p className={cn('font-semibold truncate font-headline', isLiked && 'text-primary')}>{song.title}</p>
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground truncate">{song.artist}</p>
            {hasBeenPlayed && (
              <Tooltip>
                <TooltipTrigger>
                  <History className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                </TooltipTrigger>
                <TooltipContent>
                  <p>Played</p>
                </TooltipContent>
              </Tooltip>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

'use client';

import { usePlayer } from '@/contexts/player-context';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Slider } from '@/components/ui/slider';
import { Play, Pause, SkipBack, SkipForward, Volume2, Video, Music as MusicIcon, Plus, ListMusic, PlusCircle, Heart, X, Maximize2, Minimize2, History, GripVertical, Share2 } from 'lucide-react';
import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import YouTube from 'react-youtube';
import type { YouTubePlayer, YouTubeProps, YouTubeEvent } from 'react-youtube';
import { cn } from '@/lib/utils';
import { useToast } from '@/hooks/use-toast';
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
import { CreatePlaylistDialog } from './create-playlist-dialog';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import Image from 'next/image';
import type { Song } from '@/lib/types';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from '@/components/ui/tooltip';
import { usePathname } from 'next/navigation';
import { usePrevious } from '@/hooks/use-previous';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

declare global {
  interface Window {
    HarmonyNative?: {
      play?: () => void;
      pause?: () => void;
      next?: () => void;
      previous?: () => void;
      seek?: (positionMs: number) => void;
      setQueue?: (queueJson: string) => void;
      updateState?: (title: string, artist: string, playing: boolean, positionMs: number, durationMs: number, artworkBase64: string) => void;
      getState?: () => void;
    };
    __harmonyNativeApplyCommand?: (action: string) => void;
    __harmonyNativeManagedByApp?: boolean;
  }
}


const PortraitPlayer = React.memo(function PortraitPlayer({
  currentTrack,
  progress,
  duration,
  currentTime,
  isPlaying,
  isLiked,
  playerMode,
  volume,
  playlists,
  onToggleLike,
  onTogglePlayerMode,
  onPlayPrev,
  onPlayNext,
  onTogglePlayPause,
  onVolumeChange,
  onSeekChange,
  onSeekCommit,
  onAddToPlaylist,
  onShare,
  onOpenCreatePlaylistDialog,
  container,
}: any) {
  return (
    <div className={cn("md:hidden flex flex-col fixed bottom-0 left-0 right-0 z-50 transition-colors", "bg-background border-t text-foreground")}>
        {/* Row 1: Progress Bar */}
        <div className="w-full flex items-center gap-2 px-2 pt-2">
            <span className={cn("text-xs w-10 text-right", "text-muted-foreground")}>{formatDuration(currentTime)}</span>
            <Slider value={[progress]} max={100} step={1} onValueChange={onSeekChange} onValueCommit={onSeekCommit} className="w-full [&>span:first-of-type]:h-4 [&_.h-2]:h-3" />
            <span className={cn("text-xs w-10", "text-muted-foreground")}>{formatDuration(duration)}</span>
        </div>
        
        <div className="w-full flex items-center p-2">
            <div className='min-w-0 flex-1 flex items-center gap-3'>
                <Avatar className="h-12 w-12 rounded-md">
                    <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
                    <AvatarFallback>{currentTrack.artist.charAt(0)}</AvatarFallback>
                </Avatar>
                <div className='min-w-0'>
                    <p className="font-bold truncate text-base">{currentTrack.title}</p>
                    <p className={cn("truncate text-sm", "text-muted-foreground")}>{currentTrack.artist}</p>
                </div>
            </div>
            <div className="flex items-center shrink-0">
                <Button variant="ghost" size="icon" onClick={onToggleLike} className="h-8 w-8">
                    <Heart className={cn("h-5 w-5", isLiked && "fill-red-500 text-red-500")} />
                </Button>
                <SheetTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-8 w-8">
                        <ListMusic className="h-5 w-5" />
                    </Button>
                </SheetTrigger>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={(e) => e.stopPropagation()}><Plus className="h-5 w-5" /><span className="sr-only">Add to playlist</span></Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent container={container} align="end" forceMount>
                        <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
                        <DropdownMenuSeparator />
                        {playlists.map((playlist: any) => (<DropdownMenuItem key={playlist.id} onClick={() => onAddToPlaylist(playlist.id)}><ListMusic className="mr-2 h-4 w-4" /><span>{playlist.name}</span></DropdownMenuItem>))}
                        {playlists.length > 0 && <DropdownMenuSeparator />}
                        <DropdownMenuItem onSelect={() => onOpenCreatePlaylistDialog(true)}><PlusCircle className="mr-2 h-4 w-4" />Create new playlist</DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
                <Button variant="ghost" size="icon" onClick={onShare} className="h-8 w-8">
                    <Share2 className="h-5 w-5" />
                    <span className="sr-only">Share song</span>
                </Button>
            </div>
        </div>

        <div className="w-full flex items-center justify-between px-2 pb-2">
            <div className="w-20">
                <Button variant="ghost" size="icon" onClick={onTogglePlayerMode} className="h-8 w-8">
                    {playerMode === 'audio' ? <Video className="h-5 w-5" /> : <MusicIcon className="h-5 w-5" />}
                </Button>
            </div>
            <div className="flex items-center">
                <Button variant="ghost" size="icon" onClick={onPlayPrev}><SkipBack className="h-6 w-6" /></Button>
                <Button variant='ghost' size="icon" className="h-14 w-14" onClick={onTogglePlayPause}>
                    {isPlaying ? <Pause className="h-10 w-10" /> : <Play className="h-10 w-10 ml-1" />}
                </Button>
                <Button variant="ghost" size="icon" onClick={onPlayNext}><SkipForward className="h-6 w-6" /></Button>
            </div>
            <div className="flex items-center gap-1 w-20">
                <Volume2 className={cn("h-5 w-5", "text-muted-foreground")} />
                <Slider defaultValue={[volume]} max={100} step={1} className="w-full" onValueChange={(value) => onVolumeChange(value[0])} />
            </div>
        </div>
    </div>
  );
});
PortraitPlayer.displayName = 'PortraitPlayer';

const LandscapePlayer = React.memo(function LandscapePlayer({
  currentTrack,
  progress,
  duration,
  currentTime,
  isPlaying,
  isLiked,
  playerMode,
  volume,
  playlists,
  onToggleLike,
  onTogglePlayerMode,
  onPlayPrev,
  onPlayNext,
  onTogglePlayPause,
  onVolumeChange,
  onSeekChange,
  onSeekCommit,
  onAddToPlaylist,
  onShare,
  onOpenCreatePlaylistDialog,
  isPip,
  showControls,
  onResetControlsTimeout,
  container,
}: any) {
  // In video mode, this component is part of the fullscreen container. We don't use the outer fixed div.
  const PlayerContainer = 'div';
  const containerProps = {
    className: cn(
      'hidden md:block', // This is the key: always hide on mobile
      playerMode === 'audio' && 'w-full h-auto fixed bottom-0 left-0 right-0 z-[60]',
      playerMode === 'audio' && (isPip ? 'opacity-0 pointer-events-none' : 'opacity-100')
    ),
  };

  return (
    <PlayerContainer {...containerProps}>
      <div 
        className={cn(
           "w-full flex flex-col p-2 gap-1 transition-all duration-300",
           playerMode === 'video'
               ? "bg-gradient-to-t from-black/80 to-transparent text-white fixed bottom-0 left-0 right-0 z-[60]"
               : "border-t bg-card/95 backdrop-blur-xl text-foreground"
       )}
       onMouseMove={onResetControlsTimeout}
       >
         <div className={cn(
           "relative w-full flex items-center gap-2 px-2 transition-opacity duration-300 z-10",
           playerMode === 'video' && !showControls && 'opacity-0 pointer-events-none'
         )}>
             <span className="text-xs w-10 text-right text-muted-foreground">{formatDuration(currentTime)}</span>
             <Slider value={[progress]} max={100} step={1} onValueChange={onSeekChange} onValueCommit={onSeekCommit} className="w-full [&>span:first-of-type]:h-4 [&>span:first-of-type_>_.bg-primary]:h-1 [&>span:first-of-type_>_.bg-primary]:top-1/2 [&>span:first-of-type_>_.bg-primary]:-translate-y-1/2 [&_.h-2]:h-1" />
             <span className="text-xs w-10 text-muted-foreground">{formatDuration(duration)}</span>
         </div>
         <div className={cn("relative w-full flex items-center justify-between gap-2 transition-opacity duration-300 h-[64px] z-10", playerMode === 'video' && !showControls && 'opacity-0 pointer-events-none')}>
            <div className="flex items-center gap-3 min-w-0 w-1/3">
                <Avatar className="h-12 w-12 rounded-md">
                    <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
                    <AvatarFallback>{currentTrack.artist.charAt(0)}</AvatarFallback>
                </Avatar>
                <div className='min-w-0'>
                    <p className="font-bold truncate">{currentTrack.title}</p>
                    <p className="text-sm truncate text-muted-foreground">{currentTrack.artist}</p>
                </div>
                <Button variant="ghost" size="icon" onClick={onToggleLike}><Heart className={cn("h-5 w-5", isLiked && "fill-red-500 text-red-500")} /></Button>
            </div>
            <div className="flex items-center gap-4">
                <Button variant="ghost" size="icon" onClick={onPlayPrev}><SkipBack className="h-5 w-5" /></Button>
                <Button variant='default' size="icon" className="h-10 w-10" onClick={onTogglePlayPause}>{isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}</Button>
                <Button variant="ghost" size="icon" onClick={onPlayNext}><SkipForward className="h-5 w-5" /></Button>
            </div>
            <div className="flex items-center gap-2 w-1/3 justify-end">
                <Button variant="ghost" size="icon" onClick={onShare}>
                    <Share2 className="h-5 w-5" />
                    <span className="sr-only">Share</span>
                </Button>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon" onClick={(e) => e.stopPropagation()}><Plus className="h-5 w-5" /><span className="sr-only">Add to playlist</span></Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent container={container} align="end">
                        <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
                        <DropdownMenuSeparator />
                        {playlists.map((playlist: any) => (<DropdownMenuItem key={playlist.id} onClick={() => onAddToPlaylist(playlist.id)}><ListMusic className="mr-2 h-4 w-4" /><span>{playlist.name}</span></DropdownMenuItem>))}
                        {playlists.length > 0 && <DropdownMenuSeparator />}
                        <DropdownMenuItem onSelect={() => onOpenCreatePlaylistDialog(true)}><PlusCircle className="mr-2 h-4 w-4" />Create new playlist</DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
                <SheetTrigger asChild>
                    <Button variant="ghost" size="icon"><ListMusic className="h-5 w-5" /><span className="sr-only">Open Queue</span></Button>
                </SheetTrigger>
                <Button variant="ghost" size="icon" onClick={onTogglePlayerMode}>
                    {playerMode === 'audio' ? <Video className="h-5 w-5" /> : <MusicIcon className="h-5 w-5" />}
                </Button>
                <div className="flex items-center gap-2">
                    <Volume2 className="h-5 w-5 text-muted-foreground" />
                    <Slider defaultValue={[volume]} max={100} step={1} className="w-24" onValueChange={(value) => onVolumeChange(value[0])} />
                </div>
            </div>
         </div>
      </div>
    </PlayerContainer>
  );
});
LandscapePlayer.displayName = 'LandscapePlayer';

const formatDuration = (seconds: number) => {
    if (isNaN(seconds) || seconds < 0) return '0:00';
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = Math.floor(seconds % 60);
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
};

const SortableSongItem = ({
  song,
  isCurrent,
  isPlaying,
  isLiked,
  hasBeenPlayed,
  onPlay,
  onRemove,
}: {
  song: Song;
  isCurrent: boolean;
  isPlaying: boolean;
  isLiked: boolean;
  hasBeenPlayed: boolean;
  onPlay: () => void;
  onRemove: () => void;
}) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: song.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 10 : 'auto',
  };

  return (
    <div
      ref={setNodeRef}
      id={`queue-song-${song.id}`}
      style={style}
      className={cn(
        "group flex items-center gap-2 p-2 rounded-md hover:bg-muted",
        isCurrent && "bg-secondary"
      )}
    >
      <button {...attributes} {...listeners} className="p-2 cursor-grab rounded-md hover:bg-accent -ml-2">
        <GripVertical className="h-5 w-5 text-muted-foreground" />
        <span className="sr-only">Drag to reorder</span>
      </button>

      <div
        className="flex-1 flex items-center gap-4 min-w-0 cursor-pointer"
        onClick={onPlay}
      >
        <div className="relative h-12 w-12 shrink-0">
          <Image
            src={song.thumbnailUrl}
            alt={song.title}
            fill
            className="rounded-md object-cover"
          />
          {isCurrent && (
            <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
              {isPlaying ? (
                <MusicIcon className="h-5 w-5 text-white animate-pulse" />
              ) : (
                <Play className="h-5 w-5 text-white ml-0.5" />
              )}
            </div>
          )}
        </div>
        
        <div className="min-w-0 flex-1">
          <p className={cn(
            "font-semibold", 
            (isCurrent || isLiked) && "text-primary",
            "whitespace-normal break-words"
          )}>{song.title}</p>
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground truncate">{song.artist}</p>
            {hasBeenPlayed && (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger>
                    <History className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  </TooltipTrigger>
                  <TooltipContent>
                    <p>Played</p>
                  </TooltipContent>
                </Tooltip>
              </TooltipProvider>
            )}
          </div>
        </div>
      </div>
      <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 shrink-0 opacity-0 group-hover:opacity-100"
          onClick={onRemove}
        >
          <X className="h-4 w-4" />
          <span className="sr-only">Remove from queue</span>
        </Button>
    </div>
  );
};


export function MusicPlayer() {
  const { 
    currentTrack, 
    playlist,
    history,
    playPlaylist, 
    handleTrackError, 
    clearQueue,
    isPlaying: isGlobalPlaying,
    setIsPlaying: setGlobalIsPlaying,
    playNext: globalPlayNext,
    playPrev: globalPlayPrev,
    shufflePlaylist,
    removeSongFromQueue,
    reorderPlaylist,
    playerMode,
    setPlayerMode,
    initialLoadIsVideoShare,
    setInitialLoadIsVideoShare,
  } = usePlayer();

  const { playlists, addSongToPlaylist, toggleLikeSong, isSongLiked, removeSongFromDatabase } = usePlaylists();
  const { toast } = useToast();
  const pathname = usePathname();
  const prevPathname = usePrevious(pathname);
  
  const playerRef = useRef<YouTubePlayer | null>(null);
  const playerAndVideoContainerRef = useRef<HTMLDivElement | null>(null);
  
  const isSeekingRef = useRef(false);
  const isChangingTrackRef = useRef(false);
  const progressIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const isGlobalPlayingRef = useRef(isGlobalPlaying);
  const pendingNativeActionRef = useRef<string | null>(null);

  // Player-specific UI state
  const [container, setContainer] = useState<HTMLElement | null>(null);
  const [volume, setVolume] = useState(100);
  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [isPip, setIsPip] = useState(false);
  const [zoomLevel, setZoomLevel] = useState(1.25); // 1, 1.25, 1.5
  const [isCreatePlaylistDialogOpen, setIsCreatePlaylistDialogOpen] = useState(false);
  
  // Local state for rapidly updating progress values, managed internally
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);

  const isAndroidAppRuntime = useMemo(() => {
    if (typeof window === 'undefined') return false;

    const userAgent = navigator.userAgent || '';
    const isAndroidUa = /Android/i.test(userAgent);
    const isFileProtocol = window.location.protocol === 'file:';
    const isWebViewAssetHost = window.location.hostname === 'appassets.androidplatform.net';
    const hasNativeBridge = typeof window.HarmonyNative !== 'undefined';

    return isAndroidUa && (isFileProtocol || isWebViewAssetHost || hasNativeBridge);
  }, []);

  const applyNativeCommand = useCallback((action: string) => {
    if (!action) return;

    const player = playerRef.current;
    const canControlPlayer = !!player && typeof player.getPlayerState === 'function';

    if (!canControlPlayer) {
      pendingNativeActionRef.current = action;
    }

    switch (action) {
      case 'com.sansoft.harmonystram.PLAY':
        if (canControlPlayer && typeof player.playVideo === 'function') {
          player.playVideo();
        }
        setGlobalIsPlaying(true);
        break;
      case 'com.sansoft.harmonystram.PAUSE':
        if (canControlPlayer && typeof player.pauseVideo === 'function') {
          player.pauseVideo();
        }
        setGlobalIsPlaying(false);
        break;
      case 'com.sansoft.harmonystram.PLAY_PAUSE':
        if (canControlPlayer && typeof player.getPlayerState === 'function') {
          const playerState = player.getPlayerState();
          const shouldPlay = playerState !== 1;
          if (shouldPlay && typeof player.playVideo === 'function') {
            player.playVideo();
          } else if (!shouldPlay && typeof player.pauseVideo === 'function') {
            player.pauseVideo();
          }
          setGlobalIsPlaying(shouldPlay);
        } else {
          setGlobalIsPlaying(!isGlobalPlayingRef.current);
        }
        break;
      case 'com.sansoft.harmonystram.NEXT':
        setGlobalIsPlaying(true);
        globalPlayNext();
        break;
      case 'com.sansoft.harmonystram.PREVIOUS':
        setGlobalIsPlaying(true);
        globalPlayPrev();
        break;
      default:
        break;
    }
  }, [setGlobalIsPlaying, globalPlayNext, globalPlayPrev]);

  useEffect(() => {
    isGlobalPlayingRef.current = isGlobalPlaying;
  }, [isGlobalPlaying]);

  useEffect(() => {
    if (isQueueOpen && currentTrack) {
      // Use a short timeout to allow the sheet and its content to render before scrolling
      setTimeout(() => {
        const element = document.getElementById(`queue-song-${currentTrack.id}`);
        element?.scrollIntoView({
          behavior: 'smooth',
          block: 'center',
        });
      }, 100);
    }
  }, [isQueueOpen, currentTrack]);

  useEffect(() => {
    // This makes the container available for portals after the initial render.
    if (playerAndVideoContainerRef.current) {
      setContainer(playerAndVideoContainerRef.current);
    }
  }, []);

  useEffect(() => {
    try {
      const stored = localStorage.getItem('harmony-video-zoom');
      if (stored) {
        const parsedZoom = parseFloat(stored);
        if ([1, 1.25, 1.5].includes(parsedZoom)) {
            setZoomLevel(parsedZoom);
        }
      }
    } catch (error) {
      console.error('Could not load video zoom preference', error);
    }
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem('harmony-video-zoom', String(zoomLevel));
    } catch (error) {
      console.error('Could not save video zoom preference', error);
    }
  }, [zoomLevel]);

  // --- Core Player Logic ---
  
  const stopProgressInterval = useCallback(() => {
    if (progressIntervalRef.current) {
      clearInterval(progressIntervalRef.current);
      progressIntervalRef.current = null;
    }
  }, []);

  const startProgressInterval = useCallback(() => {
    stopProgressInterval();
    progressIntervalRef.current = setInterval(() => {
      if (playerRef.current && typeof playerRef.current.getDuration === 'function' && !isSeekingRef.current) {
        const player = playerRef.current;
        const cTime = player.getCurrentTime();
        const dur = player.getDuration();
        if (dur > 0) {
          setCurrentTime(cTime);
          setDuration(dur);
          setProgress((cTime / dur) * 100);
        }
      }
    }, 1000);
  }, [stopProgressInterval]);

  const onReady: YouTubeProps['onReady'] = useCallback((event: YouTubeEvent) => {
    playerRef.current = event.target;
    playerRef.current.setVolume(volume);
    if (pendingNativeActionRef.current) {
      const action = pendingNativeActionRef.current;
      pendingNativeActionRef.current = null;
      setTimeout(() => {
        if (action) {
          window.__harmonyNativeApplyCommand?.(action);
        }
      }, 50);
    }
  }, [volume]);

  const onStateChange: YouTubeProps['onStateChange'] = useCallback((event: YouTubeEvent<number>) => {
    const playerState = event.data;
    const player = event.target;

    switch (playerState) {
      case -1: // Unstarted
        stopProgressInterval();
        break;
      case 0: // Ended
        stopProgressInterval();
        setGlobalIsPlaying(false);
        globalPlayNext();
        break;
      case 1: // Playing
        isChangingTrackRef.current = false;
        setGlobalIsPlaying(true);
        startProgressInterval();
        break;
      case 2: // Paused
        if (!isChangingTrackRef.current) {
          setGlobalIsPlaying(false);
        }
        stopProgressInterval();
        break;
      case 3: // Buffering
        stopProgressInterval();
        break;
      case 5: // Video Cued
        if (isGlobalPlaying) {
          player.playVideo();
        }
        break;
    }
  }, [setGlobalIsPlaying, globalPlayNext, startProgressInterval, stopProgressInterval, isGlobalPlaying]);
  
  const onError: YouTubeProps['onError'] = useCallback((event: YouTubeEvent<number>) => {
    const errorCode = event.data;
    stopProgressInterval();
    if (currentTrack) {
        if (errorCode === 100 || errorCode === 101 || errorCode === 150) {
            toast({
                variant: 'destructive',
                title: 'Video Unavailable',
                description: `"${currentTrack.title}" will be removed.`,
            });
            removeSongFromDatabase(currentTrack.id, currentTrack.title);
        } else {
             toast({
                variant: 'destructive',
                title: 'Playback Error',
                description: `An error occurred with "${currentTrack.title}". Skipping.`,
            });
        }
        handleTrackError(currentTrack.id);
    }
  }, [currentTrack, handleTrackError, removeSongFromDatabase, toast, stopProgressInterval]);
  
  // Effect to load a new track when the global currentTrack changes
  useEffect(() => {
    if (currentTrack && playerRef.current && typeof playerRef.current.loadVideoById === 'function') {
        isChangingTrackRef.current = true;
        stopProgressInterval();
        setProgress(0);
        setCurrentTime(0);
        setDuration(0);
        playerRef.current.loadVideoById(currentTrack.videoId);
    }
  }, [currentTrack, stopProgressInterval]);

  // Effect to sync player's play/pause state with global state
  useEffect(() => {
    const player = playerRef.current;
    if (!player || isChangingTrackRef.current || !player.getPlayerState) return;

    const playerState = player.getPlayerState();

    if (isGlobalPlaying && playerState !== 1) {
      player.playVideo();
    } else if (!isGlobalPlaying && playerState === 1) {
      player.pauseVideo();
    }
  }, [isGlobalPlaying]);

  useEffect(() => {
    if (playerRef.current && typeof playerRef.current.setVolume === 'function') {
      playerRef.current.setVolume(volume);
    }
  }, [volume]);
  
  // --- UI Handlers ---

  const handleTogglePlayPause = useCallback(() => {
    if (!currentTrack || !playerRef.current) return;
    const player = playerRef.current;

    // If it's the first interaction for a shared video, GUARANTEE playback and fullscreen.
    if (initialLoadIsVideoShare && playerMode === 'video') {
        const fsContainer = playerAndVideoContainerRef.current;
        if (fsContainer && !document.fullscreenElement) {
            fsContainer.requestFullscreen().catch(err => {
                console.warn("Fullscreen request failed on play:", err);
            });
        }
        player.playVideo(); // Directly command PLAY
        setInitialLoadIsVideoShare(false); // Consume the flag
        // The onStateChange event will handle setting the global state to true
    } else {
        // For all subsequent clicks, toggle based on current player state.
        const state = player.getPlayerState();
        if (state === 1) { // is playing
            player.pauseVideo();
        } else {
            player.playVideo();
        }
    }
  }, [currentTrack, initialLoadIsVideoShare, playerMode, setInitialLoadIsVideoShare]);

  const handleSeekChange = useCallback((value: number[]) => {
    isSeekingRef.current = true;
    stopProgressInterval();
    const player = playerRef.current;
    if (player) {
      const newDuration = player.getDuration();
      setDuration(newDuration);
      setCurrentTime((value[0] / 100) * newDuration);
      setProgress(value[0]);
    }
  }, [stopProgressInterval]);

  const handleSeekCommit = useCallback((value: number[]) => {
    const player = playerRef.current;
    if (player) {
      const newDuration = player.getDuration();
      if (newDuration > 0) {
        const newTime = (value[0] / 100) * newDuration;
        player.seekTo(newTime, true);
      }
    }
    // Let the onStateChange handler restart the interval
    setTimeout(() => {
      isSeekingRef.current = false;
      if (isGlobalPlaying) {
        startProgressInterval();
      }
    }, 150);
  }, [isGlobalPlaying, startProgressInterval]);

  const handleVolumeChange = useCallback((newVolume: number) => {
    setVolume(newVolume);
  }, []);

  const handleClearQueue = useCallback(() => {
    clearQueue();
    setIsQueueOpen(false);
  }, [clearQueue]);

  const handleQueueSongClick = useCallback((songId: string) => {
      playPlaylist(playlist, songId);
  }, [playlist, playPlaylist]);
  
  const handleTogglePlayerMode = useCallback(async () => {
    if (isPip) {
      try { await document.exitPictureInPicture(); } catch (error) { console.warn('Could not exit PiP:', error); }
    }
    setInitialLoadIsVideoShare(false);
    
    const newMode = playerMode === 'audio' ? 'video' : 'audio';
    const fsContainer = playerAndVideoContainerRef.current;

    try {
      if (newMode === 'video' && fsContainer && !document.fullscreenElement) {
        await fsContainer.requestFullscreen();
      } else if (newMode === 'audio' && document.fullscreenElement) {
        await document.exitFullscreen();
      }
    } catch (err) {
      console.warn(`Fullscreen transition failed: ${(err as Error).message}`);
    }

    setPlayerMode(newMode);
  }, [isPip, playerMode, setPlayerMode, setInitialLoadIsVideoShare]);
  
  const handleToggleLike = useCallback(() => {
    if (currentTrack) {
      toggleLikeSong(currentTrack);
    }
  }, [currentTrack, toggleLikeSong]);

  const handleAddToPlaylist = useCallback((playlistId: string) => {
    if (currentTrack) {
      addSongToPlaylist(playlistId, currentTrack);
    }
  }, [currentTrack, addSongToPlaylist]);
  
  const handleToggleZoom = () => {
    setZoomLevel(prev => {
        if (prev === 1) return 1.25;
        if (prev === 1.25) return 1.5;
        return 1; // from 1.5 back to 1
    });
  };

  const handleShare = useCallback(async () => {
    if (!currentTrack) return;
    const shareUrl = `${window.location.origin}/harmonystream/?share_id=${currentTrack.videoId}&mode=${playerMode}`;
    const shareData = {
      title: currentTrack.title,
      text: `Listen to "${currentTrack.title}" by ${currentTrack.artist} on HarmonyStream`,
      url: shareUrl,
    };
    
    try {
        if (navigator.share) {
            await navigator.share(shareData);
            toast({ title: 'Song shared!' });
        } else {
            await navigator.clipboard.writeText(shareUrl);
            toast({ title: 'Link Copied!', description: 'Shareable link copied to your clipboard.' });
        }
    } catch (error) {
        console.error('Error sharing:', error);
        // Fallback for browsers that might fail clipboard write in some contexts
        try {
          await navigator.clipboard.writeText(shareUrl);
          toast({ title: 'Link Copied!', description: 'Shareable link copied to your clipboard.' });
        } catch (copyError) {
          toast({ variant: 'destructive', title: 'Could not share', description: 'Your browser may not support this feature.' });
        }
    }
  }, [currentTrack, playerMode, toast]);

  // --- Drag and Drop ---
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8, // Drag only after 8px movement
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const {active, over} = event;

    if (over && active.id !== over.id) {
      const oldIndex = playlist.findIndex(s => s.id === active.id);
      const newIndex = playlist.findIndex(s => s.id === over.id);
      if (oldIndex !== -1 && newIndex !== -1) {
        reorderPlaylist(oldIndex, newIndex);
      }
    }
  };


  // --- Picture-in-Picture & Fullscreen Handlers ---

  useEffect(() => {
    const handlePictureInPictureChange = () => setIsPip(!!document.pictureInPictureElement);
    document.addEventListener('enterpictureinpicture', handlePictureInPictureChange);
    document.addEventListener('leavepictureinpicture', handlePictureInPictureChange);
    return () => {
        document.removeEventListener('enterpictureinpicture', handlePictureInPictureChange);
        document.removeEventListener('leavepictureinpicture', handlePictureInPictureChange);
    };
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => {
        if (!document.fullscreenElement) {
            if (playerMode === 'video') setPlayerMode('audio');
            setInitialLoadIsVideoShare(false);
        }
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, [playerMode, setPlayerMode, setInitialLoadIsVideoShare]);

  useEffect(() => {
    if (prevPathname && prevPathname !== pathname && playerMode === 'video' && !isPip) {
       const iframe = playerRef.current?.getIframe();
       if(iframe && document.pictureInPictureEnabled && !document.pictureInPictureElement) {
           iframe.requestPictureInPicture().catch((e: any) => console.warn("PiP request failed automatically.", e));
       }
    }
  }, [pathname, prevPathname, playerMode, isPip]);
  
  // --- Video Controls Visibility ---

  const [showControls, setShowControls] = useState(true);
  const controlsTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const resetControlsTimeout = useCallback(() => {
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    setShowControls(true);
    
    const canAutoHide = playerMode === 'video' && isGlobalPlaying && !isPip;
    if (canAutoHide) {
      controlsTimeoutRef.current = setTimeout(() => setShowControls(false), 3000);
    }
  }, [playerMode, isGlobalPlaying, isPip]);

  useEffect(() => {
    resetControlsTimeout();
    return () => {
      if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    };
  }, [resetControlsTimeout]);
  

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!isAndroidAppRuntime || !document.hidden) return;

      // When app goes to background while in video mode, force audio mode so playback
      // has the best chance to continue (especially on Android TV/WebView builds).
      if (playerMode === 'video' && isGlobalPlaying) {
        setPlayerMode('audio');
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [playerMode, isGlobalPlaying, setPlayerMode, isAndroidAppRuntime]);

  // --- Media Session API ---
  useEffect(() => {
    if (!('mediaSession' in navigator)) return;

    if (!currentTrack) {
      navigator.mediaSession.metadata = null;
      navigator.mediaSession.playbackState = 'none';
      return;
    }

    navigator.mediaSession.metadata = new window.MediaMetadata({
      title: currentTrack.title,
      artist: currentTrack.artist,
      album: 'HarmonyStream',
      artwork: [{ src: currentTrack.thumbnailUrl, sizes: '512x512', type: 'image/jpeg' }],
    });
    
  }, [currentTrack]);
  
  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.playbackState = isGlobalPlaying ? 'playing' : 'paused';
    
    navigator.mediaSession.setActionHandler('play', () => setGlobalIsPlaying(true));
    navigator.mediaSession.setActionHandler('pause', () => setGlobalIsPlaying(false));
    navigator.mediaSession.setActionHandler('previoustrack', globalPlayPrev);
    navigator.mediaSession.setActionHandler('nexttrack', globalPlayNext);
    navigator.mediaSession.setActionHandler('seekbackward', null);
    navigator.mediaSession.setActionHandler('seekforward', null);
    if (isAndroidAppRuntime) {
      navigator.mediaSession.setActionHandler('seekto', (details) => {
        if (typeof details.seekTime === 'number') {
          handleSeekCommit([details.seekTime]);
        }
      });
    } else {
      navigator.mediaSession.setActionHandler('seekto', null);
    }

  }, [isGlobalPlaying, setGlobalIsPlaying, globalPlayPrev, globalPlayNext, handleSeekCommit, isAndroidAppRuntime]);

  useEffect(() => {
    if ('mediaSession' in navigator && navigator.mediaSession.metadata) {
      try {
        navigator.mediaSession.setPositionState({
          duration: duration || 0,
          playbackRate: 1,
          position: currentTime || 0,
        });
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'InvalidStateError')) {
          console.warn("Failed to set MediaSession position state:", error);
        }
      }
    }
  }, [currentTime, duration]);

  useEffect(() => {
    if (!isAndroidAppRuntime || typeof window === 'undefined') return;

    window.__harmonyNativeManagedByApp = true;
    window.__harmonyNativeApplyCommand = applyNativeCommand;
    const commandListener = (event: Event) => {
      const detail = (event as CustomEvent<{ action?: string }>).detail;
      applyNativeCommand(detail?.action ?? '');
    };

    const nativeStateListener = (event: Event) => {
      const detail = (event as CustomEvent<{ playing?: boolean }>).detail;
      if (typeof detail?.playing !== 'boolean') return;

      setGlobalIsPlaying(detail.playing);
      if (detail.playing) {
        const player = playerRef.current;
        if (player && typeof player.getPlayerState === 'function' && typeof player.playVideo === 'function') {
          const state = player.getPlayerState();
          if (state !== 1) {
            player.playVideo();
          }
        }
      }
    };

    const hostResumedListener = () => {
      window.HarmonyNative?.getState?.();
      if (isGlobalPlayingRef.current) {
        const player = playerRef.current;
        if (player && typeof player.playVideo === 'function') {
          player.playVideo();
        }
      }
    };

    const pipStateListener = (event: Event) => {
      const detail = (event as CustomEvent<{ isInPictureInPictureMode?: boolean }>).detail;
      if (detail?.isInPictureInPictureMode && currentTrack) {
        setGlobalIsPlaying(true);
      }
    };

    window.addEventListener('nativePlaybackCommand', commandListener as EventListener);
    window.addEventListener('nativePlaybackState', nativeStateListener as EventListener);
    window.addEventListener('nativeHostResumed', hostResumedListener as EventListener);
    window.addEventListener('nativePictureInPictureChanged', pipStateListener as EventListener);
    window.HarmonyNative?.getState?.();

    return () => {
      window.removeEventListener('nativePlaybackCommand', commandListener as EventListener);
      window.removeEventListener('nativePlaybackState', nativeStateListener as EventListener);
      window.removeEventListener('nativeHostResumed', hostResumedListener as EventListener);
      window.removeEventListener('nativePictureInPictureChanged', pipStateListener as EventListener);
      window.__harmonyNativeApplyCommand = undefined;
      window.__harmonyNativeManagedByApp = false;
    };
  }, [isAndroidAppRuntime, applyNativeCommand, currentTrack, setGlobalIsPlaying]);

  useEffect(() => {
    if (!isAndroidAppRuntime || !currentTrack) return;
    const positionMs = Math.max(0, Math.floor(currentTime * 1000));
    const durationMs = Math.max(0, Math.floor(duration * 1000));

    window.HarmonyNative?.updateState?.(
      currentTrack.title,
      currentTrack.artist,
      isGlobalPlaying,
      positionMs,
      durationMs,
      ''
    );
  }, [isAndroidAppRuntime, currentTrack, isGlobalPlaying, currentTime, duration]);

  useEffect(() => {
    if (!isAndroidAppRuntime) return;
    const queueForNative = playlist.map((song) => ({
      id: song.id,
      title: song.title,
      artist: song.artist,
      videoId: song.videoId,
      thumbnailUrl: song.thumbnailUrl,
    }));
    window.HarmonyNative?.setQueue?.(JSON.stringify(queueForNative));
  }, [isAndroidAppRuntime, playlist]);
  
  if (!currentTrack) {
    return null;
  }
  
  const isCurrentlyLiked = currentTrack ? isSongLiked(currentTrack.id) : false;
  
  const PipControls = () => (
    <TooltipProvider>
      <div className="flex items-center justify-between p-1 bg-black/50">
        <p className="text-xs text-white truncate flex-1 ml-2">{currentTrack.title}</p>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/20 h-7 w-7"
              onClick={async () => await document.exitPictureInPicture()}
            >
              <Maximize2 className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent side="top"><p>Exit Picture-in-Picture</p></TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/20 h-7 w-7"
              onClick={() => handleTrackError(currentTrack.id) }
            >
              <X className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent side="top"><p>Close player</p></TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );

  const getZoomTooltipText = () => {
    if (zoomLevel === 1) return 'Zoom';
    if (zoomLevel === 1.25) return 'Zoom Further';
    return 'Fit to Screen';
  };

  const playerProps = {
    currentTrack,
    progress,
    duration,
    currentTime,
    isPlaying: isGlobalPlaying,
    isLiked: isCurrentlyLiked,
    playerMode,
    volume,
    playlists,
    onToggleLike: handleToggleLike,
    onTogglePlayerMode: handleTogglePlayerMode,
    onPlayPrev: globalPlayPrev,
    onPlayNext: globalPlayNext,
    onTogglePlayPause: handleTogglePlayPause,
    onVolumeChange: handleVolumeChange,
    onSeekChange: handleSeekChange,
    onSeekCommit: handleSeekCommit,
    onAddToPlaylist: handleAddToPlaylist,
    onShare: handleShare,
    onOpenCreatePlaylistDialog: setIsCreatePlaylistDialogOpen,
    isPip,
    showControls,
    onResetControlsTimeout: resetControlsTimeout,
    container: container,
  };

  return (
    <>
      <CreatePlaylistDialog
        open={isCreatePlaylistDialogOpen}
        onOpenChange={setIsCreatePlaylistDialogOpen}
        container={container}
      />
      <Sheet open={isQueueOpen} onOpenChange={setIsQueueOpen}>
        <div 
          ref={playerAndVideoContainerRef}
          className={cn(
            playerMode === 'video' ? "fixed inset-0 bg-black z-50" : "relative"
          )}
        >
            <div className={cn(
                "absolute inset-0 w-full h-full transition-all duration-300 ease-in-out pointer-events-none",
                playerMode === 'video' ? 'opacity-100' : 'opacity-0'
              )}
              style={{ transform: `scale(${zoomLevel})` }}
            >
              <YouTube
                  videoId={currentTrack.videoId}
                  opts={{ playerVars: { playsinline: 1, controls: 0, modestbranding: 1, rel: 0, iv_load_policy: 3, autoplay: 1, origin: typeof window !== 'undefined' ? window.location.origin : undefined } }}
                  onReady={onReady}
                  onStateChange={onStateChange}
                  onError={onError}
                  className="absolute top-0 left-0 w-full h-full"
                  iframeClassName="w-full h-full"
                />
            </div>
           
           <PortraitPlayer {...playerProps} />
           <LandscapePlayer {...playerProps} />

           {playerMode === 'video' && !isPip && (
              <>
                <div className="absolute top-4 right-4 z-[70] hidden md:block">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-white bg-black/30 hover:bg-black/50"
                        onClick={handleToggleZoom}
                      >
                        {zoomLevel > 1 ? <Minimize2 className="h-5 w-5" /> : <Maximize2 className="h-5 w-5" />}
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="bottom">
                      <p>{getZoomTooltipText()}</p>
                    </TooltipContent>
                  </Tooltip>
                </div>
                <div
                    className={cn("absolute inset-0 z-[55]", showControls ? 'pointer-events-none' : 'pointer-events-auto')}
                    onClick={showControls ? undefined : resetControlsTimeout}
                    aria-hidden="true"
                />
              </>
           )}
       </div>
      <SheetContent 
        container={playerAndVideoContainerRef.current}
        className={cn("p-4 z-[9999]", "md:w-[400px] md:sm:w-[540px]")}
        side="right"
      >
        <SheetHeader className="flex flex-row justify-between items-center pr-6">
          <div className="flex items-baseline gap-2">
            <SheetTitle>Up Next</SheetTitle>
            {playlist.length > 0 && (
              <span className="text-sm text-muted-foreground tabular-nums">
                {playlist.length} {playlist.length === 1 ? 'song' : 'songs'}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="icon" onClick={shufflePlaylist} disabled={playlist.length <= 1}>
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-shuffle h-5 w-5"><path d="M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22"/><path d="m18 2 4 4-4 4"/><path d="M2 6h1.9c1.5 0 2.9.9 3.6 2.2l.7 1.2c.5.9 1.4 1.5 2.5 1.5H22"/><path d="m18 22 4-4-4-4"/></svg>
              <span className="sr-only">Shuffle</span>
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="ghost" size="icon" disabled={playlist.length === 0}>
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-trash h-5 w-5"><path d="M5 4h14"/><path d="M5 4a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2"/><path d="M19 4v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>
                  <span className="sr-only">Clear Queue</span>
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent container={container}>
                <AlertDialogHeader>
                  <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This will clear your current playing queue. The current song will continue playing. This action cannot be undone.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction onClick={handleClearQueue}>Clear</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </SheetHeader>
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={playlist.map(s => s.id)}
            strategy={verticalListSortingStrategy}
          >
            <ScrollArea className="h-[calc(100vh-5rem)] mt-4 pr-6">
              <div className="flex flex-col gap-2">
                {playlist.map((song) => {
                  const isThisSongLiked = isSongLiked(song.id);
                  const hasThisSongBeenPlayed = history.some(h => h.id === song.id);

                  return (
                    <SortableSongItem
                      key={song.id}
                      song={song}
                      isCurrent={song.id === currentTrack?.id}
                      isPlaying={isGlobalPlaying}
                      isLiked={isThisSongLiked}
                      hasBeenPlayed={hasThisSongBeenPlayed}
                      onPlay={() => handleQueueSongClick(song.id)}
                      onRemove={() => removeSongFromQueue(song.id)}
                    />
                  )
                })}
                {playlist.length === 0 && (
                  <p className="text-muted-foreground text-center py-8">The queue is empty.</p>
                )}
              </div>
            </ScrollArea>
          </SortableContext>
        </DndContext>
      </SheetContent>
    </Sheet>
    {isPip && (
      <div className="fixed bottom-5 right-5 w-64 h-auto bg-black rounded-lg shadow-2xl z-[60] overflow-hidden">
          <div className="relative aspect-video">
                <YouTube
                  videoId={currentTrack.videoId}
                  opts={{ playerVars: { playsinline: 1, controls: 0, modestbranding: 1, rel: 0, iv_load_policy: 3, start: currentTime, autoplay: 1, origin: typeof window !== 'undefined' ? window.location.origin : undefined } }}
                  onReady={(e: YouTubeEvent) => {
                      e.target.setVolume(volume);
                      if (isGlobalPlaying) e.target.playVideo();
                  }}
                  className="absolute top-0 left-0 w-full h-full"
                  iframeClassName="w-full h-full object-cover"
              />
          </div>
          <PipControls />
      </div>
    )}
    </>
  );
}

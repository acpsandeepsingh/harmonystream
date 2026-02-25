'use client';

import { usePlayer } from '@/contexts/player-context';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Slider } from '@/components/ui/slider';
import {
  Play, Pause, SkipBack, SkipForward, Volume2,
  Video, Music as MusicIcon, Plus, ListMusic, PlusCircle,
  Heart, X, Maximize2, Minimize2, History, GripVertical, Share2,
} from 'lucide-react';
import React, {
  useEffect, useState, useRef, useCallback, useMemo,
} from 'react';
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
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import Image from 'next/image';
import type { Song } from '@/lib/types';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel,
  AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Tooltip, TooltipContent, TooltipTrigger, TooltipProvider,
} from '@/components/ui/tooltip';
import { usePathname } from 'next/navigation';
import { usePrevious } from '@/hooks/use-previous';
import {
  DndContext, closestCenter, KeyboardSensor, PointerSensor,
  useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext, sortableKeyboardCoordinates,
  verticalListSortingStrategy, useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// ---------------------------------------------------------------------------
// Global type augmentation for native bridge
// ---------------------------------------------------------------------------
declare global {
  interface Window {
    HarmonyNative?: {
      play?: () => void;
      pause?: () => void;
      next?: () => void;
      previous?: () => void;
      setIndex?: (index: number) => void;
      seek?: (positionMs: number) => void;
      setQueue?: (queueJson: string) => void;
      setVideoMode?: (enabled: boolean) => void;
      updateState?: (
        title: string, artist: string, playing: boolean,
        positionMs: number, durationMs: number, thumbnailUrl: string,
      ) => void;
      getState?: () => void;
    };
    AndroidNative?: {
      play?: (id?: string, title?: string, artist?: string,
               durationMs?: number, thumbnailUrl?: string) => void;
      pause?: () => void;
      resume?: () => void;
      seekTo?: (positionMs: number) => void;
      setVideoMode?: (enabled: boolean) => void;
    };
    updateProgress?: (positionMs: number, durationMs: number) => void;
    __harmonyNativeApplyCommand?: (action: string) => void;
    __harmonyNativeManagedByApp?: boolean;
  }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------
const formatDuration = (seconds: number) => {
  if (isNaN(seconds) || seconds < 0) return '0:00';
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = Math.floor(seconds % 60);
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
};

// ---------------------------------------------------------------------------
// Portrait player sub-component (mobile bottom bar)
// ---------------------------------------------------------------------------
const PortraitPlayer = React.memo(function PortraitPlayer({
  currentTrack, progress, duration, currentTime, isPlaying, isLiked,
  playerMode, volume, playlists, onToggleLike, onTogglePlayerMode,
  onPlayPrev, onPlayNext, onTogglePlayPause, onVolumeChange,
  onSeekChange, onSeekCommit, onAddToPlaylist, onShare,
  onOpenCreatePlaylistDialog, container,
}: any) {
  return (
    <div className={cn(
      "md:hidden flex flex-col fixed bottom-0 left-0 right-0 z-50 transition-colors",
      "bg-background border-t text-foreground",
    )}>
      {/* Progress bar */}
      <div className="w-full flex items-center gap-2 px-2 pt-2">
        <span className={cn("text-xs w-10 text-right", "text-muted-foreground")}>
          {formatDuration(currentTime)}
        </span>
        <Slider
          value={[progress]} max={100} step={1}
          onValueChange={onSeekChange} onValueCommit={onSeekCommit}
          className="w-full [&>span:first-of-type]:h-4 [&_.h-2]:h-3"
        />
        <span className={cn("text-xs w-10", "text-muted-foreground")}>
          {formatDuration(duration)}
        </span>
      </div>

      {/* Track info row */}
      <div className="w-full flex items-center p-2">
        <div className="min-w-0 flex-1 flex items-center gap-3">
          <Avatar className="h-12 w-12 rounded-md">
            <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
            <AvatarFallback>{currentTrack.artist.charAt(0)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0">
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
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={e => e.stopPropagation()}>
                <Plus className="h-5 w-5" /><span className="sr-only">Add to playlist</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent container={container} align="end" forceMount>
              <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {playlists.map((pl: any) => (
                <DropdownMenuItem key={pl.id} onClick={() => onAddToPlaylist(pl.id)}>
                  <ListMusic className="mr-2 h-4 w-4" /><span>{pl.name}</span>
                </DropdownMenuItem>
              ))}
              {playlists.length > 0 && <DropdownMenuSeparator />}
              <DropdownMenuItem onSelect={() => onOpenCreatePlaylistDialog(true)}>
                <PlusCircle className="mr-2 h-4 w-4" />Create new playlist
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="ghost" size="icon" onClick={onShare} className="h-8 w-8">
            <Share2 className="h-5 w-5" /><span className="sr-only">Share song</span>
          </Button>
        </div>
      </div>

      {/* Controls row */}
      <div className="w-full flex items-center justify-between px-2 pb-2">
        <div className="w-20">
          <Button variant="ghost" size="icon" onClick={onTogglePlayerMode} className="h-8 w-8">
            {playerMode === 'audio' ? <Video className="h-5 w-5" /> : <MusicIcon className="h-5 w-5" />}
          </Button>
        </div>
        <div className="flex items-center">
          <Button variant="ghost" size="icon" onClick={onPlayPrev}><SkipBack className="h-6 w-6" /></Button>
          <Button variant="ghost" size="icon" className="h-14 w-14" onClick={onTogglePlayPause}>
            {isPlaying
              ? <Pause className="h-10 w-10" />
              : <Play className="h-10 w-10 ml-1" />}
          </Button>
          <Button variant="ghost" size="icon" onClick={onPlayNext}><SkipForward className="h-6 w-6" /></Button>
        </div>
        <div className="flex items-center gap-1 w-20">
          <Volume2 className={cn("h-5 w-5", "text-muted-foreground")} />
          <Slider
            defaultValue={[volume]} max={100} step={1}
            className="w-full" onValueChange={v => onVolumeChange(v[0])}
          />
        </div>
      </div>
    </div>
  );
});
PortraitPlayer.displayName = 'PortraitPlayer';

// ---------------------------------------------------------------------------
// Landscape player sub-component (desktop / video mode)
// ---------------------------------------------------------------------------
const LandscapePlayer = React.memo(function LandscapePlayer({
  currentTrack, progress, duration, currentTime, isPlaying, isLiked,
  playerMode, volume, playlists, onToggleLike, onTogglePlayerMode,
  onPlayPrev, onPlayNext, onTogglePlayPause, onVolumeChange,
  onSeekChange, onSeekCommit, onAddToPlaylist, onShare,
  onOpenCreatePlaylistDialog, isPip, showControls, onResetControlsTimeout,
  container,
}: any) {
  const PlayerContainer = 'div';
  const containerProps = {
    className: cn(
      'hidden md:block',
      playerMode === 'audio' && 'w-full h-auto fixed bottom-0 left-0 right-0 z-[60]',
      playerMode === 'audio' && (isPip ? 'opacity-0 pointer-events-none' : 'opacity-100'),
    ),
  };

  return (
    <PlayerContainer {...containerProps}>
      <div
        className={cn(
          "w-full flex flex-col p-2 gap-1 transition-all duration-300",
          playerMode === 'video'
            ? "bg-gradient-to-t from-black/80 to-transparent text-white fixed bottom-0 left-0 right-0 z-[60]"
            : "border-t bg-card/95 backdrop-blur-xl text-foreground",
        )}
        onMouseMove={onResetControlsTimeout}
      >
        {/* Progress bar */}
        <div className={cn(
          "relative w-full flex items-center gap-2 px-2 transition-opacity duration-300 z-10",
          playerMode === 'video' && !showControls && 'opacity-0 pointer-events-none',
        )}>
          <span className="text-xs w-10 text-right text-muted-foreground">{formatDuration(currentTime)}</span>
          <Slider
            value={[progress]} max={100} step={1}
            onValueChange={onSeekChange} onValueCommit={onSeekCommit}
            className="w-full [&>span:first-of-type]:h-4 [&_.h-2]:h-1"
          />
          <span className="text-xs w-10 text-muted-foreground">{formatDuration(duration)}</span>
        </div>

        {/* Controls row */}
        <div className={cn(
          "relative w-full flex items-center justify-between gap-2 transition-opacity duration-300 h-[64px] z-10",
          playerMode === 'video' && !showControls && 'opacity-0 pointer-events-none',
        )}>
          {/* Left: track info */}
          <div className="flex items-center gap-3 min-w-0 w-1/3">
            <Avatar className="h-12 w-12 rounded-md">
              <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
              <AvatarFallback>{currentTrack.artist.charAt(0)}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <p className="font-bold truncate">{currentTrack.title}</p>
              <p className="text-sm truncate text-muted-foreground">{currentTrack.artist}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={onToggleLike}>
              <Heart className={cn("h-5 w-5", isLiked && "fill-red-500 text-red-500")} />
            </Button>
          </div>

          {/* Center: playback buttons */}
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={onPlayPrev}><SkipBack className="h-5 w-5" /></Button>
            <Button variant="default" size="icon" className="h-10 w-10" onClick={onTogglePlayPause}>
              {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}
            </Button>
            <Button variant="ghost" size="icon" onClick={onPlayNext}><SkipForward className="h-5 w-5" /></Button>
          </div>

          {/* Right: extras */}
          <div className="flex items-center gap-2 w-1/3 justify-end">
            <Button variant="ghost" size="icon" onClick={onShare}>
              <Share2 className="h-5 w-5" /><span className="sr-only">Share</span>
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" onClick={e => e.stopPropagation()}>
                  <Plus className="h-5 w-5" /><span className="sr-only">Add to playlist</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent container={container} align="end">
                <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {playlists.map((pl: any) => (
                  <DropdownMenuItem key={pl.id} onClick={() => onAddToPlaylist(pl.id)}>
                    <ListMusic className="mr-2 h-4 w-4" /><span>{pl.name}</span>
                  </DropdownMenuItem>
                ))}
                {playlists.length > 0 && <DropdownMenuSeparator />}
                <DropdownMenuItem onSelect={() => onOpenCreatePlaylistDialog(true)}>
                  <PlusCircle className="mr-2 h-4 w-4" />Create new playlist
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon">
                <ListMusic className="h-5 w-5" /><span className="sr-only">Open Queue</span>
              </Button>
            </SheetTrigger>
            <Button variant="ghost" size="icon" onClick={onTogglePlayerMode}>
              {playerMode === 'audio' ? <Video className="h-5 w-5" /> : <MusicIcon className="h-5 w-5" />}
            </Button>
            <div className="flex items-center gap-2">
              <Volume2 className="h-5 w-5 text-muted-foreground" />
              <Slider
                defaultValue={[volume]} max={100} step={1}
                className="w-24" onValueChange={v => onVolumeChange(v[0])}
              />
            </div>
          </div>
        </div>
      </div>
    </PlayerContainer>
  );
});
LandscapePlayer.displayName = 'LandscapePlayer';

// ---------------------------------------------------------------------------
// Sortable queue item
// ---------------------------------------------------------------------------
const SortableSongItem = ({
  song, isCurrent, isPlaying, isLiked, hasBeenPlayed, onPlay, onRemove,
}: {
  song: Song; isCurrent: boolean; isPlaying: boolean;
  isLiked: boolean; hasBeenPlayed: boolean;
  onPlay: () => void; onRemove: () => void;
}) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: song.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 10 : ('auto' as any),
  };

  return (
    <div
      ref={setNodeRef}
      id={`queue-song-${song.id}`}
      style={style}
      className={cn("group flex items-center gap-2 p-2 rounded-md hover:bg-muted", isCurrent && "bg-secondary")}
    >
      <button {...attributes} {...listeners} className="p-2 cursor-grab rounded-md hover:bg-accent -ml-2">
        <GripVertical className="h-5 w-5 text-muted-foreground" />
        <span className="sr-only">Drag to reorder</span>
      </button>
      <div className="flex-1 flex items-center gap-4 min-w-0 cursor-pointer" onClick={onPlay}>
        <div className="relative h-12 w-12 shrink-0">
          <Image src={song.thumbnailUrl} alt={song.title} fill className="rounded-md object-cover" />
          {isCurrent && (
            <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
              {isPlaying
                ? <MusicIcon className="h-5 w-5 text-white animate-pulse" />
                : <Play className="h-5 w-5 text-white ml-0.5" />}
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className={cn("font-semibold", (isCurrent || isLiked) && "text-primary", "whitespace-normal break-words")}>
            {song.title}
          </p>
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground truncate">{song.artist}</p>
            {hasBeenPlayed && (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger>
                    <History className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  </TooltipTrigger>
                  <TooltipContent><p>Played</p></TooltipContent>
                </Tooltip>
              </TooltipProvider>
            )}
          </div>
        </div>
      </div>
      <Button
        variant="ghost" size="icon"
        className="h-8 w-8 shrink-0 opacity-0 group-hover:opacity-100"
        onClick={onRemove}
      >
        <X className="h-4 w-4" /><span className="sr-only">Remove from queue</span>
      </Button>
    </div>
  );
};

// ---------------------------------------------------------------------------
// Main MusicPlayer component
// ---------------------------------------------------------------------------
export function MusicPlayer() {
  const {
    currentTrack, playlist, history, playPlaylist, handleTrackError,
    clearQueue, isPlaying: isGlobalPlaying, setIsPlaying: setGlobalIsPlaying,
    playNext: globalPlayNext, playPrev: globalPlayPrev, shufflePlaylist,
    removeSongFromQueue, reorderPlaylist, playerMode, setPlayerMode,
    syncNativeIndex, initialLoadIsVideoShare, setInitialLoadIsVideoShare,
  } = usePlayer();

  const { playlists, addSongToPlaylist, toggleLikeSong, isSongLiked, removeSongFromDatabase } =
    usePlaylists();
  const { toast } = useToast();
  const pathname    = usePathname();
  const prevPathname = usePrevious(pathname);

  const playerRef                  = useRef<YouTubePlayer | null>(null);
  const playerAndVideoContainerRef = useRef<HTMLDivElement | null>(null);
  const isSeekingRef               = useRef(false);
  const isChangingTrackRef         = useRef(false);
  const progressIntervalRef        = useRef<NodeJS.Timeout | null>(null);
  const isGlobalPlayingRef         = useRef(isGlobalPlaying);
  const pendingNativeActionRef     = useRef<string | null>(null);
  const nativePlayRequestAtMsRef   = useRef(0);
  const lastNativeStateTsRef       = useRef(0);

  // UI state
  const [container, setContainer]                         = useState<HTMLElement | null>(null);
  const [volume, setVolume]                               = useState(100);
  const [isQueueOpen, setIsQueueOpen]                     = useState(false);
  const [isPip, setIsPip]                                 = useState(false);
  const [showControls, setShowControls]                   = useState(true);
  const [isCreatePlaylistDialogOpen, setIsCreatePlaylistDialogOpen] = useState(false);
  const controlsTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Progress state (local, rapidly updating)
  const [progress, setProgress]     = useState(0);
  const [duration, setDuration]     = useState(0);
  const [currentTime, setCurrentTime] = useState(0);

  // ---------------------------------------------------------------------------
  // Detect Android WebView runtime
  // ---------------------------------------------------------------------------
  const isAndroidAppRuntime = useMemo(() => {
    if (typeof window === 'undefined') return false;
    const ua                = navigator.userAgent || '';
    const isAndroidUa       = /Android/i.test(ua);
    const isFileProtocol    = window.location.protocol === 'file:';
    const isWebViewAssetHost = window.location.hostname === 'appassets.androidplatform.net';
    const hasNativeBridge   = typeof window.HarmonyNative !== 'undefined';
    return isAndroidUa && (isFileProtocol || isWebViewAssetHost || hasNativeBridge);
  }, []);

  // FIX #2: In audio mode on Android, we never touch the iframe.
  // In video mode we use the iframe. On web we always use the iframe.
  const shouldControlIframePlayback = !isAndroidAppRuntime || playerMode === 'video';

  // Keep ref in sync
  useEffect(() => { isGlobalPlayingRef.current = isGlobalPlaying; }, [isGlobalPlaying]);

  // ---------------------------------------------------------------------------
  // FIX #2: Listen for 'nativeSetVideoMode' from the Android service.
  // When entering video mode  → start the YouTube iframe.
  // When leaving video mode   → pause/stop the YouTube iframe.
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const handleSetVideoMode = (e: Event) => {
      const detail = (e as CustomEvent<{ enabled: boolean }>).detail;
      if (detail.enabled) {
        // Android switched to video mode: play the iframe if a track is loaded
        if (playerRef.current && typeof playerRef.current.playVideo === 'function') {
          playerRef.current.playVideo();
        }
      } else {
        // Android switched back to audio mode: pause/stop the iframe so there
        // is no overlap with ExoPlayer audio.
        if (playerRef.current && typeof playerRef.current.pauseVideo === 'function') {
          playerRef.current.pauseVideo();
        }
      }
    };

    window.addEventListener('nativeSetVideoMode', handleSetVideoMode);
    return () => window.removeEventListener('nativeSetVideoMode', handleSetVideoMode);
  }, []);

  // ---------------------------------------------------------------------------
  // Apply pending native action once the player is ready
  // ---------------------------------------------------------------------------
  const applyNativeCommand = useCallback((action: string) => {
    if (!action) return;
    const player = playerRef.current;
    const canControlPlayer =
      shouldControlIframePlayback &&
      !!player &&
      typeof player.getPlayerState === 'function';

    if (!canControlPlayer) {
      pendingNativeActionRef.current = action;
    }

    switch (action) {
      case 'com.sansoft.harmonystram.PLAY':
        if (canControlPlayer && typeof player!.playVideo === 'function') player!.playVideo();
        break;
      case 'com.sansoft.harmonystram.PAUSE':
        if (canControlPlayer && typeof player!.pauseVideo === 'function') player!.pauseVideo();
        break;
      case 'com.sansoft.harmonystram.NEXT':
        globalPlayNext();
        break;
      case 'com.sansoft.harmonystram.PREVIOUS':
        globalPlayPrev();
        break;
      default:
        break;
    }
  }, [shouldControlIframePlayback, globalPlayNext, globalPlayPrev]);

  // Expose the command handler globally so Android can call it
  useEffect(() => {
    window.__harmonyNativeApplyCommand = applyNativeCommand;
    return () => { delete window.__harmonyNativeApplyCommand; };
  }, [applyNativeCommand]);

  // ---------------------------------------------------------------------------
  // Listen for nativePlaybackCommand (next/prev from notification/Bluetooth)
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const handler = (e: Event) => {
      const action = (e as CustomEvent<{ action: string }>).detail?.action;
      if (action) applyNativeCommand(action);
    };
    window.addEventListener('nativePlaybackCommand', handler);
    return () => window.removeEventListener('nativePlaybackCommand', handler);
  }, [applyNativeCommand]);

  // ---------------------------------------------------------------------------
  // Listen for nativePlaybackState (state updates from Android service)
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<any>).detail;
      if (!detail) return;
      const ts = detail.event_ts ?? 0;
      if (ts < lastNativeStateTsRef.current) return; // stale update
      lastNativeStateTsRef.current = ts;

      const posMs = detail.position_ms ?? detail.currentPosition ?? 0;
      const durMs = detail.duration_ms ?? detail.duration ?? 0;
      setCurrentTime(posMs / 1000);
      setDuration(durMs / 1000);
      setProgress(durMs > 0 ? (posMs / durMs) * 100 : 0);

      if (typeof detail.playing === 'boolean') setGlobalIsPlaying(detail.playing);
    };

    window.addEventListener('nativePlaybackState', handler);
    return () => window.removeEventListener('nativePlaybackState', handler);
  }, [setGlobalIsPlaying]);

  // ---------------------------------------------------------------------------
  // Progress polling for iframe (web / video mode on Android)
  // ---------------------------------------------------------------------------
  const startProgressPolling = useCallback(() => {
    if (progressIntervalRef.current) clearInterval(progressIntervalRef.current);
    progressIntervalRef.current = setInterval(async () => {
      if (!playerRef.current || isSeekingRef.current) return;
      try {
        const pos = await playerRef.current.getCurrentTime();
        const dur = await playerRef.current.getDuration();
        if (!isSeekingRef.current) {
          setCurrentTime(pos);
          setDuration(dur);
          setProgress(dur > 0 ? (pos / dur) * 100 : 0);
        }
      } catch { /* player not ready */ }
    }, 500);
  }, []);

  const stopProgressPolling = useCallback(() => {
    if (progressIntervalRef.current) {
      clearInterval(progressIntervalRef.current);
      progressIntervalRef.current = null;
    }
  }, []);

  // Start/stop polling based on whether we should control the iframe
  useEffect(() => {
    if (shouldControlIframePlayback && isGlobalPlaying) {
      startProgressPolling();
    } else {
      stopProgressPolling();
    }
    return stopProgressPolling;
  }, [shouldControlIframePlayback, isGlobalPlaying, startProgressPolling, stopProgressPolling]);

  // ---------------------------------------------------------------------------
  // FIX #2: When playerMode changes, sync the iframe and native service.
  // Audio mode  → pause iframe, tell native to use ExoPlayer.
  // Video mode  → tell native to stop ExoPlayer, start iframe.
  // ---------------------------------------------------------------------------
  const handleTogglePlayerMode = useCallback(() => {
    const newMode = playerMode === 'audio' ? 'video' : 'audio';
    setPlayerMode(newMode);

    if (isAndroidAppRuntime) {
      // Notify the Android native layer (triggers switchMode() in PlaybackService)
      if (window.HarmonyNative?.setVideoMode) {
        window.HarmonyNative.setVideoMode(newMode === 'video');
      } else if (window.AndroidNative?.setVideoMode) {
        window.AndroidNative.setVideoMode(newMode === 'video');
      }
    }

    if (newMode === 'audio') {
      // Pause the YouTube iframe immediately so we don't get double audio
      if (playerRef.current && typeof playerRef.current.pauseVideo === 'function') {
        playerRef.current.pauseVideo();
      }
      stopProgressPolling();
    } else {
      // Video mode: play the iframe
      if (playerRef.current && typeof playerRef.current.playVideo === 'function') {
        playerRef.current.playVideo();
      }
      startProgressPolling();
    }
  }, [playerMode, setPlayerMode, isAndroidAppRuntime, stopProgressPolling, startProgressPolling]);

  // ---------------------------------------------------------------------------
  // Controls timeout (video mode auto-hide)
  // ---------------------------------------------------------------------------
  const resetControlsTimeout = useCallback(() => {
    setShowControls(true);
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    if (playerMode === 'video') {
      controlsTimeoutRef.current = setTimeout(() => setShowControls(false), 3000);
    }
  }, [playerMode]);

  useEffect(() => {
    if (playerMode !== 'video') {
      setShowControls(true);
      if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    } else {
      resetControlsTimeout();
    }
  }, [playerMode, resetControlsTimeout]);

  // ---------------------------------------------------------------------------
  // YouTube iframe event handlers
  // ---------------------------------------------------------------------------
  const onPlayerReady = useCallback((event: YouTubeEvent) => {
    playerRef.current = event.target;
    if (volume !== 100) event.target.setVolume(volume);

    // Apply any pending command that arrived before the player was ready
    if (pendingNativeActionRef.current) {
      applyNativeCommand(pendingNativeActionRef.current);
      pendingNativeActionRef.current = null;
    }

    // FIX #2: In audio mode on Android, do NOT auto-play the iframe.
    if (isAndroidAppRuntime && playerMode === 'audio') {
      event.target.pauseVideo();
      return;
    }
    if (isGlobalPlayingRef.current) event.target.playVideo();
  }, [volume, applyNativeCommand, isAndroidAppRuntime, playerMode]);

  const onPlayerStateChange = useCallback((event: YouTubeEvent) => {
    // YT.PlayerState: -1=unstarted, 0=ended, 1=playing, 2=paused, 3=buffering, 5=cued
    const state = event.data;

    // FIX #2: If we're in audio mode on Android, the iframe should NOT be
    // controlling playback – force-pause it to prevent double audio.
    if (isAndroidAppRuntime && playerMode === 'audio' && state === 1 /* playing */) {
      event.target.pauseVideo();
      return;
    }

    if (state === 1) {
      setGlobalIsPlaying(true);
      startProgressPolling();
    } else if (state === 2) {
      setGlobalIsPlaying(false);
      stopProgressPolling();
    } else if (state === 0) {
      // Ended → play next
      setGlobalIsPlaying(false);
      stopProgressPolling();
      globalPlayNext();
    }
  }, [
    isAndroidAppRuntime, playerMode,
    setGlobalIsPlaying, startProgressPolling, stopProgressPolling, globalPlayNext,
  ]);

  const onPlayerError = useCallback((_event: YouTubeEvent) => {
    if (currentTrack) handleTrackError(currentTrack.id);
  }, [handleTrackError, currentTrack]);

  // ---------------------------------------------------------------------------
  // Seek
  // ---------------------------------------------------------------------------
  const handleSeekChange = useCallback((value: number[]) => {
    isSeekingRef.current = true;
    const pct = value[0];
    setProgress(pct);
    setCurrentTime((pct / 100) * duration);
  }, [duration]);

  const handleSeekCommit = useCallback((value: number[]) => {
    const pct      = value[0];
    const targetS  = (pct / 100) * duration;
    const targetMs = Math.round(targetS * 1000);

    if (shouldControlIframePlayback && playerRef.current) {
      playerRef.current.seekTo(targetS, true);
    }

    if (isAndroidAppRuntime && window.HarmonyNative?.seek) {
      window.HarmonyNative.seek(targetMs);
    }

    isSeekingRef.current = false;
    setCurrentTime(targetS);
  }, [duration, shouldControlIframePlayback, isAndroidAppRuntime]);

  // ---------------------------------------------------------------------------
  // Play / Pause toggle
  // ---------------------------------------------------------------------------
  const handleTogglePlayPause = useCallback(() => {
    if (isAndroidAppRuntime) {
      if (isGlobalPlaying) {
        window.HarmonyNative?.pause?.();
      } else {
        window.HarmonyNative?.play?.();
      }
      return;
    }
    // Web / video mode: control the iframe directly
    if (playerRef.current) {
      if (isGlobalPlaying) {
        playerRef.current.pauseVideo();
      } else {
        playerRef.current.playVideo();
      }
    }
    setGlobalIsPlaying(!isGlobalPlaying);
  }, [isAndroidAppRuntime, isGlobalPlaying, setGlobalIsPlaying]);

  // ---------------------------------------------------------------------------
  // Skip
  // ---------------------------------------------------------------------------
  const handlePlayNext = useCallback(() => {
    if (isAndroidAppRuntime) {
      window.HarmonyNative?.next?.();
      return;
    }
    globalPlayNext();
  }, [isAndroidAppRuntime, globalPlayNext]);

  const handlePlayPrev = useCallback(() => {
    if (isAndroidAppRuntime) {
      window.HarmonyNative?.previous?.();
      return;
    }
    globalPlayPrev();
  }, [isAndroidAppRuntime, globalPlayPrev]);

  // ---------------------------------------------------------------------------
  // Volume
  // ---------------------------------------------------------------------------
  const handleVolumeChange = useCallback((value: number) => {
    setVolume(value);
    if (playerRef.current) playerRef.current.setVolume(value);
  }, []);

  // ---------------------------------------------------------------------------
  // Like / playlist / share
  // ---------------------------------------------------------------------------
  const isLiked = currentTrack ? isSongLiked(currentTrack.id) : false;

  const handleToggleLike = useCallback(() => {
    if (!currentTrack) return;
    toggleLikeSong(currentTrack);
  }, [currentTrack, toggleLikeSong]);

  const handleAddToPlaylist = useCallback((playlistId: string) => {
    if (!currentTrack) return;
    addSongToPlaylist(playlistId, currentTrack);
    toast({ title: 'Added to playlist' });
  }, [currentTrack, addSongToPlaylist, toast]);

  const handleShare = useCallback(() => {
    if (!currentTrack) return;
    const url = `https://www.youtube.com/watch?v=${currentTrack.id}`;
    if (navigator.share) {
      navigator.share({ title: currentTrack.title, url }).catch(() => {});
    } else {
      navigator.clipboard.writeText(url).then(() => {
        toast({ title: 'Link copied to clipboard' });
      });
    }
  }, [currentTrack, toast]);

  // ---------------------------------------------------------------------------
  // Queue management
  // ---------------------------------------------------------------------------
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      const oldIndex = playlist.findIndex(s => s.id === active.id);
      const newIndex = playlist.findIndex(s => s.id === over.id);
      if (oldIndex !== -1 && newIndex !== -1) reorderPlaylist(oldIndex, newIndex);
    }
  }, [playlist, reorderPlaylist]);

  // ---------------------------------------------------------------------------
  // YouTube opts – keep iframe hidden in audio mode
  // ---------------------------------------------------------------------------
  const youtubeOpts: YouTubeProps['opts'] = useMemo(() => ({
    width: '100%',
    height: '100%',
    playerVars: {
      autoplay: 1,
      controls: 0,
      modestbranding: 1,
      rel: 0,
      iv_load_policy: 3,
      // FIX #2: start muted in audio mode to prevent double audio during mode transitions
      mute: (isAndroidAppRuntime && playerMode === 'audio') ? 1 : 0,
    },
  }), [isAndroidAppRuntime, playerMode]);

  // ---------------------------------------------------------------------------
  // PiP listener
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ isInPictureInPictureMode: boolean }>).detail;
      setIsPip(detail?.isInPictureInPictureMode ?? false);
    };
    window.addEventListener('nativePictureInPictureChanged', handler);
    return () => window.removeEventListener('nativePictureInPictureChanged', handler);
  }, []);

  // ---------------------------------------------------------------------------
  // Mount container ref (for dropdown portals)
  // ---------------------------------------------------------------------------
  useEffect(() => {
    setContainer(document.body);
  }, []);

  // ---------------------------------------------------------------------------
  // Early return: nothing to render without a current track
  // ---------------------------------------------------------------------------
  if (!currentTrack) return null;

  // ---------------------------------------------------------------------------
  // Shared props for both player layouts
  // ---------------------------------------------------------------------------
  const sharedPlayerProps = {
    currentTrack,
    progress,
    duration,
    currentTime,
    isPlaying: isGlobalPlaying,
    isLiked,
    playerMode,
    volume,
    playlists,
    onToggleLike:              handleToggleLike,
    onTogglePlayerMode:        handleTogglePlayerMode,
    onPlayPrev:                handlePlayPrev,
    onPlayNext:                handlePlayNext,
    onTogglePlayPause:         handleTogglePlayPause,
    onVolumeChange:            handleVolumeChange,
    onSeekChange:              handleSeekChange,
    onSeekCommit:              handleSeekCommit,
    onAddToPlaylist:           handleAddToPlaylist,
    onShare:                   handleShare,
    onOpenCreatePlaylistDialog: setIsCreatePlaylistDialogOpen,
    container,
  };

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------
  return (
    <>
      {/* Create-playlist dialog */}
      <CreatePlaylistDialog
        open={isCreatePlaylistDialogOpen}
        onOpenChange={setIsCreatePlaylistDialogOpen}
      />

      <Sheet open={isQueueOpen} onOpenChange={setIsQueueOpen}>
        {/* ------------------------------------------------------------------ */}
        {/* Video container – only visible in video mode                        */}
        {/* ------------------------------------------------------------------ */}
        {playerMode === 'video' && (
          <div
            ref={playerAndVideoContainerRef}
            className="fixed inset-0 z-40 bg-black"
            onMouseMove={resetControlsTimeout}
            onClick={resetControlsTimeout}
          >
            {currentTrack && (
              <YouTube
                videoId={currentTrack.id}
                opts={youtubeOpts}
                onReady={onPlayerReady}
                onStateChange={onPlayerStateChange}
                onError={onPlayerError}
                className="w-full h-full"
                iframeClassName="w-full h-full"
              />
            )}
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {/* Hidden iframe in audio mode – keeps the player instance alive       */}
        {/* but muted/paused so ExoPlayer has exclusive audio focus.            */}
        {/* FIX #2: display:none prevents any audio output from the iframe.    */}
        {/* ------------------------------------------------------------------ */}
        {playerMode === 'audio' && currentTrack && (
          <div style={{ display: 'none' }} aria-hidden>
            <YouTube
              videoId={currentTrack.id}
              opts={{
                playerVars: {
                  autoplay: 0,
                  mute: 1,
                  controls: 0,
                },
              }}
              onReady={onPlayerReady}
              onStateChange={onPlayerStateChange}
              onError={onPlayerError}
            />
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {/* Player bars                                                         */}
        {/* ------------------------------------------------------------------ */}
        <PortraitPlayer {...sharedPlayerProps} />

        <LandscapePlayer
          {...sharedPlayerProps}
          isPip={isPip}
          showControls={showControls}
          onResetControlsTimeout={resetControlsTimeout}
        />

        {/* ------------------------------------------------------------------ */}
        {/* Queue sheet                                                         */}
        {/* ------------------------------------------------------------------ */}
        <SheetContent side="right" className="w-full sm:w-[400px] flex flex-col p-0">
          <SheetHeader className="p-4 border-b">
            <SheetTitle>Queue ({playlist.length} tracks)</SheetTitle>
          </SheetHeader>
          <div className="flex items-center gap-2 px-4 py-2 border-b">
            <Button variant="outline" size="sm" onClick={shufflePlaylist}>Shuffle</Button>
            <Button variant="outline" size="sm" onClick={clearQueue}>Clear</Button>
          </div>
          <ScrollArea className="flex-1">
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={playlist.map(s => s.id)}
                strategy={verticalListSortingStrategy}
              >
                <div className="p-2 space-y-1">
                  {playlist.map(song => {
                    const isCurrent    = song.id === currentTrack?.id;
                    const hasBeenPlayed = history.some(h => h.id === song.id);
                    return (
                      <SortableSongItem
                        key={song.id}
                        song={song}
                        isCurrent={isCurrent}
                        isPlaying={isCurrent && isGlobalPlaying}
                        isLiked={isSongLiked(song.id)}
                        hasBeenPlayed={hasBeenPlayed}
                        onPlay={() => {
                          const idx = playlist.findIndex(s => s.id === song.id);
                          if (idx !== -1) syncNativeIndex(idx);
                        }}
                        onRemove={() => removeSongFromQueue(song.id)}
                      />
                    );
                  })}
                </div>
              </SortableContext>
            </DndContext>
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </>
  );
}

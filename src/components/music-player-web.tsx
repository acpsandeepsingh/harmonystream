'use client';

/**
 * music-player.tsx — HarmonyStream hybrid player
 *
 * ARCHITECTURE
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  AUDIO MODE (Android)  →  ExoPlayer owns audio                  │
 * │    • No YouTube iframe rendered at all                           │
 * │    • UI state driven 100% by nativePlaybackState broadcasts      │
 * │    • Progress from Java broadcastState() every 500 ms           │
 * │    • Track changes synced via queue_index in broadcast           │
 * │                                                                  │
 * │  VIDEO MODE (Android)  →  YouTube iframe owns audio+video       │
 * │    • Full-screen iframe rendered                                  │
 * │    • UI state driven by iframe onStateChange events              │
 * │    • Native broadcasts IGNORED for playing/progress in this mode │
 * │    • key={currentTrack.id} forces remount on track change        │
 * │                                                                  │
 * │  WEB (non-Android)  →  YouTube iframe is the player             │
 * │    • Same as VIDEO mode path for iframe control                  │
 * │    • key={currentTrack.id} forces remount on track change        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * SYNC BUGS FIXED
 *  #1  nativePlaybackState now calls syncNativeIndex(queue_index) so
 *      currentTrack in context updates when native skips tracks.
 *  #2  Optimistic UI: play/pause button flips instantly; broadcast
 *      confirms or corrects within 500 ms (no stale button state).
 *  #3  Single source of truth per mode: native broadcasts drive audio
 *      mode; iframe events drive video/web mode. Cross-contamination
 *      prevented by mode-gated guards.
 *  #4  key={currentTrack.id} on every YouTube component forces a
 *      clean remount + onReady whenever the track changes.
 *  #5  Hidden iframe completely removed for Android audio mode.
 *      Web audio mode keeps the iframe (it IS the player) but with
 *      key so it remounts on track change.
 *  #6  Video mode nativePlaybackState broadcasts are ignored for
 *      playing/progress — iframe is the sole source of truth.
 *  #7  currentVideoIdRef tracks last loaded videoId so we never
 *      double-load or get stuck on stale state after mode switches.
 */

import { usePlayer } from '@/contexts/player-context';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Slider } from '@/components/ui/slider';
import {
  Play, Pause, SkipBack, SkipForward, Volume2,
  Video, Music as MusicIcon, Plus, ListMusic, PlusCircle,
  Heart, X, History, GripVertical, Share2,
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
} from '@/components/ui/dropdown-menu';
import { CreatePlaylistDialog } from './create-playlist-dialog';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import Image from 'next/image';
import type { Song } from '@/lib/types';
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
// Global bridge typings
// ---------------------------------------------------------------------------
declare global {
  interface Window {
    HarmonyNative?: {
      play?:         ()                   => void;
      pause?:        ()                   => void;
      next?:         ()                   => void;
      previous?:     ()                   => void;
      setIndex?:     (index: number)      => void;
      seek?:         (positionMs: number) => void;
      setQueue?:     (queueJson: string)  => void;
      loadMedia?:    (
        mediaUrl: string,
        mediaType: string,
        title?: string,
        artist?: string,
        thumbnailUrl?: string,
      ) => void;
      setVideoMode?: (enabled: boolean)   => void;
      updateState?:  (
        title: string, artist: string, playing: boolean,
        positionMs: number, durationMs: number, thumbnailUrl: string,
      ) => void;
      getState?: () => void;
    };
    AndroidNative?: {
      play?:         (id?: string, title?: string, artist?: string,
                      durationMs?: number, thumbnailUrl?: string) => void;
      pause?:        ()                   => void;
      resume?:       ()                   => void;
      seekTo?:       (positionMs: number) => void;
      loadMedia?:    (
        mediaUrl: string,
        mediaType: string,
        title?: string,
        artist?: string,
        thumbnailUrl?: string,
      ) => void;
      setVideoMode?: (enabled: boolean)   => void;
    };
    updateProgress?:              (positionMs: number, durationMs: number) => void;
    __harmonyNativeApplyCommand?: (action: string) => void;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const formatDuration = (seconds: number) => {
  if (!isFinite(seconds) || seconds < 0) return '0:00';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
};

// ---------------------------------------------------------------------------
// PortraitPlayer  (mobile bottom bar — shown on small screens)
// ---------------------------------------------------------------------------
const PortraitPlayer = React.memo(function PortraitPlayer({
  currentTrack, progress, duration, currentTime, isPlaying, isLiked,
  playerMode, volume, playlists,
  onToggleLike, onTogglePlayerMode, onPlayPrev, onPlayNext,
  onTogglePlayPause, onVolumeChange, onSeekChange, onSeekCommit,
  onAddToPlaylist, onShare, onOpenCreatePlaylistDialog, container,
}: any) {
  return (
    <div className={cn(
      'md:hidden flex flex-col fixed bottom-0 left-0 right-0 z-50',
      'bg-background border-t text-foreground',
    )}>
      {/* Seek bar */}
      <div className="w-full flex items-center gap-2 px-2 pt-2">
        <span className="text-xs w-10 text-right text-muted-foreground">
          {formatDuration(currentTime)}
        </span>
        <Slider
          value={[progress]} max={100} step={0.1}
          onValueChange={onSeekChange} onValueCommit={onSeekCommit}
          className="w-full [&>span:first-of-type]:h-4 [&_.h-2]:h-3"
        />
        <span className="text-xs w-10 text-muted-foreground">
          {formatDuration(duration)}
        </span>
      </div>

      {/* Track info */}
      <div className="w-full flex items-center p-2">
        <div className="min-w-0 flex-1 flex items-center gap-3">
          <Avatar className="h-12 w-12 rounded-md shrink-0">
            <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
            <AvatarFallback>{currentTrack.artist?.charAt(0) ?? '?'}</AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <p className="font-bold truncate text-base">{currentTrack.title}</p>
            <p className="truncate text-sm text-muted-foreground">{currentTrack.artist}</p>
          </div>
        </div>
        <div className="flex items-center shrink-0">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onToggleLike}>
            <Heart className={cn('h-5 w-5', isLiked && 'fill-red-500 text-red-500')} />
          </Button>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8">
              <ListMusic className="h-5 w-5" />
            </Button>
          </SheetTrigger>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8"
                onClick={e => e.stopPropagation()}>
                <Plus className="h-5 w-5" />
                <span className="sr-only">Add to playlist</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent container={container} align="end" forceMount>
              <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
              <DropdownMenuSeparator />
              {playlists.map((pl: any) => (
                <DropdownMenuItem key={pl.id} onClick={() => onAddToPlaylist(pl.id)}>
                  <ListMusic className="mr-2 h-4 w-4" />
                  <span>{pl.name}</span>
                </DropdownMenuItem>
              ))}
              {playlists.length > 0 && <DropdownMenuSeparator />}
              <DropdownMenuItem onSelect={() => onOpenCreatePlaylistDialog(true)}>
                <PlusCircle className="mr-2 h-4 w-4" />Create new playlist
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onShare}>
            <Share2 className="h-5 w-5" />
            <span className="sr-only">Share</span>
          </Button>
        </div>
      </div>

      {/* Controls */}
      <div className="w-full flex items-center justify-between px-2 pb-2">
        <div className="w-20">
          <Button variant="ghost" size="icon" className="h-8 w-8"
            onClick={onTogglePlayerMode}>
            {playerMode === 'audio'
              ? <Video className="h-5 w-5" />
              : <MusicIcon className="h-5 w-5" />}
          </Button>
        </div>
        <div className="flex items-center">
          <Button variant="ghost" size="icon" onClick={onPlayPrev}>
            <SkipBack className="h-6 w-6" />
          </Button>
          <Button variant="ghost" size="icon" className="h-14 w-14"
            onClick={onTogglePlayPause}>
            {isPlaying
              ? <Pause className="h-10 w-10" />
              : <Play  className="h-10 w-10 ml-1" />}
          </Button>
          <Button variant="ghost" size="icon" onClick={onPlayNext}>
            <SkipForward className="h-6 w-6" />
          </Button>
        </div>
        <div className="flex items-center gap-1 w-20">
          <Volume2 className="h-5 w-5 text-muted-foreground" />
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
// LandscapePlayer  (desktop bar + video mode overlay)
// ---------------------------------------------------------------------------
const LandscapePlayer = React.memo(function LandscapePlayer({
  currentTrack, progress, duration, currentTime, isPlaying, isLiked,
  playerMode, volume, playlists, isPip, showControls,
  onToggleLike, onTogglePlayerMode, onPlayPrev, onPlayNext,
  onTogglePlayPause, onVolumeChange, onSeekChange, onSeekCommit,
  onAddToPlaylist, onShare, onOpenCreatePlaylistDialog,
  onResetControlsTimeout, container,
}: any) {
  const isVideo = playerMode === 'video';

  return (
    <div className={cn(
      'hidden md:block',
      !isVideo && 'w-full fixed bottom-0 left-0 right-0 z-[60]',
      !isVideo && isPip && 'opacity-0 pointer-events-none',
    )}>
      <div
        className={cn(
          'w-full flex flex-col p-2 gap-1 transition-all duration-300',
          isVideo
            ? 'bg-gradient-to-t from-black/80 to-transparent text-white fixed bottom-0 left-0 right-0 z-[60]'
            : 'border-t bg-card/95 backdrop-blur-xl text-foreground',
        )}
        onMouseMove={onResetControlsTimeout}
      >
        {/* Seek bar */}
        <div className={cn(
          'relative w-full flex items-center gap-2 px-2 transition-opacity duration-300 z-10',
          isVideo && !showControls && 'opacity-0 pointer-events-none',
        )}>
          <span className="text-xs w-10 text-right text-muted-foreground">
            {formatDuration(currentTime)}
          </span>
          <Slider
            value={[progress]} max={100} step={0.1}
            onValueChange={onSeekChange} onValueCommit={onSeekCommit}
            className="w-full [&>span:first-of-type]:h-4 [&_.h-2]:h-1"
          />
          <span className="text-xs w-10 text-muted-foreground">
            {formatDuration(duration)}
          </span>
        </div>

        {/* Controls row */}
        <div className={cn(
          'relative w-full flex items-center justify-between gap-2 h-[64px] z-10',
          'transition-opacity duration-300',
          isVideo && !showControls && 'opacity-0 pointer-events-none',
        )}>
          {/* Left: track info */}
          <div className="flex items-center gap-3 min-w-0 w-1/3">
            <Avatar className="h-12 w-12 rounded-md shrink-0">
              <AvatarImage src={currentTrack.thumbnailUrl} alt={currentTrack.title} />
              <AvatarFallback>{currentTrack.artist?.charAt(0) ?? '?'}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <p className="font-bold truncate">{currentTrack.title}</p>
              <p className="text-sm truncate text-muted-foreground">{currentTrack.artist}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={onToggleLike}>
              <Heart className={cn('h-5 w-5', isLiked && 'fill-red-500 text-red-500')} />
            </Button>
          </div>

          {/* Center: transport */}
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={onPlayPrev}>
              <SkipBack className="h-5 w-5" />
            </Button>
            <Button variant="default" size="icon" className="h-10 w-10"
              onClick={onTogglePlayPause}>
              {isPlaying
                ? <Pause className="h-5 w-5" />
                : <Play  className="h-5 w-5 ml-0.5" />}
            </Button>
            <Button variant="ghost" size="icon" onClick={onPlayNext}>
              <SkipForward className="h-5 w-5" />
            </Button>
          </div>

          {/* Right: extras */}
          <div className="flex items-center gap-2 w-1/3 justify-end">
            <Button variant="ghost" size="icon" onClick={onShare}>
              <Share2 className="h-5 w-5" />
              <span className="sr-only">Share</span>
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon"
                  onClick={e => e.stopPropagation()}>
                  <Plus className="h-5 w-5" />
                  <span className="sr-only">Add to playlist</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent container={container} align="end">
                <DropdownMenuLabel>Add to Playlist</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {playlists.map((pl: any) => (
                  <DropdownMenuItem key={pl.id}
                    onClick={() => onAddToPlaylist(pl.id)}>
                    <ListMusic className="mr-2 h-4 w-4" />
                    <span>{pl.name}</span>
                  </DropdownMenuItem>
                ))}
                {playlists.length > 0 && <DropdownMenuSeparator />}
                <DropdownMenuItem
                  onSelect={() => onOpenCreatePlaylistDialog(true)}>
                  <PlusCircle className="mr-2 h-4 w-4" />Create new playlist
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon">
                <ListMusic className="h-5 w-5" />
                <span className="sr-only">Queue</span>
              </Button>
            </SheetTrigger>
            <Button variant="ghost" size="icon" onClick={onTogglePlayerMode}>
              {playerMode === 'audio'
                ? <Video className="h-5 w-5" />
                : <MusicIcon className="h-5 w-5" />}
            </Button>
            <div className="flex items-center gap-2">
              <Volume2 className="h-5 w-5 text-muted-foreground" />
              <Slider
                defaultValue={[volume]} max={100} step={1}
                className="w-24"
                onValueChange={v => onVolumeChange(v[0])}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});
LandscapePlayer.displayName = 'LandscapePlayer';

// ---------------------------------------------------------------------------
// SortableSongItem
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

  return (
    <div
      ref={setNodeRef}
      id={`queue-song-${song.id}`}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
        zIndex:  isDragging ? 10 : 'auto' as any,
      }}
      className={cn(
        'group flex items-center gap-2 p-2 rounded-md hover:bg-muted',
        isCurrent && 'bg-secondary',
      )}
    >
      <button {...attributes} {...listeners}
        className="p-2 cursor-grab rounded-md hover:bg-accent -ml-2">
        <GripVertical className="h-5 w-5 text-muted-foreground" />
        <span className="sr-only">Drag to reorder</span>
      </button>

      <div className="flex-1 flex items-center gap-4 min-w-0 cursor-pointer"
        onClick={onPlay}>
        <div className="relative h-12 w-12 shrink-0">
          <Image src={song.thumbnailUrl} alt={song.title} fill
            className="rounded-md object-cover" />
          {isCurrent && (
            <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
              {isPlaying
                ? <MusicIcon className="h-5 w-5 text-white animate-pulse" />
                : <Play      className="h-5 w-5 text-white ml-0.5" />}
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className={cn(
            'font-semibold whitespace-normal break-words',
            (isCurrent || isLiked) && 'text-primary',
          )}>
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

      <Button variant="ghost" size="icon"
        className="h-8 w-8 shrink-0 opacity-0 group-hover:opacity-100"
        onClick={onRemove}>
        <X className="h-4 w-4" />
        <span className="sr-only">Remove</span>
      </Button>
    </div>
  );
};

// ===========================================================================
// MusicPlayer — main component
// ===========================================================================
export function WebMusicPlayer() {
  // ── Context ────────────────────────────────────────────────────────────────
  const {
    currentTrack, playlist, history,
    isPlaying: isGlobalPlaying, setIsPlaying: setGlobalIsPlaying,
    playNext: globalPlayNext, playPrev: globalPlayPrev,
    shufflePlaylist, clearQueue,
    removeSongFromQueue, reorderPlaylist,
    playerMode, setPlayerMode,
    syncNativeIndex,
    handleTrackError,
  } = usePlayer();

  const {
    playlists, addSongToPlaylist, toggleLikeSong, isSongLiked,
  } = usePlaylists();

  const { toast } = useToast();
  const pathname    = usePathname();
  const prevPathname = usePrevious(pathname);

  // ── Refs ───────────────────────────────────────────────────────────────────
  const playerRef              = useRef<YouTubePlayer | null>(null);
  const isSeekingRef           = useRef(false);
  const progressIntervalRef    = useRef<NodeJS.Timeout | null>(null);
  const controlsTimeoutRef     = useRef<NodeJS.Timeout | null>(null);
  const pendingActionRef       = useRef<string | null>(null);
  const lastNativeStateTsRef   = useRef(0);

  /**
   * Tracks the videoId that is currently loaded in the iframe.
   * Used to detect when a track change requires a full remount vs
   * a simple seek/play. The `key` prop handles remounting but this
   * ref lets us guard against stale onReady callbacks.
   */
  const currentVideoIdRef = useRef<string>('');

  // ── UI state ───────────────────────────────────────────────────────────────
  const [container, setContainer]   = useState<HTMLElement | null>(null);
  const [volume, setVolume]         = useState(100);
  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [isPip, setIsPip]           = useState(false);
  const [showControls, setShowControls] = useState(true);
  const [isCreatePlaylistDialogOpen, setIsCreatePlaylistDialogOpen] = useState(false);

  // Progress — updated at 500 ms cadence from either polling or native broadcast
  const [progress,    setProgress]    = useState(0);
  const [duration,    setDuration]    = useState(0);
  const [currentTime, setCurrentTime] = useState(0);

  // ── Runtime detection ──────────────────────────────────────────────────────
  /**
   * True when running inside the HarmonyStream Android WebView.
   * Stable after mount — never changes during a session.
   */
  const isAndroidAppRuntime = useMemo(() => {
    if (typeof window === 'undefined') return false;
    const ua = navigator.userAgent ?? '';
    return (
      /Android/i.test(ua) && (
        window.location.protocol === 'file:' ||
        window.location.hostname === 'appassets.androidplatform.net' ||
        typeof window.HarmonyNative !== 'undefined'
      )
    );
  }, []);

  /**
   * When true the YouTube iframe is the active audio engine.
   * When false (Android audio mode) ExoPlayer owns audio and the
   * iframe must NOT be rendered or polled.
   */
  const iframeIsPlayer = !isAndroidAppRuntime || playerMode === 'video';

  // ── Progress helpers ───────────────────────────────────────────────────────
  const stopProgressPolling = useCallback(() => {
    if (progressIntervalRef.current) {
      clearInterval(progressIntervalRef.current);
      progressIntervalRef.current = null;
    }
  }, []);

  const startProgressPolling = useCallback(() => {
    stopProgressPolling();
    progressIntervalRef.current = setInterval(async () => {
      if (!playerRef.current || isSeekingRef.current) return;
      try {
        const pos = await playerRef.current.getCurrentTime() as number;
        const dur = await playerRef.current.getDuration()    as number;
        if (!isSeekingRef.current && isFinite(pos) && isFinite(dur)) {
          setCurrentTime(pos);
          setDuration(dur);
          setProgress(dur > 0 ? (pos / dur) * 100 : 0);
        }
      } catch { /* player not yet ready */ }
    }, 500);
  }, [stopProgressPolling]);

  // Expose progress updater for Java's window.updateProgress() call
  useEffect(() => {
    window.updateProgress = (posMs: number, durMs: number) => {
      if (isSeekingRef.current) return;
      const pos = posMs / 1000;
      const dur = durMs / 1000;
      setCurrentTime(pos);
      setDuration(dur);
      setProgress(dur > 0 ? (pos / dur) * 100 : 0);
    };
    return () => { window.updateProgress = undefined; };
  }, []);

  // ── SYNC #1: nativePlaybackState broadcast handler ─────────────────────────
  /**
   * Sole source of truth for AUDIO MODE on Android.
   * Updates: isPlaying, progress, currentTrack (via queue_index sync).
   * In VIDEO MODE: only updates currentTrack — iframe owns isPlaying/progress.
   */
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<any>).detail;
      if (!detail) return;

      // Discard stale out-of-order broadcasts
      const ts = (detail.event_ts as number) ?? 0;
      if (ts && ts < lastNativeStateTsRef.current) return;
      if (ts) lastNativeStateTsRef.current = ts;

      // ── SYNC #1: update currentTrack when native queue index changes ──────
      // This is the primary fix for "UI shows wrong song after Bluetooth skip".
      const queueIndex = detail.queue_index ?? detail.currentIndex ?? -1;
      if (queueIndex >= 0) {
        // syncNativeIndex updates PlayerContext.currentTrack to playlist[queueIndex]
        syncNativeIndex(queueIndex);
      }

      // ── SYNC #3: single source of truth per mode ──────────────────────────
      // In VIDEO MODE the iframe owns isPlaying and progress — ignore these
      // fields from the broadcast to prevent the two sources fighting.
      if (playerMode === 'video') return;

      // AUDIO MODE: trust the native broadcast completely
      const posMs = detail.position_ms ?? detail.currentPosition ?? 0;
      const durMs = detail.duration_ms ?? detail.duration         ?? 0;

      if (!isSeekingRef.current) {
        setCurrentTime(posMs / 1000);
        setDuration(durMs / 1000);
        setProgress(durMs > 0 ? (posMs / durMs) * 100 : 0);
      }

      // ── SYNC #2: only update playing state when it actually differs ───────
      // Prevents the 500 ms "stale button" flicker after optimistic UI update.
      if (typeof detail.playing === 'boolean') {
        setGlobalIsPlaying(detail.playing);
      }
    };

    window.addEventListener('nativePlaybackState', handler);
    return () => window.removeEventListener('nativePlaybackState', handler);
  }, [playerMode, syncNativeIndex, setGlobalIsPlaying]);

  // ── SYNC: nativeSetVideoMode — service tells JS to switch mode ─────────────
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ enabled: boolean }>).detail;
      if (!detail) return;
      const newMode = detail.enabled ? 'video' : 'audio';
      setPlayerMode(newMode);
      if (!detail.enabled) {
        // Switching to audio mode: stop iframe polling, reset progress
        // so stale iframe position doesn't flash in the UI.
        stopProgressPolling();
        if (playerRef.current) {
          try { playerRef.current.pauseVideo(); } catch { /* ignore */ }
        }
      }
    };
    window.addEventListener('nativeSetVideoMode', handler);
    return () => window.removeEventListener('nativeSetVideoMode', handler);
  }, [setPlayerMode, stopProgressPolling]);

  // ── SYNC: nativePlaybackCommand (Bluetooth / notification buttons) ─────────
  const applyNativeCommand = useCallback((action: string) => {
    if (!action) return;
    switch (action) {
      case 'com.sansoft.harmonystram.PLAY':
        if (iframeIsPlayer && playerRef.current) {
          try { playerRef.current.playVideo(); } catch { /* ignore */ }
        } else {
          setGlobalIsPlaying(true);
        }
        break;
      case 'com.sansoft.harmonystram.PAUSE':
        if (iframeIsPlayer && playerRef.current) {
          try { playerRef.current.pauseVideo(); } catch { /* ignore */ }
        } else {
          setGlobalIsPlaying(false);
        }
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
  }, [iframeIsPlayer, setGlobalIsPlaying, globalPlayNext, globalPlayPrev]);

  useEffect(() => {
    window.__harmonyNativeApplyCommand = applyNativeCommand;
    return () => { delete window.__harmonyNativeApplyCommand; };
  }, [applyNativeCommand]);

  useEffect(() => {
    const handler = (e: Event) => {
      const action = (e as CustomEvent<{ action: string }>).detail?.action;
      if (action) applyNativeCommand(action);
    };
    window.addEventListener('nativePlaybackCommand', handler);
    return () => window.removeEventListener('nativePlaybackCommand', handler);
  }, [applyNativeCommand]);

  // ── SYNC: PiP ─────────────────────────────────────────────────────────────
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ isInPictureInPictureMode: boolean }>).detail;
      setIsPip(detail?.isInPictureInPictureMode ?? false);
    };
    window.addEventListener('nativePictureInPictureChanged', handler);
    return () => window.removeEventListener('nativePictureInPictureChanged', handler);
  }, []);

  // ── iframe polling lifecycle ───────────────────────────────────────────────
  // Start polling whenever the iframe is the player AND something is playing.
  useEffect(() => {
    if (iframeIsPlayer && isGlobalPlaying) {
      startProgressPolling();
    } else {
      stopProgressPolling();
    }
    return stopProgressPolling;
  }, [iframeIsPlayer, isGlobalPlaying, startProgressPolling, stopProgressPolling]);

  // ── Reset progress when track changes ─────────────────────────────────────
  // Prevents old position bleeding into new track's seek bar.
  const prevTrackId = usePrevious(currentTrack?.id);
  useEffect(() => {
    if (currentTrack?.id && currentTrack.id !== prevTrackId) {
      setProgress(0);
      setCurrentTime(0);
      setDuration(0);
      currentVideoIdRef.current = currentTrack.id;
    }
  }, [currentTrack?.id, prevTrackId]);

  // ── YouTube iframe events ──────────────────────────────────────────────────

  /**
   * SYNC #4 & #7: onPlayerReady is called after every remount
   * (guaranteed by key={currentTrack.id}).
   * Guard with currentVideoIdRef so a stale callback from a
   * previous track never starts playing the wrong video.
   */
  const onPlayerReady = useCallback((event: YouTubeEvent) => {
    const expectedId = currentVideoIdRef.current;
    playerRef.current = event.target;

    // Set volume
    if (volume !== 100) {
      try { event.target.setVolume(volume); } catch { /* ignore */ }
    }

    // Apply any command that arrived before the player was ready
    if (pendingActionRef.current) {
      const pending = pendingActionRef.current;
      pendingActionRef.current = null;
      applyNativeCommand(pending);
      return;
    }

    // Only auto-play if this ready event belongs to the current track
    const loadedId: string = (() => {
      try { return event.target.getVideoData?.()?.video_id ?? expectedId; }
      catch { return expectedId; }
    })();

    if (loadedId !== expectedId) {
      // Stale ready event — ignore
      return;
    }

    if (isGlobalPlaying) {
      try { event.target.playVideo(); } catch { /* ignore */ }
    }
  }, [volume, applyNativeCommand, isGlobalPlaying]);

  /**
   * SYNC #3 & #6: iframe state changes are the source of truth in
   * video/web mode.  In audio mode on Android this callback should
   * never fire (no iframe rendered), but guard anyway.
   */
  const onPlayerStateChange = useCallback((event: YouTubeEvent) => {
    // Safety: if for any reason the iframe fires in audio mode on Android, ignore.
    if (isAndroidAppRuntime && playerMode === 'audio') return;

    const state = event.data as number;
    // YT.PlayerState: -1=unstarted, 0=ended, 1=playing, 2=paused, 3=buffering, 5=cued
    switch (state) {
      case 1: // playing
        setGlobalIsPlaying(true);
        startProgressPolling();
        break;
      case 2: // paused
        setGlobalIsPlaying(false);
        stopProgressPolling();
        break;
      case 0: // ended
        setGlobalIsPlaying(false);
        stopProgressPolling();
        globalPlayNext();
        break;
      default:
        break;
    }
  }, [
    isAndroidAppRuntime, playerMode,
    setGlobalIsPlaying, startProgressPolling, stopProgressPolling, globalPlayNext,
  ]);

  const onPlayerError = useCallback((_event: YouTubeEvent) => {
    if (currentTrack) handleTrackError(currentTrack.id);
  }, [currentTrack, handleTrackError]);

  // ── Controls auto-hide (video mode) ───────────────────────────────────────
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

  // ── Transport handlers ─────────────────────────────────────────────────────

  /**
   * SYNC #2: Optimistic UI — flip the button immediately then let the
   * native broadcast or iframe event confirm/correct within 500 ms.
   */
  const handleTogglePlayPause = useCallback(() => {
    const next = !isGlobalPlaying;
    // Optimistic update — prevents 500 ms stale button appearance
    setGlobalIsPlaying(next);

    if (isAndroidAppRuntime) {
      if (next) {
        window.HarmonyNative?.play?.();
      } else {
        window.HarmonyNative?.pause?.();
      }
    } else if (playerRef.current) {
      try {
        if (next) playerRef.current.playVideo();
        else      playerRef.current.pauseVideo();
      } catch { /* ignore */ }
    }
  }, [isAndroidAppRuntime, isGlobalPlaying, setGlobalIsPlaying]);

  const handlePlayNext = useCallback(() => {
    if (isAndroidAppRuntime) {
      window.HarmonyNative?.next?.();
    } else {
      globalPlayNext();
    }
  }, [isAndroidAppRuntime, globalPlayNext]);

  const handlePlayPrev = useCallback(() => {
    if (isAndroidAppRuntime) {
      window.HarmonyNative?.previous?.();
    } else {
      globalPlayPrev();
    }
  }, [isAndroidAppRuntime, globalPlayPrev]);

  const handleSeekChange = useCallback((value: number[]) => {
    isSeekingRef.current = true;
    setProgress(value[0]);
    setCurrentTime((value[0] / 100) * duration);
  }, [duration]);

  const handleSeekCommit = useCallback((value: number[]) => {
    const targetS  = (value[0] / 100) * duration;
    const targetMs = Math.round(targetS * 1000);

    if (iframeIsPlayer && playerRef.current) {
      try { playerRef.current.seekTo(targetS, true); } catch { /* ignore */ }
    }
    if (isAndroidAppRuntime) {
      window.HarmonyNative?.seek?.(targetMs);
    }

    setCurrentTime(targetS);
    isSeekingRef.current = false;
  }, [duration, iframeIsPlayer, isAndroidAppRuntime]);

  const handleVolumeChange = useCallback((value: number) => {
    setVolume(value);
    if (playerRef.current) {
      try { playerRef.current.setVolume(value); } catch { /* ignore */ }
    }
  }, []);

  // ── Mode toggle ────────────────────────────────────────────────────────────
  const handleTogglePlayerMode = useCallback(() => {
    const newMode = playerMode === 'audio' ? 'video' : 'audio';
    setPlayerMode(newMode);

    if (isAndroidAppRuntime) {
      window.HarmonyNative?.setVideoMode?.(newMode === 'video') ??
        window.AndroidNative?.setVideoMode?.(newMode === 'video');
    }

    if (newMode === 'audio') {
      stopProgressPolling();
      // Pause iframe so ExoPlayer can claim audio focus cleanly
      if (playerRef.current) {
        try { playerRef.current.pauseVideo(); } catch { /* ignore */ }
      }
    }
    // Video mode: iframe mounts fresh (key changes) → onPlayerReady auto-plays
  }, [playerMode, setPlayerMode, isAndroidAppRuntime, stopProgressPolling]);

  // ── Like / playlist / share ────────────────────────────────────────────────
  const isLiked = currentTrack ? isSongLiked(currentTrack.id) : false;

  const handleToggleLike = useCallback(() => {
    if (currentTrack) toggleLikeSong(currentTrack);
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
      navigator.clipboard.writeText(url).then(() =>
        toast({ title: 'Link copied to clipboard' }));
    }
  }, [currentTrack, toast]);

  // ── Queue DnD ──────────────────────────────────────────────────────────────
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = playlist.findIndex(s => s.id === active.id);
    const newIndex = playlist.findIndex(s => s.id === over.id);
    if (oldIndex !== -1 && newIndex !== -1) reorderPlaylist(oldIndex, newIndex);
  }, [playlist, reorderPlaylist]);

  // ── Mount ──────────────────────────────────────────────────────────────────
  useEffect(() => { setContainer(document.body); }, []);

  // ── YouTube opts ───────────────────────────────────────────────────────────
  /**
   * SYNC #5 & #7:
   *  • In video/web mode: autoplay=1, mute=0, controls=0 (we draw our own)
   *  • In Android audio mode: NO iframe rendered at all (see JSX below)
   */
  const youtubeOpts: YouTubeProps['opts'] = useMemo(() => ({
    width:  '100%',
    height: '100%',
    playerVars: {
      autoplay:       1,
      controls:       0,
      modestbranding: 1,
      rel:            0,
      iv_load_policy: 3,
    },
  }), []);

  // ── Early exit ─────────────────────────────────────────────────────────────
  if (!currentTrack) return null;

  // ── Shared props ───────────────────────────────────────────────────────────
  const sharedPlayerProps = {
    currentTrack,
    progress,
    duration,
    currentTime,
    isPlaying:   isGlobalPlaying,
    isLiked,
    playerMode,
    volume,
    playlists,
    container,
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
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      <CreatePlaylistDialog
        open={isCreatePlaylistDialogOpen}
        onOpenChange={setIsCreatePlaylistDialogOpen}
      />

      <Sheet open={isQueueOpen} onOpenChange={setIsQueueOpen}>

        {/* ═══════════════════════════════════════════════════════════════════
            VIDEO MODE — full-screen YouTube iframe
            SYNC #4 & #7: key={currentTrack.id} forces a clean iframe remount
            whenever the track changes, so onPlayerReady always fires fresh.
        ════════════════════════════════════════════════════════════════════ */}
        {playerMode === 'video' && (
          <div
            className="fixed inset-0 z-40 bg-black"
            onMouseMove={resetControlsTimeout}
            onClick={resetControlsTimeout}
          >
            <YouTube
              key={currentTrack.id}
              videoId={currentTrack.id}
              opts={youtubeOpts}
              onReady={onPlayerReady}
              onStateChange={onPlayerStateChange}
              onError={onPlayerError}
              className="w-full h-full"
              iframeClassName="w-full h-full"
            />
          </div>
        )}

        {/* ═══════════════════════════════════════════════════════════════════
            WEB AUDIO MODE — invisible iframe, iframe IS the audio engine.
            SYNC #5: Only rendered on non-Android (web browser).
            SYNC #7: key={currentTrack.id} forces remount on track change.
            Not rendered on Android audio mode — ExoPlayer owns audio there.
        ════════════════════════════════════════════════════════════════════ */}
        {playerMode === 'audio' && !isAndroidAppRuntime && (
          <div style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden' }}
            aria-hidden>
            <YouTube
              key={currentTrack.id}
              videoId={currentTrack.id}
              opts={youtubeOpts}
              onReady={onPlayerReady}
              onStateChange={onPlayerStateChange}
              onError={onPlayerError}
            />
          </div>
        )}

        {/* ═══════════════════════════════════════════════════════════════════
            PLAYER BARS — always visible (portrait + landscape)
        ════════════════════════════════════════════════════════════════════ */}
        <PortraitPlayer  {...sharedPlayerProps} />
        <LandscapePlayer
          {...sharedPlayerProps}
          isPip={isPip}
          showControls={showControls}
          onResetControlsTimeout={resetControlsTimeout}
        />

        {/* ═══════════════════════════════════════════════════════════════════
            QUEUE SHEET
        ════════════════════════════════════════════════════════════════════ */}
        <SheetContent side="right"
          className="w-full sm:w-[400px] flex flex-col p-0">
          <SheetHeader className="p-4 border-b">
            <SheetTitle>Queue ({playlist.length} tracks)</SheetTitle>
          </SheetHeader>
          <div className="flex items-center gap-2 px-4 py-2 border-b">
            <Button variant="outline" size="sm" onClick={shufflePlaylist}>
              Shuffle
            </Button>
            <Button variant="outline" size="sm" onClick={clearQueue}>
              Clear
            </Button>
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
                    const isCurrent    = song.id === currentTrack.id;
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

'use client';

import type { Song } from '@/lib/types';
import { createContext, useContext, useState, type ReactNode, useCallback, useEffect, Suspense } from 'react';
import { useToast } from '@/hooks/use-toast';
import { getSongByVideoId } from '@/lib/youtube';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import {
  clearPlayerPersistence,
  removeVersionedStorage,
  safeReadVersionedStorage,
  writeVersionedStorage,
} from '@/lib/persisted-state';

interface PlayerContextType {
  currentTrack: Song | null;
  isPlaying: boolean;
  playlist: Song[];
  history: Song[];
  playerMode: 'audio' | 'video';
  isMounted: boolean;
  initialLoadIsVideoShare: boolean;
  setInitialLoadIsVideoShare: (value: boolean) => void;
  playTrack: (track: Song) => void;
  playPlaylist: (playlist: Song[], startingTrackId?: string) => void;
  togglePlayPause: () => void;
  playNext: () => void;
  playPrev: () => void;
  setIsPlaying: (isPlaying: boolean) => void;
  setPlayerMode: (mode: 'audio' | 'video') => void;
  syncNativeIndex: (index: number) => void;
  isPlayerVisible: boolean;
  shufflePlaylist: () => void;
  handleTrackError: (songId: string) => void;
  clearQueue: () => void;
  removeSongFromQueue: (songId: string) => void;
  reorderPlaylist: (oldIndex: number, newIndex: number) => void;
  toast: ({...props}: any) => void;
}

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

function PlayerInitializer() {
    const context = useContext(PlayerContext);
    if (!context) return null; // Should not happen if used correctly

    const { playTrack, setPlayerMode, isMounted, toast, setInitialLoadIsVideoShare } = context;
    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();

    useEffect(() => {
        if (!isMounted) return;

        const shareId = searchParams.get('share_id');
        const shareMode = searchParams.get('mode');

        if (shareId) {
            const handleSharedSong = async () => {
                toast({ title: 'Loading shared song...' });
                try {
                    const song = await getSongByVideoId(shareId);
                    if (song) {
                        playTrack(song);
                        if (shareMode === 'video') {
                            setPlayerMode('video');
                            setInitialLoadIsVideoShare(true);
                        }
                    } else {
                        toast({ variant: 'destructive', title: 'Song not found', description: 'The shared song could not be loaded.' });
                    }
                } catch (error: any) {
                    toast({ variant: 'destructive', title: 'Error loading song', description: error.message });
                } finally {
                    // Clean up URL to prevent re-triggering.
                    const newParams = new URLSearchParams(searchParams.toString());
                    newParams.delete('share_id');
                    newParams.delete('mode');
                    router.replace(`${pathname}?${newParams.toString()}`, { scroll: false });
                }
            };
            handleSharedSong();
        }
    // Only re-run if searchParams changes. Other deps are stable or setters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams, isMounted]);

    return null; // This component just runs logic, it doesn't render anything
}


export function PlayerProvider({ children }: { children: ReactNode }) {
  const [currentTrack, setCurrentTrack] = useState<Song | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playlist, setPlaylist] = useState<Song[]>([]);
  const [history, setHistory] = useState<Song[]>([]);
  const [playerMode, setPlayerMode] = useState<'audio' | 'video'>('audio');
  const [isMounted, setIsMounted] = useState(false);
  const [initialLoadIsVideoShare, setInitialLoadIsVideoShare] = useState(false);
  const { toast } = useToast();

  const isSong = (value: unknown): value is Song => {
    if (!value || typeof value !== 'object') return false;
    const candidate = value as Song;
    return typeof candidate.id === 'string' && typeof candidate.title === 'string';
  };

  const isSongArray = (value: unknown): value is Song[] =>
    Array.isArray(value) && value.every(isSong);

  useEffect(() => {
    setIsMounted(true);
    try {
      const nextTrack = safeReadVersionedStorage<Song | null>(
        'last-played',
        null,
        (value): value is Song | null => value === null || isSong(value)
      );
      const nextPlaylist = safeReadVersionedStorage<Song[]>(
        'last-playlist',
        [],
        isSongArray
      );
      const nextHistory = safeReadVersionedStorage<Song[]>(
        'history',
        [],
        isSongArray
      );

      setCurrentTrack(nextTrack);
      setPlaylist(nextPlaylist);
      setHistory(nextHistory);
    } catch (error) {
      console.error('Failed to load player state from localStorage', error);
      clearPlayerPersistence(['last-played', 'last-playlist', 'history']);
    }
  }, []);

  useEffect(() => {
    if (!isMounted) return;
    try {
      if (currentTrack) {
        writeVersionedStorage('last-played', currentTrack);
      } else {
        removeVersionedStorage('last-played');
      }
      writeVersionedStorage('last-playlist', playlist);
      writeVersionedStorage('history', history);
    } catch (error) {
      console.error('Failed to save player state to localStorage', error);
    }
  }, [currentTrack, playlist, history, isMounted]);

  const isAndroidNativeRuntime = () => {
    if (typeof window === 'undefined') return false;
    return typeof (window as any).HarmonyNative !== 'undefined';
  };

  const sendQueueToNative = (queue: Song[]) => {
    if (!isAndroidNativeRuntime()) return;
    const payload = queue.map((song) => ({
      id: song.id,
      title: song.title,
      artist: song.artist,
      videoId: song.videoId,
      thumbnailUrl: song.thumbnailUrl,
    }));
    (window as any).HarmonyNative?.setQueue?.(JSON.stringify(payload));
  };

  const setNewCurrentTrack = useCallback((track: Song | null) => {
    if (track) {
      // Add to history, ensuring no duplicates and maintaining a max size
      setHistory(prev => [track, ...prev.filter(t => t.id !== track.id)].slice(0, 200));
    }
    setCurrentTrack(track);
  }, []);

  const playTrack = useCallback((track: Song) => {
    setPlaylist([track]);
    setNewCurrentTrack(track);
    setIsPlaying(true);

    if (isAndroidNativeRuntime()) {
      sendQueueToNative([track]);
      (window as any).HarmonyNative?.setIndex?.(0);
      (window as any).HarmonyNative?.play?.();
    }
  }, [setNewCurrentTrack]);
  
  const playPlaylist = useCallback((newPlaylist: Song[], startingTrackId?: string) => {
    if (newPlaylist.length === 0) return;
    
    let playlistToPlay = [...newPlaylist];
    let trackToStart: Song | undefined;

    if (startingTrackId) {
        // A specific song was clicked. Don't shuffle.
        trackToStart = playlistToPlay.find(t => t.id === startingTrackId);
    } else {
        // "Play All" was clicked. Shuffle the playlist.
        for (let i = playlistToPlay.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [playlistToPlay[i], playlistToPlay[j]] = [playlistToPlay[j], playlistToPlay[i]];
        }
        trackToStart = playlistToPlay[0];
    }

    // Fallback if song not found (shouldn't happen with valid IDs)
    if (!trackToStart) {
        trackToStart = playlistToPlay[0];
    }
    
    setPlaylist(playlistToPlay);
    
    if (trackToStart) {
      const selectedIndex = playlistToPlay.findIndex((song) => song.id === trackToStart?.id);
      if (currentTrack?.id === trackToStart.id) {
         // If it's the same track, just ensure it's playing.
         // This handles un-pausing.
         setIsPlaying(true);
      } else {
        // This is a new track.
        setNewCurrentTrack(trackToStart);
        setIsPlaying(true);
      }

      if (isAndroidNativeRuntime()) {
        sendQueueToNative(playlistToPlay);
        (window as any).HarmonyNative?.setIndex?.(Math.max(0, selectedIndex));
        (window as any).HarmonyNative?.play?.();
      }
    } else {
        // This case is unlikely if newPlaylist has items.
      setCurrentTrack(null);
      setIsPlaying(false);
    }
  }, [currentTrack, setNewCurrentTrack]);

  const togglePlayPause = useCallback(() => {
    if (currentTrack) {
      setIsPlaying((prev) => !prev);
    }
  }, [currentTrack]);

  const playNext = useCallback(() => {
    if (!currentTrack || playlist.length === 0) return;

    const currentIndex = playlist.findIndex(t => t.id === currentTrack.id);
    // If not found or only one song, do nothing (or loop)
    if (currentIndex === -1 || playlist.length === 1) {
      // Re-play the same song if it's the only one
      if(playlist.length === 1) {
        setNewCurrentTrack(playlist[0]);
        setIsPlaying(true);
      }
      return;
    }
    const nextIndex = (currentIndex + 1) % playlist.length;
    const nextTrack = playlist[nextIndex];
    
    if (nextTrack) {
      setNewCurrentTrack(nextTrack);
      setIsPlaying(true);
    }
  }, [currentTrack, playlist, setNewCurrentTrack]);
  
  const playPrev = useCallback(() => {
    if (!currentTrack || playlist.length === 0) return;

    const currentIndex = playlist.findIndex(t => t.id === currentTrack.id);
    // If not found or only one song, do nothing
    if (currentIndex === -1 || playlist.length === 1) {
      // Re-play the same song
      if(playlist.length === 1) {
        setNewCurrentTrack(playlist[0]);
        setIsPlaying(true);
      }
      return;
    }
    const prevIndex = (currentIndex - 1 + playlist.length) % playlist.length;
    const prevTrack = playlist[prevIndex];
    
    if (prevTrack) {
      setNewCurrentTrack(prevTrack);
      setIsPlaying(true);
    }
  }, [currentTrack, playlist, setNewCurrentTrack]);

  const shufflePlaylist = useCallback(() => {
    if (playlist.length <= 1) return;

    if (!currentTrack) {
      const shuffled = [...playlist].sort(() => Math.random() - 0.5);
      playPlaylist(shuffled, shuffled[0]?.id);
      return;
    }
    
    const otherSongs = playlist.filter(s => s.id !== currentTrack.id);
    const shuffledOthers = otherSongs.sort(() => Math.random() - 0.5);
    const newPlaylist = [currentTrack, ...shuffledOthers];

    playPlaylist(newPlaylist, currentTrack.id);
  }, [currentTrack, playlist, playPlaylist]);
  
  const clearQueue = useCallback(() => {
    if (currentTrack) {
        setPlaylist([currentTrack]);
    } else {
        setPlaylist([]);
    }
    toast({ title: 'Queue cleared', description: 'Upcoming songs have been removed.' });
  }, [currentTrack, toast]);
  
  const removeSongFromQueue = useCallback((songId: string) => {
    setPlaylist(prevPlaylist => {
        const songIndex = prevPlaylist.findIndex(s => s.id === songId);
        if (songIndex === -1) {
            return prevPlaylist; // Song not in queue, do nothing
        }

        const newPlaylist = prevPlaylist.filter(s => s.id !== songId);

        // If the removed song was the currently playing one
        if (currentTrack?.id === songId) {
            if (newPlaylist.length === 0) {
                // We removed the last song
                setCurrentTrack(null);
                setIsPlaying(false);
            } else {
                // Play the next song relative to the old position
                const nextIndex = songIndex % newPlaylist.length;
                setNewCurrentTrack(newPlaylist[nextIndex]);
                setIsPlaying(true); // Ensure playback continues
            }
        }
        
        toast({ title: "Song removed from queue." });
        return newPlaylist;
    });
  }, [currentTrack?.id, setNewCurrentTrack, toast]);

  const handleTrackError = useCallback((songId: string) => {
    setPlaylist(currentPlaylist => {
        const trackIndex = currentPlaylist.findIndex(s => s.id === songId);
        if (trackIndex === -1) return currentPlaylist;

        const newPlaylist = currentPlaylist.filter(s => s.id !== songId);
        
        setHistory(prev => prev.filter(s => s.id !== songId));

        if (newPlaylist.length === 0) {
            setCurrentTrack(null);
            setIsPlaying(false);
        } else {
            if (currentTrack?.id === songId) {
                const nextIndex = trackIndex % newPlaylist.length;
                const nextTrack = newPlaylist[nextIndex];
                if (nextTrack) {
                    setNewCurrentTrack(nextTrack);
                    setIsPlaying(true);
                } else {
                    setCurrentTrack(null);
                    setIsPlaying(false);
                }
            }
        }
        return newPlaylist;
    });
}, [currentTrack, setNewCurrentTrack]);

  const syncNativeIndex = useCallback((index: number) => {
    if (index < 0 || index >= playlist.length) return;
    const nextTrack = playlist[index];
    if (!nextTrack) return;
    if (currentTrack?.id !== nextTrack.id) {
      setNewCurrentTrack(nextTrack);
    }
  }, [playlist, currentTrack, setNewCurrentTrack]);

  const reorderPlaylist = useCallback((oldIndex: number, newIndex: number) => {
    setPlaylist(playlist => {
      if (oldIndex < 0 || oldIndex >= playlist.length || newIndex < 0 || newIndex >= playlist.length) {
        return playlist;
      }
      const newPlaylist = Array.from(playlist);
      const [movedItem] = newPlaylist.splice(oldIndex, 1);
      newPlaylist.splice(newIndex, 0, movedItem);
      return newPlaylist;
    });
  }, []);

  const value = {
    currentTrack,
    isPlaying,
    playlist,
    history,
    playerMode,
    isMounted,
    initialLoadIsVideoShare,
    setInitialLoadIsVideoShare,
    playTrack,
    playPlaylist,
    togglePlayPause,
    playNext,
    playPrev,
    setIsPlaying,
    setPlayerMode,
    syncNativeIndex,
    isPlayerVisible: isMounted && !!currentTrack,
    shufflePlaylist,
    handleTrackError,
    clearQueue,
    removeSongFromQueue,
    reorderPlaylist,
    toast,
  };

  return (
    <PlayerContext.Provider value={value}>
      {children}
      <Suspense fallback={null}>
        <PlayerInitializer />
      </Suspense>
    </PlayerContext.Provider>
  );
}

export const usePlayer = (): PlayerContextType => {
  const context = useContext(PlayerContext);
  if (context === undefined) {
    throw new Error('usePlayer must be used within a PlayerProvider');
  }
  return context;
};

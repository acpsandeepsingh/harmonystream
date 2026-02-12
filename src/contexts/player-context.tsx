'use client';

import type { Song } from '@/lib/types';
import { createContext, useContext, useState, type ReactNode, useCallback, useEffect } from 'react';
import { useToast } from '@/hooks/use-toast';

interface PlayerContextType {
  currentTrack: Song | null;
  isPlaying: boolean;
  playlist: Song[];
  history: Song[];
  playTrack: (track: Song) => void;
  playPlaylist: (playlist: Song[], startingTrackId?: string) => void;
  togglePlayPause: () => void;
  playNext: () => void;
  playPrev: () => void;
  setIsPlaying: (isPlaying: boolean) => void;
  isPlayerVisible: boolean;
  shufflePlaylist: () => void;
  handleTrackError: (songId: string) => void;
  clearQueue: () => void;
  removeSongFromQueue: (songId: string) => void;
  reorderPlaylist: (oldIndex: number, newIndex: number) => void;
}

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [currentTrack, setCurrentTrack] = useState<Song | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playlist, setPlaylist] = useState<Song[]>([]);
  const [history, setHistory] = useState<Song[]>([]);
  const [isMounted, setIsMounted] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    setIsMounted(true);
    try {
      const lastPlayedTrack = localStorage.getItem('harmony-last-played');
      const lastPlaylist = localStorage.getItem('harmony-last-playlist');
      const storedHistory = localStorage.getItem('harmony-history');
      
      if (lastPlayedTrack) setCurrentTrack(JSON.parse(lastPlayedTrack));
      if (lastPlaylist) setPlaylist(JSON.parse(lastPlaylist));
      if (storedHistory) setHistory(JSON.parse(storedHistory));
    } catch (error) {
      console.error('Failed to load player state from localStorage', error);
    }
  }, []);

  useEffect(() => {
    if (!isMounted) return;
    try {
      if (currentTrack) {
        localStorage.setItem('harmony-last-played', JSON.stringify(currentTrack));
      } else {
        localStorage.removeItem('harmony-last-played');
      }
      localStorage.setItem('harmony-last-playlist', JSON.stringify(playlist));
      localStorage.setItem('harmony-history', JSON.stringify(history));
    } catch (error) {
      console.error('Failed to save player state to localStorage', error);
    }
  }, [currentTrack, playlist, history, isMounted]);

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
      if (currentTrack?.id === trackToStart.id) {
         // If it's the same track, just ensure it's playing.
         // This handles un-pausing.
         setIsPlaying(true);
      } else {
        // This is a new track.
        setNewCurrentTrack(trackToStart);
        setIsPlaying(true);
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
    playTrack,
    playPlaylist,
    togglePlayPause,
    playNext,
    playPrev,
    setIsPlaying,
    isPlayerVisible: isMounted && !!currentTrack,
    shufflePlaylist,
    handleTrackError,
    clearQueue,
    removeSongFromQueue,
    reorderPlaylist,
  };

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

export const usePlayer = (): PlayerContextType => {
  const context = useContext(PlayerContext);
  if (context === undefined) {
    throw new Error('usePlayer must be used within a PlayerProvider');
  }
  return context;
};

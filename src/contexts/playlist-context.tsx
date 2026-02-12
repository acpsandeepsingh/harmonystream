'use client';

import type { Playlist, Song } from '@/lib/types';
import { createContext, useContext, useState, useEffect, type ReactNode, useMemo } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useUser, useFirestore, useMemoFirebase } from '@/firebase';
import { useCollection } from '@/firebase/firestore/use-collection';
import {
  collection,
  doc,
  addDoc,
  setDoc,
  deleteDoc,
  updateDoc,
  arrayUnion,
  arrayRemove,
} from 'firebase/firestore';
import { LIKED_SONGS_PLAYLIST_ID } from '@/lib/constants';

interface PlaylistContextType {
  playlists: Playlist[];
  createPlaylist: (name: string, description: string) => void;
  addSongToPlaylist: (playlistId: string, song: Song) => void;
  addSongsToPlaylist: (playlistId: string, songs: Song[]) => void;
  removeSongFromPlaylist: (playlistId: string, songId: string) => void;
  deletePlaylist: (playlistId: string) => void;
  toggleLikeSong: (song: Song) => void;
  isSongLiked: (songId: string) => boolean;
  removeSongFromDatabase: (songId: string, songTitle: string) => void;
}

const PlaylistContext = createContext<PlaylistContextType | undefined>(undefined);

export function PlaylistProvider({ children }: { children: ReactNode }) {
  const { user } = useUser();
  const firestore = useFirestore();
  const { toast } = useToast();
  
  const [localPlaylists, setLocalPlaylists] = useState<Playlist[]>([]);
  
  // --- Firestore Data ---
  const playlistsCollectionRef = useMemoFirebase(() => 
    user ? collection(firestore, 'users', user.uid, 'playlists') : null
  , [user, firestore]);
  
  const { data: firestorePlaylists, isLoading: firestoreLoading } = useCollection<Playlist>(playlistsCollectionRef);

  // --- Local Storage Data ---
  useEffect(() => {
    if (!user) {
      try {
        const storedPlaylists = localStorage.getItem('harmony-playlists');
        const parsedPlaylists = storedPlaylists ? JSON.parse(storedPlaylists) : [];
        if (!parsedPlaylists.some((p: Playlist) => p.id === LIKED_SONGS_PLAYLIST_ID)) {
          parsedPlaylists.unshift({ id: LIKED_SONGS_PLAYLIST_ID, name: 'Liked Songs', description: 'Your favorite tracks.', songs: [] });
        }
        setLocalPlaylists(parsedPlaylists);
      } catch (error) {
        console.error('Failed to load playlists from localStorage', error);
        setLocalPlaylists([{ id: LIKED_SONGS_PLAYLIST_ID, name: 'Liked Songs', description: 'Your favorite tracks.', songs: [] }]);
      }
    }
  }, [user]);

  useEffect(() => {
    if (!user) {
      try {
        localStorage.setItem('harmony-playlists', JSON.stringify(localPlaylists));
      } catch (error) {
        console.error('Failed to save playlists to localStorage', error);
      }
    }
  }, [localPlaylists, user]);

  const playlists = useMemo(() => {
    if (user) {
      const allPlaylists = firestorePlaylists ? [...firestorePlaylists] : [];
       if (!firestoreLoading && !allPlaylists.some(p => p.id === LIKED_SONGS_PLAYLIST_ID)) {
         allPlaylists.unshift({ id: LIKED_SONGS_PLAYLIST_ID, name: 'Liked Songs', description: 'Your favorite tracks.', songs: [] });
      }
      return allPlaylists;
    }
    return localPlaylists;
  }, [user, firestorePlaylists, firestoreLoading, localPlaylists]);


  // --- CRUD Operations ---

  const createPlaylist = async (name: string, description: string) => {
    if (user && firestore) {
      try {
        await addDoc(collection(firestore, 'users', user.uid, 'playlists'), {
          name,
          description,
          songs: [],
          userId: user.uid,
        });
        toast({ title: 'Playlist created!', description: `"${name}" has been created.` });
      } catch (error) {
        console.error('Error creating playlist:', error);
        toast({ variant: 'destructive', title: 'Error', description: 'Could not create playlist.' });
      }
    } else {
      const newPlaylist: Playlist = {
        id: `playlist-${Date.now()}`,
        name,
        description,
        songs: [],
      };
      setLocalPlaylists((prev) => [...prev, newPlaylist]);
      toast({ title: 'Playlist created!', description: `"${name}" has been created.` });
    }
  };

  const addSongToPlaylist = async (playlistId: string, song: Song) => {
    const targetPlaylist = playlists.find((p) => p.id === playlistId);
    if (!targetPlaylist) return;

    if (targetPlaylist.songs.some((s) => s.id === song.id)) {
      toast({ variant: 'destructive', title: 'Already in playlist', description: `"${song.title}" is already in "${targetPlaylist.name}".` });
      return;
    }
    
    if (user && firestore) {
      const docRef = doc(firestore, 'users', user.uid, 'playlists', playlistId);
      try {
        await updateDoc(docRef, { songs: arrayUnion(song) });
        toast({ title: 'Song added', description: `"${song.title}" added to "${targetPlaylist.name}".` });
      } catch (error) {
         // This can happen if the playlist doc doesn't exist yet (e.g. Liked Songs)
         await setDoc(docRef, { ...targetPlaylist, songs: [song], userId: user.uid }, { merge: true });
         toast({ title: 'Song added', description: `"${song.title}" added to "${targetPlaylist.name}".` });
      }
    } else {
      setLocalPlaylists((prev) =>
        prev.map((p) => (p.id === playlistId ? { ...p, songs: [...p.songs, song] } : p))
      );
      toast({ title: 'Song added', description: `"${song.title}" added to "${targetPlaylist.name}".` });
    }
  };

  const addSongsToPlaylist = async (playlistId: string, songs: Song[]) => {
    const targetPlaylist = playlists.find((p) => p.id === playlistId);
    if (!targetPlaylist || songs.length === 0) return;

    const songsToAdd = songs.filter(
      (song) => !targetPlaylist.songs.some((s) => s.id === song.id)
    );

    if (songsToAdd.length === 0) {
      toast({
        title: 'Songs already in playlist',
        description: `All ${songs.length} songs are already in "${targetPlaylist.name}".`,
      });
      return;
    }

    if (user && firestore) {
      const docRef = doc(firestore, 'users', user.uid, 'playlists', playlistId);
      try {
        await updateDoc(docRef, { songs: arrayUnion(...songsToAdd) });
        toast({
          title: 'Songs added',
          description: `${songsToAdd.length} new song(s) added to "${targetPlaylist.name}".`,
        });
      } catch (error) {
        await setDoc(docRef, { ...targetPlaylist, songs: [...targetPlaylist.songs, ...songsToAdd], userId: user.uid }, { merge: true });
        toast({
          title: 'Songs added',
          description: `${songsToAdd.length} new song(s) added to "${targetPlaylist.name}".`,
        });
      }
    } else {
      setLocalPlaylists((prev) =>
        prev.map((p) =>
          p.id === playlistId
            ? { ...p, songs: [...p.songs, ...songsToAdd] }
            : p
        )
      );
      toast({
        title: 'Songs added',
        description: `${songsToAdd.length} new song(s) added to "${targetPlaylist.name}".`,
      });
    }
  };

  const removeSongFromPlaylist = async (playlistId: string, songId: string) => {
    if (user && firestore) {
        const songToRemove = playlists.find(p => p.id === playlistId)?.songs.find(s => s.id === songId);
        if (!songToRemove) return;
        const docRef = doc(firestore, 'users', user.uid, 'playlists', playlistId);
        try {
            await updateDoc(docRef, { songs: arrayRemove(songToRemove) });
        } catch(error) {
            console.error("Error removing song: ", error);
            toast({ variant: 'destructive', title: 'Error', description: 'Could not remove song.' });
        }
    } else {
        setLocalPlaylists((prev) =>
            prev.map((p) =>
            p.id === playlistId ? { ...p, songs: p.songs.filter((s) => s.id !== songId) } : p
            )
        );
    }
  };

  const deletePlaylist = async (playlistId: string) => {
    if (playlistId === LIKED_SONGS_PLAYLIST_ID) {
      toast({ variant: 'destructive', title: 'Action not allowed', description: 'You cannot delete the "Liked Songs" playlist.' });
      return;
    }
    const playlistName = playlists.find(p => p.id === playlistId)?.name || '';

    if (user && firestore) {
        const docRef = doc(firestore, 'users', user.uid, 'playlists', playlistId);
        try {
            await deleteDoc(docRef);
            toast({ title: 'Playlist deleted', description: `"${playlistName}" has been deleted.` });
        } catch (error) {
            console.error("Error deleting playlist: ", error);
            toast({ variant: 'destructive', title: 'Error', description: 'Could not delete playlist.' });
        }
    } else {
        setLocalPlaylists(prev => prev.filter(p => p.id !== playlistId));
        toast({ title: 'Playlist deleted', description: `"${playlistName}" has been deleted.` });
    }
  };

  const removeSongFromDatabase = async (songId: string, songTitle: string) => {
    // If the user is a registered user (not anonymous/guest) and firestore is available
    if (user && !user.isAnonymous && firestore) {
      // 1. Remove from the global 'songs' collection
      try {
        const songRef = doc(firestore, 'songs', songId);
        await deleteDoc(songRef);
        console.log(`Removed song ${songId} from global song collection.`);
      } catch (error) {
        console.error('Error removing song from global song collection:', error);
        // We can continue to remove from playlists even if this fails (e.g. permissions).
      }

      // 2. Remove from all of the user's personal playlists
      const playlistsToUpdate = (firestorePlaylists || []).filter(p => p.songs.some(s => s.id === songId));
      for (const playlist of playlistsToUpdate) {
        const songToRemove = playlist.songs.find(s => s.id === songId);
        if (songToRemove) {
          try {
            const playlistRef = doc(firestore, 'users', user.uid, 'playlists', playlist.id);
            await updateDoc(playlistRef, { songs: arrayRemove(songToRemove) });
          } catch (error) {
            console.error(`Error removing song from playlist ${playlist.id}:`, error);
          }
        }
      }
      toast({
        title: 'Unavailable Song Cleaned Up',
        description: `"${songTitle}" has been removed.`,
      });
    } else {
      // Handle guest or signed-out user: remove from local playlists only
      setLocalPlaylists(prev =>
        prev.map(p => ({
          ...p,
          songs: p.songs.filter(s => s.id !== songId),
        }))
      );
      toast({
        title: 'Unavailable Song Removed',
        description: `"${songTitle}" was removed from your local playlists.`,
      });
    }
  };

  const isSongLiked = (songId: string): boolean => {
    const likedPlaylist = playlists.find(p => p.id === LIKED_SONGS_PLAYLIST_ID);
    return !!likedPlaylist?.songs.some(s => s.id === songId);
  };

  const toggleLikeSong = (song: Song) => {
    const currentlyLiked = isSongLiked(song.id);
    if (currentlyLiked) {
      removeSongFromPlaylist(LIKED_SONGS_PLAYLIST_ID, song.id);
      toast({ title: 'Removed from Liked Songs' });
    } else {
      addSongToPlaylist(LIKED_SONGS_PLAYLIST_ID, song);
      toast({ title: 'Added to Liked Songs' });
    }
  };

  const value = {
    playlists,
    createPlaylist,
    addSongToPlaylist,
    addSongsToPlaylist,
    removeSongFromPlaylist,
    deletePlaylist,
    toggleLikeSong,
    isSongLiked,
    removeSongFromDatabase,
  };

  return <PlaylistContext.Provider value={value}>{children}</PlaylistContext.Provider>;
}

export const usePlaylists = (): PlaylistContextType => {
  const context = useContext(PlaylistContext);
  if (context === undefined) {
    throw new Error('usePlaylists must be used within a PlaylistProvider');
  }
  return context;
};

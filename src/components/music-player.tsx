'use client';

import React, { useEffect, useMemo } from 'react';
import { usePlayer } from '@/contexts/player-context';
import { usePlaylists } from '@/contexts/playlist-context';
import { WebMusicPlayer } from './music-player-web';
import { LIKED_SONGS_PLAYLIST_ID } from '@/lib/constants';

const detectAndroidRuntime = () => {
  if (typeof window === 'undefined') return false;
  return typeof (window as any).NativePlayer !== 'undefined';
};

function AndroidBridgePlayer() {
  const {
    currentTrack,
    playlist,
    playerMode,
    setPlayerMode,
    setIsPlaying,
    syncNativeIndex,
  } = usePlayer();
  const { playlists, addSongToPlaylist, toggleLikeSong } = usePlaylists();

  useEffect(() => {
    const parsePayload = (raw: unknown) => {
      if (typeof raw !== 'string') return null;
      try {
        return JSON.parse(raw);
      } catch {
        return null;
      }
    };

    const handleNativeMessage = (event: MessageEvent) => {
      const detail = parsePayload(event.data);
      if (!detail || typeof detail.action !== 'string') return;

      const queueIndex = Number(detail.queue_index);
      if (Number.isFinite(queueIndex) && queueIndex >= 0) {
        syncNativeIndex(queueIndex);
      }

      if (detail.action === 'playbackStarted') {
        setIsPlaying(true);
      }
      if (detail.action === 'playbackPaused') {
        setIsPlaying(false);
      }
      if (detail.action === 'likeUpdated' && currentTrack) {
        const liked = Boolean(detail.liked);
        const alreadyLiked = playlists
          .find((playlistEntry) => playlistEntry.id === LIKED_SONGS_PLAYLIST_ID)
          ?.songs.some((song) => song.id === currentTrack.id) ?? false;
        if (liked !== alreadyLiked) {
          toggleLikeSong(currentTrack);
        }
      }
      if (detail.action === 'addToPlaylist' && currentTrack && playlists.length > 0) {
        const playlistId = typeof detail.playlistId === 'string' ? detail.playlistId : playlists[0].id;
        addSongToPlaylist(playlistId, currentTrack);
      }
    };

    window.addEventListener('message', handleNativeMessage);
    return () => window.removeEventListener('message', handleNativeMessage);
  }, [addSongToPlaylist, currentTrack, playlists, setIsPlaying, syncNativeIndex, toggleLikeSong]);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent).detail ?? {};

      if (typeof detail.playing === 'boolean') setIsPlaying(detail.playing);
      if (typeof detail.queue_index === 'number' && detail.queue_index >= 0) {
        syncNativeIndex(detail.queue_index);
      }
      if (typeof detail.video_mode === 'boolean') {
        setPlayerMode(detail.video_mode ? 'video' : 'audio');
      }
    };

    window.addEventListener('nativePlaybackState', handler);
    return () => window.removeEventListener('nativePlaybackState', handler);
  }, [setIsPlaying, setPlayerMode, syncNativeIndex]);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent).detail;
      const enabled = typeof detail?.enabled === 'boolean' ? detail.enabled : detail === true;
      setPlayerMode(enabled ? 'video' : 'audio');
    };

    window.addEventListener('nativeSetVideoMode', handler);
    return () => window.removeEventListener('nativeSetVideoMode', handler);
  }, [setPlayerMode]);

  useEffect(() => {
    const isVideo = playerMode === 'video';
    (window as any).HarmonyNative?.setVideoMode?.(isVideo);
    (window as any).AndroidNative?.setVideoMode?.(isVideo);
  }, [playerMode]);

  useEffect(() => {
    if (!currentTrack || playlist.length === 0) return;
    const mediaType = playerMode === 'video' ? 'video' : 'audio';
    const payload = playlist.map((track) => ({
      id: track.id,
      title: track.title,
      artist: track.artist,
      videoId: track.videoId,
      thumbnailUrl: track.thumbnailUrl,
    }));
    const activeIndex = playlist.findIndex((track) => track.id === currentTrack.id);
    (window as any).NativePlayer?.postMessage?.(JSON.stringify({ action: 'setQueue', tracks: payload }));
    (window as any).NativePlayer?.postMessage?.(JSON.stringify({ action: 'setIndex', index: Math.max(0, activeIndex) }));
    (window as any).NativePlayer?.postMessage?.(JSON.stringify({ action: 'play', track: payload[Math.max(0, activeIndex)] }));
    (window as any).HarmonyNative?.loadMedia?.(currentTrack.videoId, mediaType, currentTrack.title, currentTrack.artist, currentTrack.thumbnailUrl);
    (window as any).AndroidNative?.loadMedia?.(currentTrack.videoId, mediaType, currentTrack.title, currentTrack.artist, currentTrack.thumbnailUrl);
  }, [currentTrack, playerMode, playlist]);

  return null;
}

export function MusicPlayer() {
  const isAndroidAppRuntime = useMemo(detectAndroidRuntime, []);
  if (!isAndroidAppRuntime) return <WebMusicPlayer />;
  return <AndroidBridgePlayer />;
}

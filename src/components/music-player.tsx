'use client';

import React, { useEffect, useMemo } from 'react';
import { usePlayer } from '@/contexts/player-context';
import { WebMusicPlayer } from './music-player-web';


const detectAndroidRuntime = () => {
  if (typeof window === 'undefined') return false;
  const ua = navigator.userAgent ?? '';
  return /Android/i.test(ua)
    && (typeof window.HarmonyNative !== 'undefined'
      || window.location.protocol === 'file:'
      || window.location.hostname === 'appassets.androidplatform.net');
};

function AndroidBridgePlayer() {
  const {
    currentTrack,
    playerMode,
    setPlayerMode,
    setIsPlaying,
    syncNativeIndex,
  } = usePlayer();

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
    window.HarmonyNative?.setVideoMode?.(isVideo);
    window.AndroidNative?.setVideoMode?.(isVideo);
  }, [playerMode]);

  useEffect(() => {
    if (!currentTrack) return;
    const mediaType = playerMode === 'video' ? 'video' : 'audio';
    window.HarmonyNative?.loadMedia?.(
      currentTrack.videoId,
      mediaType,
      currentTrack.title,
      currentTrack.artist,
      currentTrack.thumbnailUrl,
    );
    window.AndroidNative?.loadMedia?.(
      currentTrack.videoId,
      mediaType,
      currentTrack.title,
      currentTrack.artist,
      currentTrack.thumbnailUrl,
    );
  }, [currentTrack, playerMode]);

  return null;
}

export function MusicPlayer() {
  const isAndroidAppRuntime = useMemo(detectAndroidRuntime, []);
  if (!isAndroidAppRuntime) return <WebMusicPlayer />;
  return <AndroidBridgePlayer />;
}

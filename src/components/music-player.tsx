'use client';

import React, { useCallback, useEffect, useMemo, useRef } from 'react';
import YouTube from 'react-youtube';
import type { YouTubeEvent, YouTubePlayer, YouTubeProps } from 'react-youtube';
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
    handleTrackError,
  } = usePlayer();

  const playerRef = useRef<YouTubePlayer | null>(null);

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

  const onPlayerReady = useCallback((event: YouTubeEvent) => {
    playerRef.current = event.target;
    event.target.playVideo();
  }, []);

  const onPlayerStateChange = useCallback((event: YouTubeEvent) => {
    if (event.data === 1) setIsPlaying(true);
    if (event.data === 2 || event.data === 0) setIsPlaying(false);
    if (event.data === 0 && currentTrack) handleTrackError(currentTrack.id);
  }, [currentTrack, handleTrackError, setIsPlaying]);

  const onPlayerError = useCallback(() => {
    if (currentTrack) handleTrackError(currentTrack.id);
  }, [currentTrack, handleTrackError]);

  const youtubeOpts: YouTubeProps['opts'] = useMemo(() => ({
    width: '100%',
    height: '100%',
    playerVars: {
      autoplay: 1,
      controls: 0,
      modestbranding: 1,
      rel: 0,
      iv_load_policy: 3,
      fs: 1,
    },
  }), []);

  if (!currentTrack) return null;

  if (playerMode !== 'video') return null;

  return (
    <div className="fixed inset-0 z-[60] bg-black">
      <YouTube
        key={currentTrack.id}
        videoId={currentTrack.id}
        opts={youtubeOpts}
        onReady={onPlayerReady}
        onStateChange={onPlayerStateChange}
        onError={onPlayerError}
        className="h-full w-full"
        iframeClassName="h-full w-full"
      />
    </div>
  );
}

export function MusicPlayer() {
  const isAndroidAppRuntime = useMemo(detectAndroidRuntime, []);
  if (!isAndroidAppRuntime) return <WebMusicPlayer />;
  return <AndroidBridgePlayer />;
}

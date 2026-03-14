import { useCallback } from 'react';
import type { RefObject } from 'react';
import type { YouTubePlayer } from 'react-youtube';

type UseAudioEngineProps = {
  playerRef: RefObject<YouTubePlayer | null>;
  isAndroidAppRuntime: boolean;
};

export function useAudioEngine({ playerRef, isAndroidAppRuntime }: UseAudioEngineProps) {
  const setVolume = useCallback((volume: number) => {
    if (playerRef.current) {
      try { playerRef.current.setVolume(volume); } catch { /* ignore */ }
    }

    if (isAndroidAppRuntime) {
      window.HarmonyNative?.setVolume?.(volume);
    }
  }, [isAndroidAppRuntime, playerRef]);

  return { setVolume };
}

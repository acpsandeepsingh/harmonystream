'use client';

import React, { Suspense, lazy, useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  Music as MusicIcon,
  Pause,
  Play,
  SkipBack,
  SkipForward,
  Video,
  Volume2,
} from 'lucide-react';

const LazyVolumeControl = lazy(() => import('./volume-control'));

type PlayerControlsProps = {
  isPlaying: boolean;
  playerMode: 'audio' | 'video';
  volume: number;
  mobile?: boolean;
  onTogglePlayerMode: () => void;
  onPlayPrev: () => void;
  onPlayNext: () => void;
  onTogglePlayPause: () => void;
  onVolumeChange: (value: number) => void;
  showVolumeControl?: boolean;
};

function VolumeButton({ volume, onVolumeChange, mobile = false }: {
  volume: number;
  onVolumeChange: (value: number) => void;
  mobile?: boolean;
}) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [isMounted, setIsMounted] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [isOpen]);

  const toggle = () => {
    if (!isMounted) setIsMounted(true);
    setIsOpen((prev) => !prev);
  };

  return (
    <div ref={rootRef} className="relative">
      <Button variant="ghost" size="icon" className={cn(mobile && 'h-8 w-8')} onClick={toggle}>
        <Volume2 className="h-5 w-5 text-muted-foreground" />
        <span className="sr-only">Toggle volume</span>
      </Button>
      {isMounted && (
        <div className={cn('absolute bottom-full right-0 mb-2 z-[80]', mobile && 'right-[-6px]')}>
          <Suspense fallback={null}>
            <LazyVolumeControl
              isOpen={isOpen}
              volume={volume}
              onVolumeChange={onVolumeChange}
            />
          </Suspense>
        </div>
      )}
    </div>
  );
}

export function PlayerControls({
  isPlaying,
  playerMode,
  volume,
  mobile = false,
  onTogglePlayerMode,
  onPlayPrev,
  onPlayNext,
  onTogglePlayPause,
  onVolumeChange,
  showVolumeControl = true,
}: PlayerControlsProps) {
  return (
    <div className="w-full flex items-center justify-between">
      <div className={cn('w-20', !mobile && 'w-auto')}>
        <Button variant="ghost" size="icon" className={cn(mobile && 'h-8 w-8')} onClick={onTogglePlayerMode}>
          {playerMode === 'audio'
            ? <Video className="h-5 w-5" />
            : <MusicIcon className="h-5 w-5" />}
        </Button>
      </div>
      <div className="flex items-center">
        <Button variant="ghost" size="icon" onClick={onPlayPrev}>
          <SkipBack className={cn(mobile ? 'h-6 w-6' : 'h-5 w-5')} />
        </Button>
        <Button
          variant={mobile ? 'ghost' : 'default'}
          size="icon"
          className={cn(mobile ? 'h-14 w-14' : 'h-10 w-10')}
          onClick={onTogglePlayPause}
        >
          {isPlaying
            ? <Pause className={cn(mobile ? 'h-10 w-10' : 'h-5 w-5')} />
            : <Play className={cn(mobile ? 'h-10 w-10 ml-1' : 'h-5 w-5 ml-0.5')} />}
        </Button>
        <Button variant="ghost" size="icon" onClick={onPlayNext}>
          <SkipForward className={cn(mobile ? 'h-6 w-6' : 'h-5 w-5')} />
        </Button>
      </div>
      <div className={cn('w-20 flex justify-end', !mobile && 'w-auto')}>
        {showVolumeControl && (
          <VolumeButton volume={volume} onVolumeChange={onVolumeChange} mobile={mobile} />
        )}
      </div>
    </div>
  );
}

export function VolumeToggleControl({
  volume,
  onVolumeChange,
}: {
  volume: number;
  onVolumeChange: (value: number) => void;
}) {
  return <VolumeButton volume={volume} onVolumeChange={onVolumeChange} />;
}

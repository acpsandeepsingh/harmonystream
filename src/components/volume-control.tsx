'use client';

import React from 'react';
import { cn } from '@/lib/utils';

type VolumeControlProps = {
  isOpen: boolean;
  volume: number;
  onVolumeChange: (value: number) => void;
};

export default function VolumeControl({ isOpen, volume, onVolumeChange }: VolumeControlProps) {
  return (
    <div
      className={cn(
        'rounded-xl border bg-card/95 p-3 shadow-xl backdrop-blur-sm',
        'transition-all duration-200 ease-out will-change-transform',
        isOpen
          ? 'pointer-events-auto opacity-100 translate-y-0 scale-100'
          : 'pointer-events-none opacity-0 translate-y-2 scale-95',
      )}
      aria-hidden={!isOpen}
    >
      <label htmlFor="volume-slider" className="sr-only">Volume</label>
      <input
        id="volume-slider"
        type="range"
        min={0}
        max={100}
        step={1}
        value={volume}
        onChange={(event) => onVolumeChange(Number(event.target.value))}
        className="h-28 w-6 cursor-pointer appearance-none rounded-full bg-transparent [writing-mode:vertical-lr] [direction:rtl]"
      />
    </div>
  );
}

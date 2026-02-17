'use client';

import { useEffect, useState } from 'react';
import PlaylistPageClient from './[id]/playlist-page-client';

export default function PlaylistQueryPageClient() {
  const [playlistId, setPlaylistId] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setPlaylistId(params.get('id') ?? '');
  }, []);

  if (playlistId === null) {
    return (
      <div className="text-center py-16">
        <h1 className="text-2xl font-bold">Loading playlist...</h1>
      </div>
    );
  }

  return <PlaylistPageClient id={playlistId} />;
}

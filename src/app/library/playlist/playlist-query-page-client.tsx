'use client';

import { useSearchParams } from 'next/navigation';
import PlaylistPageClient from './[id]/playlist-page-client';

export default function PlaylistQueryPageClient() {
  const searchParams = useSearchParams();
  const playlistId = searchParams.get('id') ?? '';

  return <PlaylistPageClient id={playlistId} />;
}

'use client';

import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import PlaylistPageClient from './playlist-page-client';

function PlaylistPageContent() {
  const searchParams = useSearchParams();
  const id = searchParams.get('id');

  if (!id) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <h1 className="text-2xl font-bold mb-2">No Playlist Selected</h1>
          <p className="text-muted-foreground">Please select a playlist from your library.</p>
        </div>
      </div>
    );
  }

  return <PlaylistPageClient id={id} />;
}

export default function PlaylistPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    }>
      <PlaylistPageContent />
    </Suspense>
  );
}

import { Suspense } from 'react';
import PlaylistQueryPageClient from './playlist-query-page-client';

export default function PlaylistPage() {
  return (
    <Suspense
      fallback={
        <div className="text-center py-16">
          <h1 className="text-2xl font-bold">Loading playlist...</h1>
        </div>
      }
    >
      <PlaylistQueryPageClient />
    </Suspense>
  );
}

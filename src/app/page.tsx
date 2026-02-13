import { Suspense } from 'react';
import HomePageClient from './home-client';
import { Skeleton } from '@/components/ui/skeleton';

export default function HomePage() {
  return (
    <Suspense fallback={
      <div className="space-y-8">
        <div>
          <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Top Music</h1>
          <p className="text-muted-foreground">Loading...</p>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="aspect-square w-full" />
              <Skeleton className="h-4 w-3/4" />
            </div>
          ))}
        </div>
      </div>
    }>
      <HomePageClient />
    </Suspense>
  );
}

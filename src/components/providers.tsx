'use client';

import type { ReactNode } from 'react';
import { ThemeProvider } from '@/components/theme-provider';
import { PlayerProvider } from '@/contexts/player-context';
import { PlaylistProvider } from '@/contexts/playlist-context';
import { SearchHistoryProvider } from '@/contexts/search-history-context';
import { AppLayout } from '@/components/layout/app-layout';
import { usePathname } from 'next/navigation';
import { TooltipProvider } from '@/components/ui/tooltip';

const AUTH_ROUTES = ['/login', '/signup'];

export function Providers({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isAuthRoute = AUTH_ROUTES.includes(pathname);

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <TooltipProvider>
        <SearchHistoryProvider>
          <PlaylistProvider>
            <PlayerProvider>
              {isAuthRoute ? (
                <main className="bg-background min-h-screen">{children}</main>
              ) : (
                <AppLayout>
                  {children}
                </AppLayout>
              )}
            </PlayerProvider>
          </PlaylistProvider>
        </SearchHistoryProvider>
      </TooltipProvider>
    </ThemeProvider>
  );
}

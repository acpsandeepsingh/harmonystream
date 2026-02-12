'use client';

import { AppSidebar } from '@/components/layout/app-sidebar';
import { Header } from '@/components/layout/header';
import { SidebarProvider } from '@/components/ui/sidebar';
import { MusicPlayer } from '../music-player';
import { usePlayer } from '@/contexts/player-context';
import { cn } from '@/lib/utils';
import { useIsMobile } from '@/hooks/use-mobile';


function AppLayoutContent({ children }: { children: React.ReactNode }) {
  const { isPlayerVisible } = usePlayer();
  const isMobile = useIsMobile();

  return (
    <>
      <div className="flex min-h-screen">
        <AppSidebar />
        <div className="flex-1">
          <Header />
          <main className={cn(
              "p-4 pt-6 md:p-8 md:pt-6",
              // Add padding to bottom to prevent content from being hidden by the player bar
              isPlayerVisible && (isMobile ? "pb-40" : "pb-28")
            )}
          >
            {children}
          </main>
        </div>
      </div>
      
      {isPlayerVisible && (
        <div className="fixed bottom-0 left-0 right-0 z-50">
          <MusicPlayer />
        </div>
      )}
    </>
  );
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SidebarProvider>
      <AppLayoutContent>{children}</AppLayoutContent>
    </SidebarProvider>
  );
}

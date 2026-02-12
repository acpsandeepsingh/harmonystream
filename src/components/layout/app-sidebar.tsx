'use client';

import {
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
} from '@/components/ui/sidebar';
import { Home, Library, Music, PlusCircle, History } from 'lucide-react';
import { Separator } from '@/components/ui/separator';
import { usePlaylists } from '@/contexts/playlist-context';
import { ScrollArea } from '@/components/ui/scroll-area';
import { usePathname } from 'next/navigation';
import { CreatePlaylistDialog } from '@/components/create-playlist-dialog';
import Link from 'next/link';

export function AppSidebar() {
  const { playlists } = usePlaylists();
  const pathname = usePathname();

  return (
    <Sidebar>
      <SidebarHeader className="p-4">
        <Link href="/" className="flex items-center gap-2">
            <Music className="text-primary h-8 w-8" />
            <h1 className="text-2xl font-headline font-bold">HarmonyStream</h1>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton href="/" isActive={pathname === '/'}>
              <Home />
              Home
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton href="/history" isActive={pathname.startsWith('/history')}>
              <History />
              History
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton href="/library" isActive={pathname.startsWith('/library')}>
              <Library />
              Your Library
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <Separator className="my-4" />
        <SidebarGroup>
            <SidebarGroupLabel>Playlists</SidebarGroupLabel>
            <ScrollArea className="h-64">
                <SidebarMenu>
                    {playlists.map((playlist) => (
                    <SidebarMenuItem key={playlist.id}>
                        <SidebarMenuButton href={`/library/playlist/${playlist.id}`} isActive={pathname === `/library/playlist/${playlist.id}`}>
                        {playlist.name}
                        </SidebarMenuButton>
                    </SidebarMenuItem>
                    ))}
                </SidebarMenu>
            </ScrollArea>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <CreatePlaylistDialog>
              <SidebarMenuButton>
                  <PlusCircle />
                  Create Playlist
              </SidebarMenuButton>
            </CreatePlaylistDialog>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}

'use client';

import { usePlaylists } from '@/contexts/playlist-context';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CreatePlaylistDialog } from '@/components/create-playlist-dialog';
import { PlusCircle } from 'lucide-react';
import Link from 'next/link';

export default function LibraryPage() {
  const { playlists } = usePlaylists();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Your Library</h1>
        <p className="text-muted-foreground">All your playlists in one place.</p>
      </div>

      {playlists.length > 0 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {playlists.map((playlist) => (
            <Link key={playlist.id} href={`/library/playlist/${playlist.id}`}>
              <Card className="hover:bg-muted/50 transition-colors h-full cursor-pointer">
                <CardHeader>
                  <CardTitle className="truncate">{playlist.name}</CardTitle>
                  <CardDescription>{playlist.songs.length} songs</CardDescription>
                </CardHeader>
              </Card>
            </Link>
          ))}
        </div>
      ) : (
        <div className="text-center py-16 border-dashed border-2 rounded-lg flex flex-col items-center space-y-4">
          <p className="text-muted-foreground">You haven't created any playlists yet.</p>
          <CreatePlaylistDialog>
             <Button>
                <PlusCircle className="mr-2 h-4 w-4" />
                Create a Playlist
             </Button>
          </CreatePlaylistDialog>
        </div>
      )}
    </div>
  );
}

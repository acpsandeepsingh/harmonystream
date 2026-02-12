'use client';

import { usePlayer } from '@/contexts/player-context';
import { SongCard } from '@/components/song-card';
import { Button } from '@/components/ui/button';
import { Play, Trash, History as HistoryIcon, X } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSearchHistory } from '@/contexts/search-history-context';
import Link from 'next/link';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

function ListeningHistoryTab() {
  const { history, playPlaylist } = usePlayer();

  const handlePlayAll = () => {
    if (history.length > 0) {
      playPlaylist(history);
    }
  };

  return (
    <div>
      {history.length > 0 ? (
        <>
          <Button onClick={handlePlayAll} disabled={history.length === 0}>
            <Play className="mr-2 h-4 w-4" />
            Play All
          </Button>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4 mt-6">
            {history.map((song) => (
              <SongCard
                key={song.id}
                song={song}
                onPlay={() => playPlaylist(history, song.id)}
              />
            ))}
          </div>
        </>
      ) : (
        <div className="text-center py-16 border-dashed border-2 rounded-lg flex flex-col items-center space-y-4">
          <p className="text-muted-foreground">Your listening history is empty.</p>
          <p className="text-sm text-muted-foreground">Start listening to some music to see your history here.</p>
        </div>
      )}
    </div>
  );
}

function SearchHistoryTab() {
  const { searchHistory, removeSearchTerm, clearSearchHistory } = useSearchHistory();

  return (
    <div>
      {searchHistory.length > 0 ? (
        <>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="destructive">
                <Trash className="mr-2 h-4 w-4" /> Clear History
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                <AlertDialogDescription>
                  This will permanently delete your entire search history. This action cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction onClick={clearSearchHistory}>Delete</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
          <div className="flex flex-col gap-2 mt-6">
            {searchHistory.map((term) => (
              <div
                key={term}
                className="flex items-center justify-between gap-4 p-2 rounded-md hover:bg-muted group"
              >
                <Link href={`/search?q=${encodeURIComponent(term)}`} className="flex items-center gap-3 flex-1 min-w-0">
                  <HistoryIcon className="h-4 w-4 text-muted-foreground" />
                  <span className="truncate">{term}</span>
                </Link>
                <Button 
                  variant="ghost" 
                  size="icon" 
                  className="h-8 w-8 opacity-0 group-hover:opacity-100"
                  onClick={() => removeSearchTerm(term)}
                >
                  <X className="h-4 w-4" />
                  <span className="sr-only">Remove from history</span>
                </Button>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="text-center py-16 border-dashed border-2 rounded-lg flex flex-col items-center space-y-4">
          <p className="text-muted-foreground">Your search history is empty.</p>
          <p className="text-sm text-muted-foreground">Your recent searches will appear here.</p>
        </div>
      )}
    </div>
  )
}

export default function HistoryPage() {
  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-4xl font-headline font-bold text-foreground mb-2">History</h1>
        <p className="text-muted-foreground">Your listening and search history.</p>
      </div>

      <Tabs defaultValue="listening" className="w-full">
        <TabsList>
          <TabsTrigger value="listening">Listening History</TabsTrigger>
          <TabsTrigger value="search">Search History</TabsTrigger>
        </TabsList>
        <TabsContent value="listening" className="mt-6">
          <ListeningHistoryTab />
        </TabsContent>
        <TabsContent value="search" className="mt-6">
          <SearchHistoryTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

'use client';

import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { ThemeToggle } from '@/components/theme-toggle';
import { UserNav } from '@/components/user-nav';
import { SidebarTrigger } from '../ui/sidebar';
import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useSearchHistory } from '@/contexts/search-history-context';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export function Header() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const query = searchParams.get('q');
  const source = searchParams.get('source');
  
  const [searchTerm, setSearchTerm] = useState(query || '');
  const [searchSource, setSearchSource] = useState(source === 'ai' ? 'database' : (source || 'database'));
  const { addSearchTerm } = useSearchHistory();

  // Sync input with URL query param
  useEffect(() => {
    const querySource = source || 'database';
    setSearchTerm(query || '');
    // If the source from URL is 'ai', default to 'database' instead.
    setSearchSource(querySource === 'ai' ? 'database' : querySource);
  }, [query, source]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedTerm = searchTerm.trim();
    if (!trimmedTerm) {
      return;
    }
    addSearchTerm(trimmedTerm);
    router.push(`/search?q=${encodeURIComponent(trimmedTerm)}&source=${searchSource}`);
  };

  return (
    <header className="sticky top-0 z-40 flex h-16 items-center gap-4 border-b bg-background/80 px-4 backdrop-blur-sm md:px-6">
      <div className="md:hidden">
        <SidebarTrigger />
      </div>
      <div className="flex-1">
        <form onSubmit={handleSearch} className="flex w-full md:w-[420px] lg:w-[520px]">
            <div className="relative flex-grow">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                type="search"
                placeholder="Search..."
                className="w-full rounded-r-none bg-secondary pl-8"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                />
            </div>
            <Select value={searchSource} onValueChange={setSearchSource}>
                <SelectTrigger className="w-[120px] rounded-l-none border-l-0 bg-secondary">
                    <SelectValue placeholder="Source" />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="database">Database</SelectItem>
                    <SelectItem value="youtube">YouTube</SelectItem>
                </SelectContent>
            </Select>
        </form>
      </div>
      <div className="flex items-center gap-2">
        <ThemeToggle />
        <UserNav />
      </div>
    </header>
  );
}

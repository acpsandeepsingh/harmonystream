'use client';

import { createContext, useContext, useState, type ReactNode, useCallback, useEffect } from 'react';

interface SearchHistoryContextType {
  searchHistory: string[];
  addSearchTerm: (term: string) => void;
  removeSearchTerm: (term: string) => void;
  clearSearchHistory: () => void;
}

const SearchHistoryContext = createContext<SearchHistoryContextType | undefined>(undefined);

const LOCAL_STORAGE_KEY = 'harmony-search-history';

export function SearchHistoryProvider({ children }: { children: ReactNode }) {
  const [searchHistory, setSearchHistory] = useState<string[]>([]);
  const [isMounted, setIsMounted] = useState(false);

  useEffect(() => {
    setIsMounted(true);
    try {
      const storedHistory = localStorage.getItem(LOCAL_STORAGE_KEY);
      if (storedHistory) {
        setSearchHistory(JSON.parse(storedHistory));
      }
    } catch (error) {
      console.error('Failed to load search history from localStorage', error);
    }
  }, []);

  useEffect(() => {
    if (!isMounted) return;
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(searchHistory));
    } catch (error) {
      console.error('Failed to save search history to localStorage', error);
    }
  }, [searchHistory, isMounted]);

  const addSearchTerm = useCallback((term: string) => {
    const cleanedTerm = term.trim();
    if (!cleanedTerm) return;

    setSearchHistory(prev => {
      // Remove existing instance of the term to move it to the front
      const filtered = prev.filter(t => t.toLowerCase() !== cleanedTerm.toLowerCase());
      // Add the new term to the beginning of the array
      const newHistory = [cleanedTerm, ...filtered];
      // Limit history to a reasonable number, e.g., 50
      return newHistory.slice(0, 50);
    });
  }, []);

  const removeSearchTerm = useCallback((term: string) => {
    setSearchHistory(prev => prev.filter(t => t !== term));
  }, []);

  const clearSearchHistory = useCallback(() => {
    setSearchHistory([]);
  }, []);

  const value = {
    searchHistory,
    addSearchTerm,
    removeSearchTerm,
    clearSearchHistory,
  };

  return <SearchHistoryContext.Provider value={value}>{children}</SearchHistoryContext.Provider>;
}

export const useSearchHistory = (): SearchHistoryContextType => {
  const context = useContext(SearchHistoryContext);
  if (context === undefined) {
    throw new Error('useSearchHistory must be used within a SearchHistoryProvider');
  }
  return context;
};

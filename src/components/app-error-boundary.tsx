'use client';

import type { ReactNode } from 'react';
import { Component } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: unknown) {
    console.error('[AppErrorBoundary] React render failure', error);
    try {
      localStorage.removeItem('player_v2:last-played');
      localStorage.removeItem('player_v2:last-playlist');
      localStorage.removeItem('player_v2:history');
      localStorage.removeItem('player_v2:playlists');
      localStorage.removeItem('player_v2:search-history');
    } catch (storageError) {
      console.error('[AppErrorBoundary] Failed to clear persisted state', storageError);
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="min-h-screen bg-background text-foreground p-6 grid place-items-center text-center">
          <div>
            <h1 className="text-xl font-semibold mb-2">Something went wrong</h1>
            <p className="text-sm opacity-80 mb-4">
              Corrupted local state was cleared. Please reload the app.
            </p>
            <button
              className="px-4 py-2 rounded bg-primary text-primary-foreground"
              onClick={() => window.location.reload()}
              type="button"
            >
              Reload
            </button>
          </div>
        </main>
      );
    }

    return this.props.children;
  }
}

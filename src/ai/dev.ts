import { config } from 'dotenv';
config();

import '@/ai/flows/suggest-songs-based-on-history.ts';
import '@/ai/flows/suggest-songs-from-description.ts';
import '@/ai/flows/generate-playlist-description.ts';
import '@/ai/flows/ai-search-query.ts';
import '@/ai/flows/suggest-similar-songs.ts';

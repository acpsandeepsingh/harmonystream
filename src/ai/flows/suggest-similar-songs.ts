'use server';

/**
 * @fileOverview Suggests similar songs based on a given track.
 *
 * - suggestSimilarSongs - A function that suggests similar songs.
 * - SuggestSimilarSongsInput - The input type for suggestSimilarSongs.
 * - SuggestSimilarSongsOutput - The output type for suggestSimilarSongs.
 */

import {ai} from '@/ai/genkit';
import {z} from 'zod';

const SuggestSimilarSongsInputSchema = z.object({
  title: z.string().describe('The title of the song.'),
  artist: z.string().describe('The artist of the song.'),
});
export type SuggestSimilarSongsInput = z.infer<typeof SuggestSimilarSongsInputSchema>;

const SongSuggestionSchema = z.object({
    title: z.string().describe('The title of the suggested song.'),
    artist: z.string().describe('The artist of the suggested song.'),
});

const SuggestSimilarSongsOutputSchema = z.object({
  songs: z.array(SongSuggestionSchema).describe('An array of 10 suggested songs that are similar to the input song.'),
});
export type SuggestSimilarSongsOutput = z.infer<typeof SuggestSimilarSongsOutputSchema>;

export async function suggestSimilarSongs(input: SuggestSimilarSongsInput): Promise<SuggestSimilarSongsOutput> {
    return suggestSimilarSongsFlow(input);
}

const prompt = ai.definePrompt({
    name: 'suggestSimilarSongsPrompt',
    model: 'googleai/gemini-1.5-flash',
    input: {schema: SuggestSimilarSongsInputSchema},
    output: {schema: SuggestSimilarSongsOutputSchema},
    prompt: `You are a music recommendation expert for Indian music. Given a song title and artist, suggest 10 similar songs that the user might enjoy. Focus on providing songs that are likely to be in a music database. Provide the title and artist for each suggestion.

    Original Song:
    Title: {{{title}}}
    Artist: {{{artist}}}

    Suggested Songs:`,
});

const suggestSimilarSongsFlow = ai.defineFlow(
    {
        name: 'suggestSimilarSongsFlow',
        inputSchema: SuggestSimilarSongsInputSchema,
        outputSchema: SuggestSimilarSongsOutputSchema,
    },
    async (input) => {
        // This flow is temporarily disabled to improve stability.
        // In a real scenario, you might re-enable this or have a fallback.
        return { songs: [] };
    }
);

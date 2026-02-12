'use server';
/**
 * @fileOverview This file defines a Genkit flow for suggesting songs based on a user's listening history.
 *
 * The flow takes a user's listening history as input and returns a list of suggested songs.
 *
 * @example
 * // Example usage:
 * const listeningHistory = ['Song A', 'Song B', 'Song C'];
 * const suggestedSongs = await suggestSongsBasedOnHistory(listeningHistory);
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';


const SuggestSongsBasedOnHistoryInputSchema = z.array(z.string()).describe('An array of song titles representing the user\'s listening history.');
export type SuggestSongsBasedOnHistoryInput = z.infer<typeof SuggestSongsBasedOnHistoryInputSchema>;

const SuggestSongsBasedOnHistoryOutputSchema = z.array(z.string()).describe('An array of suggested song titles.');
export type SuggestSongsBasedOnHistoryOutput = z.infer<typeof SuggestSongsBasedOnHistoryOutputSchema>;


export async function suggestSongsBasedOnHistory(listeningHistory: SuggestSongsBasedOnHistoryInput): Promise<SuggestSongsBasedOnHistoryOutput> {
  return suggestSongsBasedOnHistoryFlow(listeningHistory);
}


const suggestSongsBasedOnHistoryPrompt = ai.definePrompt({
  name: 'suggestSongsBasedOnHistoryPrompt',
  model: 'googleai/gemini-1.5-flash',
  input: { schema: SuggestSongsBasedOnHistoryInputSchema },
  output: { schema: SuggestSongsBasedOnHistoryOutputSchema },
  prompt: `You are a music recommendation expert. Given a user's listening history, you will suggest songs that the user might enjoy. Please make sure suggested songs are diverse and interesting. Consider current music trends.

Listening History:
{{#each this}}
- {{this}}
{{/each}}

Suggested Songs:
`,
});

const suggestSongsBasedOnHistoryFlow = ai.defineFlow(
  {
    name: 'suggestSongsBasedOnHistoryFlow',
    inputSchema: SuggestSongsBasedOnHistoryInputSchema,
    outputSchema: SuggestSongsBasedOnHistoryOutputSchema,
  },
  async listeningHistory => {
    const {output} = await suggestSongsBasedOnHistoryPrompt(listeningHistory);
    return output!;
  }
);

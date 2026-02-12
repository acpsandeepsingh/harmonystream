'use server';

/**
 * @fileOverview This file defines a Genkit flow for converting a natural language query into a structured search query for the song database.
 *
 * @exports aiSearchQuery - An async function that takes a natural language query and returns a structured search query.
 * @exports AiSearchQueryInput - The input type for the aiSearchQuery function.
 * @exports AiSearchQueryOutput - The output type for the aiSearchQuery function.
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';

const AiSearchQueryInputSchema = z.object({
  query: z.string().describe('The natural language search query from the user.'),
});
export type AiSearchQueryInput = z.infer<typeof AiSearchQueryInputSchema>;

const AiSearchQueryOutputSchema = z.object({
  keywords: z.array(z.string()).describe('An array of keywords extracted from the query to search the song database. Should be lowercase.'),
});
export type AiSearchQueryOutput = z.infer<typeof AiSearchQueryOutputSchema>;

export async function aiSearchQuery(
  input: AiSearchQueryInput
): Promise<AiSearchQueryOutput> {
  return aiSearchQueryFlow(input);
}

const aiSearchQueryPrompt = ai.definePrompt({
  name: 'aiSearchQueryPrompt',
  model: 'googleai/gemini-1.5-flash',
  input: {schema: AiSearchQueryInputSchema},
  output: {schema: AiSearchQueryOutputSchema},
  prompt: `You are an intelligent music search assistant for Indian music. Your primary task is to convert a user's natural language search query into a list of lowercase keywords for a database search. Many queries will be in English but refer to Hindi or other Indian language words, so you must be excellent at handling phonetic misspellings and transliteration variations.

  Follow these steps carefully:
  1.  **Analyze for Intent and Spelling:** Look at the user's query. Identify the core song titles, artist names, or genres they are looking for.
  2.  **Correct Spelling & Find Variations:** Crucially, correct any spelling mistakes. Also, consider common phonetic variations. For example, if the user types 'dhurander' or 'dhoorandar', you should recognize they likely mean 'dhoorandhar'. 'bolewod' should become 'bollywood'. 'arijit sing' should become 'arijit singh'.
  3.  **Extract Keywords:** From the corrected and expanded understanding of the query, extract all relevant keywords. This includes artist names, song titles, genres, and moods.
  4.  **Format Output:** Return only the extracted keywords as a lowercase array of strings.

  Example 1:
  User Query: "sad bolewod danc songs"
  Resulting Keywords: ["sad", "bollywood", "dance", "songs"]

  Example 2:
  User Query: "new songs by arijit sing"
  Resulting Keywords: ["new", "songs", "arijit", "singh"]
  
  Example 3:
  User Query: "dhurander"
  Resulting Keywords: ["dhoorandhar"]

  User Query: {{{query}}}
  `,
});

const aiSearchQueryFlow = ai.defineFlow(
  {
    name: 'aiSearchQueryFlow',
    inputSchema: AiSearchQueryInputSchema,
    outputSchema: AiSearchQueryOutputSchema,
  },
  async input => {
    const {output} = await aiSearchQueryPrompt(input);
    return output!;
  }
);

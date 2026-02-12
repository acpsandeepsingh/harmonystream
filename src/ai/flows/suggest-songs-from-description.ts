'use server';

/**
 * @fileOverview Provides song suggestions based on a playlist description.
 *
 * - suggestSongsFromDescription -  A function that suggests songs based on a playlist description.
 * - SuggestSongsFromDescriptionInput - The input type for suggestSongsFromDescription.
 * - SuggestSongsFromDescriptionOutput - The output type for suggestSongsFromDescription.
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';

const SuggestSongsFromDescriptionInputSchema = z.object({
  description: z
    .string()
    .describe('A description of the desired playlist, including mood, genre, and artists.'),
});

export type SuggestSongsFromDescriptionInput = z.infer<
  typeof SuggestSongsFromDescriptionInputSchema
>;

const SuggestSongsFromDescriptionOutputSchema = z.object({
  songs: z
    .array(z.string())
    .describe('An array of song titles that fit the description.'),
});

export type SuggestSongsFromDescriptionOutput = z.infer<
  typeof SuggestSongsFromDescriptionOutputSchema
>;

export async function suggestSongsFromDescription(
  input: SuggestSongsFromDescriptionInput
): Promise<SuggestSongsFromDescriptionOutput> {
  return suggestSongsFromDescriptionFlow(input);
}

const prompt = ai.definePrompt({
  name: 'suggestSongsFromDescriptionPrompt',
  model: 'googleai/gemini-1.5-flash',
  input: {schema: SuggestSongsFromDescriptionInputSchema},
  output: {schema: SuggestSongsFromDescriptionOutputSchema},
  prompt: `You are a music expert. Based on the description of the playlist, suggest some songs.

Description: {{{description}}}

Suggest songs:`,
});

const suggestSongsFromDescriptionFlow = ai.defineFlow(
  {
    name: 'suggestSongsFromDescriptionFlow',
    inputSchema: SuggestSongsFromDescriptionInputSchema,
    outputSchema: SuggestSongsFromDescriptionOutputSchema,
  },
  async input => {
    const {output} = await prompt(input);
    return output!;
  }
);

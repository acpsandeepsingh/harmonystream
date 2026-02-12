'use server';

/**
 * @fileOverview This file defines a Genkit flow for generating a playlist description using AI.
 *
 * The flow takes a playlist title and a list of song titles as input, and returns a generated description for the playlist.
 *
 * @exports generatePlaylistDescription - An async function that takes a playlist title and song titles as input and returns a playlist description.
 * @exports GeneratePlaylistDescriptionInput - The input type for the generatePlaylistDescription function.
 * @exports GeneratePlaylistDescriptionOutput - The output type for the generatePlaylistDescription function.
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';

const GeneratePlaylistDescriptionInputSchema = z.object({
  playlistTitle: z.string().describe('The title of the playlist.'),
  songTitles: z.array(z.string()).describe('A list of song titles in the playlist.'),
});
export type GeneratePlaylistDescriptionInput = z.infer<typeof GeneratePlaylistDescriptionInputSchema>;

const GeneratePlaylistDescriptionOutputSchema = z.object({
  description: z.string().describe('A generated description for the playlist.'),
});
export type GeneratePlaylistDescriptionOutput = z.infer<typeof GeneratePlaylistDescriptionOutputSchema>;

export async function generatePlaylistDescription(
  input: GeneratePlaylistDescriptionInput
): Promise<GeneratePlaylistDescriptionOutput> {
  return generatePlaylistDescriptionFlow(input);
}

const generatePlaylistDescriptionPrompt = ai.definePrompt({
  name: 'generatePlaylistDescriptionPrompt',
  model: 'googleai/gemini-1.5-flash',
  input: {schema: GeneratePlaylistDescriptionInputSchema},
  output: {schema: GeneratePlaylistDescriptionOutputSchema},
  prompt: `You are an AI playlist description generator. You will be provided with a playlist title and a list of songs in the playlist.

  Your goal is to generate a concise and engaging description for the playlist that would entice people to listen to it.

  Playlist Title: {{{playlistTitle}}}
  Songs: {{#each songTitles}}{{{this}}}{{#unless @last}}, {{/unless}}{{/each}}

  Description: `,
});

const generatePlaylistDescriptionFlow = ai.defineFlow(
  {
    name: 'generatePlaylistDescriptionFlow',
    inputSchema: GeneratePlaylistDescriptionInputSchema,
    outputSchema: GeneratePlaylistDescriptionOutputSchema,
  },
  async input => {
    const {output} = await generatePlaylistDescriptionPrompt(input);
    return output!;
  }
);

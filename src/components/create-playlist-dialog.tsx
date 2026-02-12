'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { usePlaylists } from '@/contexts/playlist-context';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useToast } from '@/hooks/use-toast';
import { generatePlaylistDescription } from '@/ai/flows/generate-playlist-description';
import { Loader2 } from 'lucide-react';

const formSchema = z.object({
  name: z.string().min(2, {
    message: 'Playlist name must be at least 2 characters.',
  }),
  description: z.string().optional(),
});

interface CreatePlaylistDialogProps {
  children: React.ReactNode;
  container?: HTMLElement | null;
}

export function CreatePlaylistDialog({ children, container }: CreatePlaylistDialogProps) {
  const [open, setOpen] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const { createPlaylist } = usePlaylists();
  const { toast } = useToast();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  });

  const handleGenerateDescription = async () => {
    const playlistTitle = form.getValues('name');
    if (!playlistTitle) {
      toast({
        variant: 'destructive',
        title: 'Playlist title is missing',
        description: 'Please enter a title before generating a description.',
      });
      return;
    }
    setIsGenerating(true);
    try {
      const result = await generatePlaylistDescription({ playlistTitle, songTitles: [] });
      if (result.description) {
        form.setValue('description', result.description);
        toast({
            title: 'Description generated!',
            description: 'The AI has created a description for your playlist.',
        });
      }
    } catch (error) {
      console.error('Failed to generate playlist description:', error);
      toast({
        variant: 'destructive',
        title: 'Generation failed',
        description: 'Could not generate a description at this time.',
      });
    } finally {
      setIsGenerating(false);
    }
  };

  function onSubmit(values: z.infer<typeof formSchema>) {
    createPlaylist(values.name, values.description || '');
    form.reset();
    setOpen(false);
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent container={container} className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Create Playlist</DialogTitle>
          <DialogDescription>
            Give your new playlist a name and an optional description.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input placeholder="My Awesome Playlist" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="A short description of your playlist..."
                      className="resize-none"
                      {...field}
                    />
                  </FormControl>
                   <Button 
                      type="button" 
                      variant="link" 
                      onClick={handleGenerateDescription} 
                      disabled={isGenerating}
                      className="p-0 h-auto text-sm"
                    >
                      {isGenerating ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Generating...
                        </>
                      ) : 'Generate with AI'}
                    </Button>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type="submit">Create Playlist</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

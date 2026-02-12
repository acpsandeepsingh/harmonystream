'use client';

import { useUser, useFirestore, useDoc, useMemoFirebase } from '@/firebase';
import { doc, updateDoc } from 'firebase/firestore';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { format } from 'date-fns';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';

const profileSchema = z.object({
  username: z.string().min(3, { message: 'Username must be at least 3 characters.' }).max(20, { message: 'Username cannot be longer than 20 characters.' }),
});

type UserProfile = {
  username: string;
  email: string;
  dateJoined: string;
  themePreference?: 'light' | 'dark' | 'system';
  usernameIsSet?: boolean;
};

export default function ProfilePage() {
  const { user, isUserLoading } = useUser();
  const firestore = useFirestore();
  const router = useRouter();
  const { toast } = useToast();
  const [isSaving, setIsSaving] = useState(false);

  const userDocRef = useMemoFirebase(() => {
    if (!user) return null;
    return doc(firestore, 'users', user.uid);
  }, [user, firestore]);

  const { data: userProfile, isLoading: isProfileLoading } = useDoc<UserProfile>(userDocRef);
  
  const form = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      username: '',
    },
  });

  useEffect(() => {
    if (!isUserLoading && !user) {
        router.push('/login');
    }
  }, [isUserLoading, user, router]);

  useEffect(() => {
    if (userProfile) {
      form.reset({ username: userProfile.username });
    } else if (user && !isProfileLoading) {
      // Fallback if profile doc doesn't exist yet for some reason
      form.reset({ username: user.displayName || user.email?.split('@')[0] || 'User' });
    }
  }, [userProfile, user, isProfileLoading, form]);
  
  async function onSubmit(values: z.infer<typeof profileSchema>) {
    if (!userDocRef || !userProfile || userProfile.usernameIsSet) return;
    
    setIsSaving(true);
    try {
      await updateDoc(userDocRef, {
        username: values.username,
        usernameIsSet: true,
      });
      toast({
        title: 'Profile updated!',
        description: 'Your username has been saved.',
      });
    } catch (e: any) {
        console.error('Failed to update profile:', e);
        toast({
            variant: 'destructive',
            title: 'Update failed',
            description: e.message || 'Could not save your profile. Please try again.',
        });
    } finally {
        setIsSaving(false);
    }
  }

  if (isUserLoading || (user && isProfileLoading)) {
    return (
      <div className="space-y-8">
        <div>
          <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Your Profile</h1>
          <p className="text-muted-foreground">Your personal information on HarmonyStream.</p>
        </div>
        <Card>
          <CardHeader>
            <div className="flex items-center gap-4">
              <Skeleton className="h-20 w-20 rounded-full" />
              <div className="space-y-2">
                <Skeleton className="h-8 w-48" />
                <Skeleton className="h-5 w-64" />
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4 pt-4">
            <Skeleton className="h-5 w-1/2" />
          </CardContent>
        </Card>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="text-center py-16">
        <h1 className="text-2xl font-bold">Redirecting to login...</h1>
        <p className="text-muted-foreground">Please make sure you are logged in.</p>
      </div>
    );
  }

  if (!userProfile && !isProfileLoading) {
      return (
        <div className="text-center py-16 border-dashed border-2 rounded-lg">
          <h1 className="text-2xl font-bold">Profile not found</h1>
          <p className="text-muted-foreground">It seems your profile data is missing. This can happen for new accounts.</p>
           <p className="text-sm text-muted-foreground mt-2">Please try refreshing the page in a moment.</p>
        </div>
      )
  }

  const displayName = userProfile?.username || user.displayName || user.email?.split('@')[0] || 'User';
  const displayEmail = userProfile?.email || user.email || 'No email available';
  const joinDate = userProfile?.dateJoined 
    ? format(new Date(userProfile.dateJoined), 'PPP') 
    : user.metadata.creationTime 
    ? format(new Date(user.metadata.creationTime), 'PPP')
    : 'Not available';
  const avatarFallback = displayName?.charAt(0).toUpperCase() || 'U';

  const isUsernameEditable = !userProfile?.usernameIsSet;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Your Profile</h1>
        <p className="text-muted-foreground">View and edit your personal information.</p>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
            <Card>
                <CardHeader>
                    <div className="flex items-center gap-4">
                        <Avatar className="h-20 w-20">
                            <AvatarImage src={user.photoURL || `https://avatar.vercel.sh/${user.uid}.png`} alt={displayName} />
                            <AvatarFallback>{avatarFallback}</AvatarFallback>
                        </Avatar>
                        <div className="space-y-2 flex-1">
                            <FormField
                              control={form.control}
                              name="username"
                              render={({ field }) => (
                                <FormItem>
                                  <FormLabel className="text-sm text-muted-foreground">Username</FormLabel>
                                  <FormControl>
                                    <Input 
                                      {...field}
                                      className="text-3xl font-semibold leading-none tracking-tight h-auto p-0 border-none bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0 disabled:cursor-default disabled:opacity-100"
                                      disabled={!isUsernameEditable || isSaving}
                                    />
                                  </FormControl>
                                  <FormMessage />
                                </FormItem>
                              )}
                            />
                            <CardDescription>{displayEmail}</CardDescription>
                        </div>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="space-y-2">
                        <p className="text-sm font-medium">
                            Joined on: <span className="font-normal text-muted-foreground">{joinDate}</span>
                        </p>
                         {!isUsernameEditable && (
                            <p className="text-xs text-muted-foreground pt-2">
                                Usernames can only be set once.
                            </p>
                        )}
                    </div>
                     {isUsernameEditable && (
                        <Button type="submit" disabled={isSaving}>
                            {isSaving ? 'Saving...' : 'Save Profile'}
                        </Button>
                    )}
                </CardContent>
            </Card>
        </form>
      </Form>
    </div>
  );
}

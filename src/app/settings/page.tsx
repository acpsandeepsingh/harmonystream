'use client';

import { useState, useEffect } from 'react';
import { useUser, useFirestore, useDoc, useMemoFirebase } from '@/firebase';
import { doc, updateDoc } from 'firebase/firestore';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useTheme } from 'next-themes';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { useToast } from '@/hooks/use-toast';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Skeleton } from '@/components/ui/skeleton';

const settingsSchema = z.object({
  themePreference: z.enum(['light', 'dark', 'system']),
});

type UserProfile = {
    themePreference?: 'light' | 'dark' | 'system';
};

export default function SettingsPage() {
  const { user, isUserLoading } = useUser();
  const firestore = useFirestore();
  const { setTheme } = useTheme();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);

  const userDocRef = useMemoFirebase(() => {
    if (!user) return null;
    return doc(firestore, 'users', user.uid);
  }, [user, firestore]);

  const { data: userProfile, isLoading: isProfileLoading } = useDoc<UserProfile>(userDocRef);

  const form = useForm<z.infer<typeof settingsSchema>>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
        themePreference: 'system',
    }
  });

  useEffect(() => {
    if (userProfile) {
      form.reset({
        themePreference: userProfile.themePreference || 'system',
      });
    }
  }, [userProfile, form]);
  
  async function onSubmit(values: z.infer<typeof settingsSchema>) {
    if (!user || !userDocRef) return;
    setLoading(true);

    try {
      const updatePayload = {
        themePreference: values.themePreference,
      };

      await updateDoc(userDocRef, updatePayload);
      setTheme(values.themePreference);
      toast({
        title: 'Settings updated!',
        description: 'Your theme preference has been saved.',
      });
    } catch (e: any) {
      console.error('Failed to update settings:', e);
      toast({
        variant: 'destructive',
        title: 'Update failed',
        description: e.message || 'Could not save your settings. Please try again.',
      });
    } finally {
      setLoading(false);
    }
  }
  
  if (isUserLoading || isProfileLoading) {
      return (
          <div className="space-y-8">
              <div>
                  <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Settings</h1>
                  <p className="text-muted-foreground">Manage your account and app preferences.</p>
              </div>
              <Card>
                  <CardHeader>
                      <CardTitle>Theme</CardTitle>
                      <CardDescription>Select your preferred color scheme.</CardDescription>
                  </CardHeader>
                  <CardContent>
                       <div className="space-y-2">
                            <Skeleton className="h-6 w-24" />
                            <Skeleton className="h-6 w-20" />
                            <Skeleton className="h-6 w-28" />
                       </div>
                  </CardContent>
              </Card>
              <Skeleton className="h-10 w-32" />
          </div>
      );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-4xl font-headline font-bold text-foreground mb-2">Settings</h1>
        <p className="text-muted-foreground">Manage your account and app preferences.</p>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
          <Card>
            <CardHeader>
              <CardTitle>Theme</CardTitle>
              <CardDescription>Select your preferred color scheme for the application.</CardDescription>
            </CardHeader>
            <CardContent>
              <FormField
                control={form.control}
                name="themePreference"
                render={({ field }) => (
                  <FormItem className="space-y-3">
                    <FormControl>
                      <RadioGroup
                        onValueChange={(value) => {
                            field.onChange(value);
                            setTheme(value as 'light' | 'dark' | 'system');
                        }}
                        defaultValue={field.value}
                        className="flex flex-col space-y-1"
                      >
                        <FormItem className="flex items-center space-x-3 space-y-0">
                          <FormControl>
                            <RadioGroupItem value="light" />
                          </FormControl>
                          <FormLabel className="font-normal">Light</FormLabel>
                        </FormItem>
                        <FormItem className="flex items-center space-x-3 space-y-0">
                          <FormControl>
                            <RadioGroupItem value="dark" />
                          </FormControl>
                          <FormLabel className="font-normal">Dark</FormLabel>
                        </FormItem>
                        <FormItem className="flex items-center space-x-3 space-y-0">
                          <FormControl>
                            <RadioGroupItem value="system" />
                          </FormControl>
                          <FormLabel className="font-normal">System</FormLabel>
                        </FormItem>
                      </RadioGroup>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </CardContent>
          </Card>

          <Button type="submit" disabled={loading}>
            {loading ? 'Saving...' : 'Save Settings'}
          </Button>
        </form>
      </Form>
    </div>
  );
}

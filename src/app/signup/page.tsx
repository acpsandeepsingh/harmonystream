'use client';

import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import { useAuth, useUser, useFirestore } from '@/firebase';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc } from 'firebase/firestore';
import { FirebaseError } from 'firebase/app';
import { Music } from 'lucide-react';

const formSchema = z.object({
  email: z.string().email({ message: 'Please enter a valid email.' }),
  password: z.string().min(6, { message: 'Password must be at least 6 characters.' }),
});

export default function SignupPage() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const { toast } = useToast();
  const auth = useAuth();
  const firestore = useFirestore();
  const { user, isUserLoading } = useUser();

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  useEffect(() => {
    if (!isUserLoading && user) {
      router.replace('/');
    }
  }, [user, isUserLoading, router]);

  async function onSubmit(values: z.infer<typeof formSchema>) {
    setLoading(true);
    try {
      const userCredential = await createUserWithEmailAndPassword(auth, values.email, values.password);
      const newUser = userCredential.user;
      
      // Create user document in Firestore
      const userDocRef = doc(firestore, 'users', newUser.uid);
      await setDoc(userDocRef, {
        id: newUser.uid,
        email: newUser.email,
        username: newUser.email?.split('@')[0] || `user${Date.now()}`,
        dateJoined: new Date().toISOString(),
        themePreference: 'system',
        usernameIsSet: false,
      });

      toast({
        title: 'Account created!',
        description: "You've been logged in and your profile is saved.",
      });
      router.push('/');
    } catch (error) {
      console.error(error);
      let description = 'An unexpected error occurred.';
      if (error instanceof FirebaseError) {
         switch (error.code) {
            case 'auth/email-already-in-use':
                description = 'This email is already associated with an account.';
                break;
            default:
                description = error.message;
                break;
         }
      }
      toast({
        variant: 'destructive',
        title: 'Sign up failed',
        description,
      });
    } finally {
      setLoading(false);
    }
  }

  if (isUserLoading || (!isUserLoading && user)) {
    return <div className="flex justify-center items-center h-screen w-full"><p>Loading...</p></div>;
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-4">
      <div className="flex items-center gap-2 mb-8">
          <Link href="/" className="flex items-center gap-2">
            <Music className="text-primary h-8 w-8" />
            <h1 className="text-2xl font-headline font-bold">HarmonyStream</h1>
          </Link>
      </div>
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl">Sign Up</CardTitle>
          <CardDescription>Enter your information to create an account.</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" placeholder="m@example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? 'Creating account...' : 'Create Account'}
              </Button>
            </form>
          </Form>
          <div className="mt-4 text-center text-sm">
            Already have an account?{' '}
            <Link href="/login" className="underline text-primary">
              Login
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

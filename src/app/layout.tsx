import type { Metadata } from 'next';
import { Providers } from '@/components/providers';
import { Toaster } from '@/components/ui/toaster';
import './globals.css';
import dynamic from 'next/dynamic';

const FirebaseClientProvider = dynamic(
  () => import('@/firebase/client-provider').then((mod) => mod.FirebaseClientProvider),
  { ssr: false }
);

export const metadata: Metadata = {
  title: 'HarmonyStream',
  description: 'Your personal music streaming experience.',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      </head>
      <body className="font-body antialiased">
        <FirebaseClientProvider>
          <Providers>
              {children}
          </Providers>
        </FirebaseClientProvider>
        <Toaster />
      </body>
    </html>
  );
}

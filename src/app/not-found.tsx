import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="text-center">
        <h1 className="mb-2 text-4xl font-bold">404</h1>
        <p className="mb-6 text-muted-foreground">This page could not be found.</p>
        <Link className="underline" href="/">
          Go back home
        </Link>
      </div>
    </main>
  );
}

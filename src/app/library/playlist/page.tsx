import PlaylistPageClient from './[id]/playlist-page-client';

export default function PlaylistPage({
  searchParams,
}: {
  searchParams: { id?: string };
}) {
  const { id } = searchParams;

  return <PlaylistPageClient id={id ?? ''} />;
}

import PlaylistPageClient from './playlist-page-client';

export default function PlaylistPage({ params }: { params: { id:string } }) {
  // The client component will handle rendering the specific playlist content.
  return <PlaylistPageClient id={params.id} />;
}

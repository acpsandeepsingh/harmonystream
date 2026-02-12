'use server';

import { NextResponse } from 'next/server';

export async function GET(request: Request) {
  return NextResponse.json(
    { error: 'This API route has been disabled in favor of client-side fetching.' }, 
    { status: 404 }
  );
}

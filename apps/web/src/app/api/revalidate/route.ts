/* SPDX-License-Identifier: AGPL-3.0-only */
import { NextResponse, NextRequest } from 'next/server';
import { revalidatePath } from 'next/cache';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const revalidate = 0;

export async function POST(request: NextRequest) {
  try {
    const { path } = await request.json();
    if (path) {
      revalidatePath(path);
      return NextResponse.json({ ok: true, revalidated: path }, {
        headers: {
          'Cache-Control': 'no-store, no-cache, must-revalidate',
        },
      });
    }
    return NextResponse.json({ error: 'Missing path parameter' }, { status: 400 });
  } catch (err: unknown) {
    return NextResponse.json({ error: (err as Error).message }, { status: 500 });
  }
}

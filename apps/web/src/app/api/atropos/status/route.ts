import { NextResponse } from 'next/server';
import {
  allowedCommands,
  repoRoot,
  resolveJar,
  runEngineCommand,
} from '@/lib/bridge/engine';

/** Route handlers spawn the engine, so this must not run on the edge runtime. */
export const runtime = 'nodejs';
/** Engine state changes underneath us; a cached answer would be a stale claim. */
export const dynamic = 'force-dynamic';

export interface EngineStatus {
  /** True only when the engine actually answered. Never optimistic. */
  online: boolean;
  jarPath: string | null;
  workspace: string;
  /** Populated only when offline: why, and what to do about it (§4.1). */
  detail: string | null;
  remedy: string | null;
  durationMs: number;
  /** The read-only surface the browser is permitted to reach. */
  allowedCommands: readonly string[];
  checkedAt: string;
}

/**
 * Reports whether the ATROPOS engine is reachable from this web surface.
 *
 * The web app is a client of the CLI. Every other ATROPOS route depends on the
 * engine, so "is it there" must be answerable directly rather than inferred
 * from a page that silently renders empty.
 */
export async function GET() {
  const jarPath = await resolveJar();
  const workspace = repoRoot();
  const checkedAt = new Date().toISOString();

  if (!jarPath) {
    const status: EngineStatus = {
      online: false,
      jarPath: null,
      workspace,
      detail: 'No ATROPOS jar found. The web surface presents the engine; it is not the engine.',
      remedy: 'Build it with `gradle jar`, or set ATROPOS_JAR to an existing jar.',
      durationMs: 0,
      allowedCommands: allowedCommands(),
      checkedAt,
    };
    return NextResponse.json(status);
  }

  // `/home` is the cheapest command that proves the engine started, loaded its
  // config and rendered a real surface.
  const probe = await runEngineCommand('/home');

  const status: EngineStatus = {
    online: probe.ok,
    jarPath,
    workspace,
    detail: probe.ok ? null : probe.detail,
    remedy: probe.ok ? null : probe.remedy,
    durationMs: probe.durationMs,
    allowedCommands: allowedCommands(),
    checkedAt,
  };
  return NextResponse.json(status);
}

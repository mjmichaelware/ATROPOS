import { NextResponse } from 'next/server';
import { resolveJar, runEngineCommand } from '@/lib/bridge/engine';
import { parseContinuity } from '@/lib/bridge/continuity';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export interface RecoveryReport {
  /** False when the engine could not be asked at all. */
  available: boolean;
  /** True only when durable state actually needed repair. */
  repaired: boolean;
  /** The engine's own continuity line, verbatim. Null when it reported nothing. */
  notice: string | null;
  /** Set when recovery itself could not run — a fault, not a clean start. */
  failure: string | null;
  detail: string | null;
  remedy: string | null;
  checkedAt: string;
}

export async function GET() {
  const checkedAt = new Date().toISOString();
  const jar = await resolveJar();

  if (!jar) {
    return NextResponse.json({
      available: false,
      repaired: false,
      notice: null,
      failure: null,
      detail: 'The engine is not reachable, so recovery state cannot be read.',
      remedy: 'Build the engine with `gradle jar`, or set ATROPOS_JAR.',
      checkedAt,
    } satisfies RecoveryReport);
  }

  // Starting the engine is what runs recovery; `/home` is the cheapest command
  // that gets us a started process and its startup output.
  const result = await runEngineCommand('/home');

  if (!result.ok) {
    return NextResponse.json({
      available: false,
      repaired: false,
      notice: null,
      failure: null,
      detail: result.detail,
      remedy: result.remedy,
      checkedAt,
    } satisfies RecoveryReport);
  }

  const { notice, failure } = parseContinuity(result.stdout);

  return NextResponse.json({
    available: true,
    repaired: notice !== null && failure === null,
    notice,
    failure,
    detail: failure
      ? 'Crash recovery could not run, so stale state may still be present.'
      : null,
    remedy: failure ? 'Run `/agent recover` in the CLI and inspect the queue.' : null,
    checkedAt,
  } satisfies RecoveryReport);
}

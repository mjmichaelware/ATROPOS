import { NextResponse } from 'next/server';
import { allowedCommands, isAllowedCommand, runEngineCommand } from '@/lib/bridge/engine';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export interface CommandResponse {
  ok: boolean;
  command: string;
  /** Engine stdout, verbatim. The web surface presents it; it does not rewrite it. */
  output: string | null;
  reason: string | null;
  detail: string | null;
  remedy: string | null;
  durationMs: number;
}

/**
 * Runs one read-only engine command on behalf of the browser.
 *
 * This is the restricted form of the handoff's `POST /cli`. Unrestricted argv
 * passthrough is deliberately not implemented: the CLI can reach `/shell`,
 * `!command` and `/cd`, so an open passthrough on a localhost port would let
 * any page in the browser execute code on the operator's machine. The refusal
 * is typed and names the allowed set rather than failing opaquely.
 */
export async function POST(request: Request) {
  let command: unknown;
  try {
    const body = await request.json();
    command = (body as { command?: unknown })?.command;
  } catch {
    return NextResponse.json(
      {
        ok: false,
        command: '',
        output: null,
        reason: 'bad-request',
        detail: 'Request body must be JSON containing a "command" string.',
        remedy: 'Send {"command": "/home"}.',
        durationMs: 0,
      } satisfies CommandResponse,
      { status: 400 }
    );
  }

  if (typeof command !== 'string' || command.trim().length === 0) {
    return NextResponse.json(
      {
        ok: false,
        command: '',
        output: null,
        reason: 'bad-request',
        detail: '"command" must be a non-empty string.',
        remedy: `Allowed commands: ${allowedCommands().join(', ')}`,
        durationMs: 0,
      } satisfies CommandResponse,
      { status: 400 }
    );
  }

  const trimmed = command.trim();

  if (!isAllowedCommand(trimmed)) {
    return NextResponse.json(
      {
        ok: false,
        command: trimmed,
        output: null,
        reason: 'refused',
        detail: `'${trimmed}' is not one of the bridge's read-only commands.`,
        remedy: `Run it directly in the CLI. Allowed here: ${allowedCommands().join(', ')}`,
        durationMs: 0,
      } satisfies CommandResponse,
      { status: 403 }
    );
  }

  const result = await runEngineCommand(trimmed);

  if (!result.ok) {
    return NextResponse.json(
      {
        ok: false,
        command: trimmed,
        output: null,
        reason: result.reason,
        detail: result.detail,
        remedy: result.remedy,
        durationMs: result.durationMs,
      } satisfies CommandResponse,
      { status: 502 }
    );
  }

  return NextResponse.json({
    ok: true,
    command: trimmed,
    output: result.stdout,
    reason: null,
    detail: null,
    remedy: null,
    durationMs: result.durationMs,
  } satisfies CommandResponse);
}

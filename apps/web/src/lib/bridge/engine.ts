import { spawn } from 'node:child_process';
import { access, constants } from 'node:fs/promises';
import path from 'node:path';

/**
 * Server-side bridge to the ATROPOS engine.
 *
 * Handoff §2: "Engine stays the CLI. Web and Android are clients." Nothing in
 * this module reimplements policy, DAG, DLOI, territory or verification — it
 * runs the existing `atropos.jar` and returns what the engine said.
 *
 * This module must only ever be imported from server code (route handlers).
 * It spawns processes; importing it into a client component would fail the
 * build, which is the intended guard rail.
 */

/** Why a bridge call could not produce engine output. Never a thrown error. */
export type EngineFailureReason =
  | 'jar-missing'
  | 'java-missing'
  | 'timeout'
  | 'refused'
  | 'engine-error';

export interface EngineSuccess {
  ok: true;
  stdout: string;
  stderr: string;
  durationMs: number;
}

export interface EngineFailure {
  ok: false;
  reason: EngineFailureReason;
  /** Operator-facing explanation. §4.1: a failure states why. */
  detail: string;
  /** The next action that could resolve it. §4.1: and what to do about it. */
  remedy: string;
  durationMs: number;
}

export type EngineResult = EngineSuccess | EngineFailure;

/**
 * Commands the browser may run.
 *
 * The handoff lists `POST /cli` argv passthrough, but an unrestricted
 * passthrough reachable from a browser page is remote code execution against
 * the operator's machine: the CLI can reach `/shell`, `!command` and `/cd`.
 * §13 requires privileged actions to be explicit, so the bridge exposes only
 * read-only introspection. Anything that mutates or escapes is refused with a
 * typed reason rather than silently dropped.
 *
 * Widening this set is a deliberate act and belongs with an approval flow, not
 * a convenience edit.
 */
const READ_ONLY_COMMANDS: readonly string[] = [
  '/home',
  '/dashboard',
  '/status',
  '/status endpoints',
  '/status quota',
  '/providers',
  '/tabs',
  '/agent status',
  '/agent queue list',
  // Read-only project surface. `/project new` and `/project status` mutate and
  // are deliberately absent: a write reachable from the browser needs explicit
  // attribution (§13), which this bridge does not yet carry.
  '/project list',
  '/help',
];

export function isAllowedCommand(command: string): boolean {
  return READ_ONLY_COMMANDS.includes(command.trim());
}

export function allowedCommands(): readonly string[] {
  return READ_ONLY_COMMANDS;
}

/** Workspace root the engine runs against. */
export function repoRoot(): string {
  return process.env.ATROPOS_HOME ?? path.resolve(process.cwd(), '..', '..');
}

/**
 * Resolves the engine jar.
 *
 * Checked in priority order so an operator can point at a specific build
 * without moving files around.
 */
export async function resolveJar(): Promise<string | null> {
  const candidates = [
    process.env.ATROPOS_JAR,
    path.join(repoRoot(), 'build', 'libs', 'ATROPOS.jar'),
    path.join(repoRoot(), 'atropos.jar'),
  ].filter((candidate): candidate is string => Boolean(candidate));

  for (const candidate of candidates) {
    try {
      await access(candidate, constants.R_OK);
      return candidate;
    } catch {
      // Try the next candidate; absence is reported by the caller, not thrown.
    }
  }
  return null;
}

const DEFAULT_TIMEOUT_MS = 20_000;

/**
 * Runs one read-only CLI command and returns the engine's own output.
 *
 * The CLI reads commands from stdin in headless mode, so a command is written
 * and stdin is closed; the process exits on EOF.
 */
export async function runEngineCommand(
  command: string,
  options: { timeoutMs?: number } = {}
): Promise<EngineResult> {
  const started = Date.now();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  if (!isAllowedCommand(command)) {
    return {
      ok: false,
      reason: 'refused',
      detail: `'${command}' is not one of the bridge's read-only commands.`,
      remedy: `Run it directly in the CLI. Allowed here: ${READ_ONLY_COMMANDS.join(', ')}`,
      durationMs: Date.now() - started,
    };
  }

  const jar = await resolveJar();
  if (!jar) {
    return {
      ok: false,
      reason: 'jar-missing',
      detail: 'No ATROPOS jar found. The web surface is a client; the engine is the CLI.',
      remedy: 'Build it with `gradle jar`, or set ATROPOS_JAR to an existing jar.',
      durationMs: Date.now() - started,
    };
  }

  return new Promise<EngineResult>((resolve) => {
    let child: ReturnType<typeof spawn>;
    try {
      child = spawn('java', ['-jar', jar], {
        cwd: repoRoot(),
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch {
      resolve({
        ok: false,
        reason: 'java-missing',
        detail: 'A Java runtime could not be started.',
        remedy: 'Install a JRE (Termux: `pkg install openjdk-21`) and retry.',
        durationMs: Date.now() - started,
      });
      return;
    }

    let stdout = '';
    let stderr = '';
    let settled = false;

    const finish = (result: EngineResult) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(result);
    };

    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish({
        ok: false,
        reason: 'timeout',
        detail: `The engine did not answer '${command}' within ${timeoutMs}ms.`,
        remedy: 'Retry, or run the command directly in the CLI to see where it stalls.',
        durationMs: Date.now() - started,
      });
    }, timeoutMs);

    child.stdout?.on('data', (chunk) => {
      stdout += String(chunk);
    });
    child.stderr?.on('data', (chunk) => {
      stderr += String(chunk);
    });

    child.on('error', (error: NodeJS.ErrnoException) => {
      finish({
        ok: false,
        reason: error.code === 'ENOENT' ? 'java-missing' : 'engine-error',
        detail:
          error.code === 'ENOENT'
            ? 'A Java runtime could not be started.'
            : `The engine process failed: ${error.message}`,
        remedy:
          error.code === 'ENOENT'
            ? 'Install a JRE (Termux: `pkg install openjdk-21`) and retry.'
            : 'Run the same command directly in the CLI to reproduce it.',
        durationMs: Date.now() - started,
      });
    });

    child.on('close', (code) => {
      if (code !== 0) {
        finish({
          ok: false,
          reason: 'engine-error',
          detail: `The engine exited with code ${code}.`,
          remedy: 'Run the same command directly in the CLI to reproduce it.',
          durationMs: Date.now() - started,
        });
        return;
      }
      finish({ ok: true, stdout, stderr, durationMs: Date.now() - started });
    });

    child.stdin?.write(`${command}\n`);
    child.stdin?.end();
  });
}

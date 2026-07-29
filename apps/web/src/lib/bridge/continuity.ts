/**
 * Parses the engine's startup continuity notice.
 *
 * Lives outside the route handler because Next route files may only export
 * HTTP verbs, and this needs to be importable by tests and by any other
 * surface that reports recovery.
 *
 * `RuntimeContinuitySupervisor.startupNotice` emits exactly two shapes: a
 * repair summary, or a statement that crash recovery could not run. Silence
 * means nothing needed repair.
 */
const CONTINUITY_PREFIX = 'continuity:';

export interface ContinuityParse {
  notice: string | null;
  failure: string | null;
}

export function parseContinuity(stdout: string): ContinuityParse {
  const line = stdout
    .split('\n')
    .map((value) => value.trim())
    .find((value) => value.startsWith(CONTINUITY_PREFIX));

  if (!line) return { notice: null, failure: null };

  // "did not run" is a fault: stale leases may still be present, and it must
  // never be reported as a clean start.
  if (line.includes('crash recovery did not run')) {
    return { notice: line, failure: line };
  }
  return { notice: line, failure: null };
}

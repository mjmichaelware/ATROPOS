import { describe, expect, it } from 'vitest';
import { parseContinuity } from '@/lib/bridge/continuity';

/**
 * The fixtures below are the two strings
 * `RuntimeContinuitySupervisor.startupNotice` can actually emit. If that
 * format changes, these fail — which is the point: the web ribbon reports the
 * engine's own words and must not quietly start showing nothing.
 */
describe('parseContinuity', () => {
  it('reports nothing when the engine printed no continuity line', () => {
    const stdout = 'ATROPOS\ngroq · /home/user/ATROPOS · /help\n── HOME ──\n';
    expect(parseContinuity(stdout)).toEqual({ notice: null, failure: null });
  });

  it('captures a repair report verbatim', () => {
    const line =
      'continuity: recovered 2 queue, 1 session, 0 dag claim, 3 interrupted run(s)';
    const stdout = `ATROPOS\n${line}\n── HOME ──\n`;

    const result = parseContinuity(stdout);

    expect(result.notice).toBe(line);
    expect(result.failure).toBeNull();
  });

  it('distinguishes recovery that could not run from a clean start', () => {
    const line = 'continuity: crash recovery did not run — IOException: lock held';
    const stdout = `ATROPOS\n${line}\n`;

    const result = parseContinuity(stdout);

    // A failed recovery must never be reported as "nothing needed repair":
    // stale leases would still be present and the operator would not know.
    expect(result.failure).toBe(line);
    expect(result.notice).toBe(line);
  });

  it('keeps the error suffix the supervisor appends', () => {
    const line =
      'continuity: recovered 1 queue, 0 session, 0 dag claim, 0 interrupted run(s) (2 error(s): stale lease)';
    const result = parseContinuity(`${line}\n`);

    expect(result.notice).toContain('2 error(s)');
    expect(result.failure).toBeNull();
  });
});

/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Whether a surface may animate, derived from real work.
 *
 * `HOE-E03`: "Animate only when real progress exists; never fake activity."
 * A spinner that runs because a request is in flight — or worse, because a
 * component mounted — tells the operator that something is happening when the
 * engine may be idle, blocked, or dead. That is the same class of lie as a
 * permanently-green health row, and it is the more convincing one because
 * motion reads as liveness.
 *
 * So animation is a function of observed node progress, not of network state.
 * The rule is deliberately strict: motion requires a running item *and* a
 * change since the last observation. A run that is stuck at the same
 * checkpoint is running and not progressing, and showing it as animated would
 * hide exactly the situation the operator needs to notice.
 */

export interface ActivityObservation {
  /** Items the engine reports as running right now. */
  readonly runningCount: number;
  /**
   * A value that changes when work advances — a checkpoint, an attempt count,
   * a node id. Compared, never interpreted.
   */
  readonly progressToken: string;
}

export interface ActivitySignal {
  /** True only when real, changing work is underway. */
  readonly animate: boolean;
  /** What the surface should say when it is not animating. */
  readonly reason: 'progressing' | 'idle' | 'running-without-progress' | 'reduced-motion';
}

/**
 * Decides whether to animate.
 *
 * `prefersReducedMotion` short-circuits everything. Source Doc 3 makes reduced
 * motion a release-blocking accessibility requirement, so it is honoured before
 * any progress question is asked rather than as a modifier afterwards.
 */
export function activitySignal(
  previous: ActivityObservation | null,
  current: ActivityObservation,
  prefersReducedMotion: boolean,
): ActivitySignal {
  if (prefersReducedMotion) return { animate: false, reason: 'reduced-motion' };
  if (current.runningCount <= 0) return { animate: false, reason: 'idle' };
  if (previous === null) {
    // First observation: something is running and there is no prior token to
    // compare against. Animate — a run that has just been seen is the case
    // where motion is most informative and least likely to be misleading.
    return { animate: true, reason: 'progressing' };
  }
  if (previous.progressToken === current.progressToken) {
    return { animate: false, reason: 'running-without-progress' };
  }
  return { animate: true, reason: 'progressing' };
}

/**
 * A stable token from the engine's running work.
 *
 * Built from the fields that change when a node advances. Deliberately not a
 * timestamp: a clock always changes, so a timestamp-derived token would make
 * every observation look like progress and defeat the check entirely.
 */
export function progressTokenFrom(
  running: ReadonlyArray<{ id: string; state: string; detail: string; attempt: number | null }>,
): string {
  return running
    .map((item) => `${item.id}:${item.state}:${item.detail}:${item.attempt ?? '-'}`)
    .sort()
    .join('|');
}

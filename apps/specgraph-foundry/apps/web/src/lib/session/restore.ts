/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Restoring a persisted session, and saying what actually came back.
 *
 * HOE-A09 pairs layout persistence with a recovery report; HOE-E06 forbids a
 * silent resume. Both fail the same way if restore is a `try/catch` inside a
 * component: the failure has nowhere to go except the console, and the operator
 * is shown a default workspace that is indistinguishable from a fresh one.
 *
 * Pulled out of the provider so the restore rules can be tested without
 * rendering a React tree — a restore path that cannot be exercised is a restore
 * path that will be wrong.
 */

export interface RecoveryReport {
  /** True when a stored session was found and used. */
  restored: boolean;
  /** Fields that were present but unusable and therefore reset to a default. */
  dropped: number;
  /** True when nothing was lost — the only case a surface may stay silent. */
  clean: boolean;
  message: string;
}

export interface RestoreResult<T> {
  session: T;
  report: RecoveryReport;
}

const clean = (message: string, restored: boolean): RecoveryReport => ({
  restored,
  dropped: 0,
  clean: true,
  message,
});

/**
 * Merges a stored payload over the defaults, reporting what it had to discard.
 *
 * Unknown keys are ignored rather than trusted: a payload written by a later
 * build can carry fields this one has no meaning for, and spreading them in
 * would let stored data introduce state the running code never validates.
 */
export function restoreSession<T extends object>(
  stored: string | null | undefined,
  defaults: T,
): RestoreResult<T> {
  if (stored == null || stored === '') {
    return { session: defaults, report: clean('Starting a new session.', false) };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(stored);
  } catch {
    return {
      session: defaults,
      report: {
        restored: false,
        dropped: 0,
        clean: false,
        message: 'Your saved session could not be read and was replaced with a new one.',
      },
    };
  }

  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return {
      session: defaults,
      report: {
        restored: false,
        dropped: 0,
        clean: false,
        message: 'Your saved session had an unexpected shape and was replaced with a new one.',
      },
    };
  }

  const candidate = parsed as Record<string, unknown>;
  const session: T = { ...defaults };
  const writable = session as Record<string, unknown>;
  const defaultsByKey = defaults as Record<string, unknown>;
  const droppedKeys: string[] = [];

  Object.keys(defaultsByKey).forEach((key) => {
    if (!(key in candidate)) return;
    const value = candidate[key];
    const fallback = defaultsByKey[key];

    // A field whose default is null is genuinely nullable, so the default
    // carries no type to compare against. Accept the primitives such a field
    // can legitimately hold — including null itself — and refuse objects and
    // arrays, whose shape cannot be checked without a schema this layer does
    // not have. Comparing `typeof` against a null default would instead drop
    // every valid value, because `typeof null` is "object".
    if (fallback === null) {
      if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) {
        writable[key] = value;
      } else {
        droppedKeys.push(key);
      }
      return;
    }

    // Otherwise type identity against the default is the check. A stored
    // `openTabs` that arrived as a string, or a `simpleModeEnabled` that
    // arrived as null, would reach components typed to expect neither.
    if (value === null || typeof value !== typeof fallback) {
      droppedKeys.push(key);
      return;
    }
    if (Array.isArray(fallback) !== Array.isArray(value)) {
      droppedKeys.push(key);
      return;
    }
    writable[key] = value;
  });

  if (droppedKeys.length === 0) {
    return { session, report: clean('Session restored.', true) };
  }

  return {
    session,
    report: {
      restored: true,
      dropped: droppedKeys.length,
      clean: false,
      message: `Session restored, but ${droppedKeys.length} setting(s) were unreadable and reset to defaults: ${droppedKeys.join(', ')}.`,
    },
  };
}

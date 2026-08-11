/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, type EngineFailure } from '@/lib/engine/client';

/**
 * Reads stored reasoning at the depth this surface asked for.
 *
 * `HOE-C06` puts multi-level thinking in a drawer; `HOE-E04` requires the depth
 * to be this surface's own, so a terminal expanded to L3 never drags the Web
 * with it. The depth therefore travels as a request parameter and is never
 * persisted anywhere shared.
 *
 * The engine filters. This module does not receive the full record and trim it
 * — that would put the depth rule in two places, and the Web's copy would be
 * the one nobody checks.
 */

export type ThinkingDepth = 1 | 2 | 3;

export const THINKING_DEPTHS: readonly ThinkingDepth[] = [1, 2, 3];

/** `HOE-A08`'s rule applied to depth: collapsed by default. */
export const DEFAULT_THINKING_DEPTH: ThinkingDepth = 1;

export interface ThinkingLine {
  id: string;
  minDepth: number;
  text: string;
}

export interface ThinkingAbsent {
  ok: true;
  present: false;
  detail: string;
  remedy: string;
}

export interface ThinkingPresent {
  ok: true;
  present: true;
  nodeId: string;
  depth: number;
  depthLabel: string;
  /** Whether expanding would reveal anything. */
  hasMore: boolean;
  deepestAvailable: number;
  levels: { level: number; label: string }[];
  lines: ThinkingLine[];
}

export type ThinkingPayload = ThinkingAbsent | ThinkingPresent;

export type ThinkingResult =
  | { ok: true; data: ThinkingPayload }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

export const thinking = {
  read: (nodeId: string, depth: ThinkingDepth = DEFAULT_THINKING_DEPTH): Promise<ThinkingResult> =>
    readEngine<ThinkingPayload>(
      `/v1/thinking?nodeId=${encodeURIComponent(nodeId)}&depth=${depth}`,
    ),
};

/**
 * The next depth to offer, or null when there is nothing further.
 *
 * Null is what removes the expand control. `HOE-C08`'s rule — morph only when
 * there is something to reveal — applies here for the same reason it applies to
 * evidence: a gesture that sometimes does nothing teaches the operator to stop
 * using it, including on the occasions when it would have worked.
 */
export function nextDepth(payload: ThinkingPresent): ThinkingDepth | null {
  if (!payload.hasMore) return null;
  const next = payload.depth + 1;
  return THINKING_DEPTHS.includes(next as ThinkingDepth) ? (next as ThinkingDepth) : null;
}

/** True when the operator can collapse back toward the outline. */
export function canCollapse(payload: ThinkingPresent): boolean {
  return payload.depth > DEFAULT_THINKING_DEPTH;
}

/**
 * Checks that a deeper payload only ever added to a shallower one.
 *
 * Exported so the guarantee is assertable against real responses rather than
 * assumed from the engine's comment. Disclosure that silently filters looks
 * correct at every individual level, which is exactly why it needs a check that
 * compares two.
 */
export function isAdditive(shallower: ThinkingPresent, deeper: ThinkingPresent): boolean {
  const seen = new Set(deeper.lines.map((line) => line.id));
  return shallower.lines.every((line) => seen.has(line.id));
}

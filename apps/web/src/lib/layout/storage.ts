/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The layout theme: how the shell arranges its panes.
 *
 * F-WEB-003 fixes two layouts and one default. "Session-first" is the default:
 * a conversation surface with the stream as the hero. "Workbench" is the
 * switchable VS Code arrangement — left explorer, center tabs, bottom log
 * panel, right AI rail — for operators who want Cursor's spatial memory
 * without forking an editor.
 *
 * This module owns only the choice and its persistence. It knows nothing about
 * color themes (`lib/theme/storage.ts` is a separate axis: dark/light/high-
 * contrast applies within either layout), nothing about which components render
 * in which pane, and nothing about the DOM. A pure state machine plus a storage
 * adapter keeps both halves testable without jsdom.
 *
 * Persistence survives refresh (F-WEB-012 depends on it), so the key is
 * namespaced under `atropos.` — not SpecGraph's `specgraph.theme`, which is the
 * color axis and must not be coupled to layout.
 */

export type LayoutTheme = 'session' | 'workbench';

export const LAYOUT_THEMES: readonly LayoutTheme[] = Object.freeze([
  'session',
  'workbench',
]);

const STORAGE_KEY = 'atropos.layout';

/** F-WEB-003: session-first is the default theme. */
export const DEFAULT_LAYOUT: LayoutTheme = 'session';

function isLayoutTheme(value: unknown): value is LayoutTheme {
  return typeof value === 'string' && (LAYOUT_THEMES as readonly string[]).includes(value);
}

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>;

/**
 * Reads the persisted layout, falling back to the default.
 *
 * Storage may be absent (SSR, tests, privacy mode); any failure or unknown
 * value means the default, never a throw — a cockpit that crashes because
 * localStorage was disabled would invert a preference into a fault.
 */
export function readLayoutTheme(storage: StorageLike | undefined): LayoutTheme {
  try {
    const value = storage?.getItem(STORAGE_KEY);
    return isLayoutTheme(value) ? value : DEFAULT_LAYOUT;
  } catch {
    return DEFAULT_LAYOUT;
  }
}

/** Persists the layout. Throws only on genuinely invalid input, like writeTheme does. */
export function writeLayoutTheme(storage: StorageLike, layout: LayoutTheme): void {
  if (!isLayoutTheme(layout)) {
    throw new Error(`invalid layout theme: ${String(layout)}`);
  }
  try {
    storage.setItem(STORAGE_KEY, layout);
  } catch {
    // Private-browsing quota failures must not break the toggle; the choice
    // simply lives for this page view.
  }
}

/** Applies the layout as a data attribute so CSS can key off it. */
export function applyLayoutTheme(root: HTMLElement, layout: LayoutTheme): void {
  root.dataset.layout = layout;
}

/**
 * Toggles between the two themes.
 *
 * Two themes make toggle unambiguous; if a third layout ever lands, replace
 * this with a cycle function — do not overload toggle to mean "next".
 */
export function toggleLayout(current: LayoutTheme): LayoutTheme {
  return current === 'session' ? 'workbench' : 'session';
}

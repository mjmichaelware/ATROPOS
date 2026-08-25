/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The editor tab strip's state (F-WEB-005).
 *
 * Scope discipline from the atom: "Do not build full LSP IDE; viewer +
 * agent-driven edits." A buffer here is therefore a path, its text, and a
 * dirty marker — no language services, no syntax tree, no undo stack. Agent
 * patches arrive as diffs and replace the text; the surface never edits
 * semantics it cannot verify.
 *
 * Pure functions over an immutable snapshot. React state lives with the caller
 * (`useTabStore` below is the only place `useState` appears), so the transition
 * rules are testable without a renderer.
 */

export interface EditorTab {
  /** Workspace-relative path; also the identity. Opening twice focuses, not duplicates. */
  readonly path: string;
  readonly title: string;
  /** Undefined while loading. */
  readonly content?: string;
  readonly dirty: boolean;
}

export interface TabStore {
  readonly tabs: readonly EditorTab[];
  /** Path of the focused tab, or undefined when none are open. */
  readonly activePath?: string;
}

export const EMPTY_STORE: TabStore = { tabs: [] };

/** Title is the basename; the full path lives in the tab's tooltip. */
export function titleFor(path: string): string {
  const segments = path.split('/');
  return segments[segments.length - 1] || path;
}

/**
 * Opens a tab, or focuses it if already open.
 *
 * Content is supplied by the caller when known; a tab opened without content
 * stays in its loading state until the fetch resolves through [setContent].
 */
export function openTab(
  store: TabStore,
  path: string,
  content?: string
): TabStore {
  const existing = store.tabs.find((tab) => tab.path === path);
  if (existing) {
    return { ...store, activePath: path };
  }
  return {
    tabs: [
      ...store.tabs,
      { path, title: titleFor(path), content, dirty: false },
    ],
    activePath: path,
  };
}

/** Replaces one tab's text and marks it dirty. Unknown paths are ignored. */
export function setContent(store: TabStore, path: string, content: string): TabStore {
  return {
    ...store,
    tabs: store.tabs.map((tab) =>
      tab.path === path && tab.content !== content
        ? { ...tab, content, dirty: true }
        : tab
    ),
  };
}

/** Marks a tab clean after a save/patch lands. */
export function markClean(store: TabStore, path: string): TabStore {
  return {
    ...store,
    tabs: store.tabs.map((tab) =>
      tab.path === path ? { ...tab, dirty: false } : tab
    ),
  };
}

/**
 * Closes a tab and moves focus to its right neighbour, then left on the edge.
 *
 * Closing the active tab must never leave focus dangling: VS Code moves right
 * first, and matching that keeps spatial memory intact.
 */
export function closeTab(store: TabStore, path: string): TabStore {
  const index = store.tabs.findIndex((tab) => tab.path === path);
  if (index < 0) return store;

  const tabs = store.tabs.filter((tab) => tab.path !== path);
  if (store.activePath !== path || tabs.length === 0) {
    return { tabs, activePath: tabs.length === 0 ? undefined : store.activePath };
  }

  // Right neighbour, else the new last tab.
  const next = tabs[Math.min(index, tabs.length - 1)];
  return { tabs, activePath: next.path };
}

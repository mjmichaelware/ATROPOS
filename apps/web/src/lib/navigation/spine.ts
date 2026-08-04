/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The HOE-A02 primary navigation spine, and the Developer Tools container.
 *
 * Two rules are encoded here rather than left to each renderer.
 *
 * HOE-A02 fixes the spine and its order. A component that hard-codes its own
 * list drifts the moment an entry moves, and the drift is invisible because
 * each surface looks internally consistent.
 *
 * HOE-C07 keeps SpecGraph out of the primary navigation entirely — "No
 * SpecGraph in primary nav; Developer Tools hidden by default". That is why
 * SpecGraph is not an eleventh spine entry with a flag: a flag is something a
 * renderer can ignore, whereas absence from `NAV_SPINE` cannot be rendered by
 * accident. Reaching it requires deliberately reading `DEVELOPER_TOOLS`.
 */

export interface NavItem {
  id: string;
  label: string;
  path: string;
}

export const NAV_SPINE: readonly NavItem[] = Object.freeze([
  { id: 'home', label: 'Home', path: '/' },
  { id: 'projects', label: 'Projects', path: '/projects' },
  { id: 'work', label: 'Work', path: '/work' },
  { id: 'conversations', label: 'Conversations', path: '/conversations' },
  { id: 'files', label: 'Files', path: '/files' },
  { id: 'agents', label: 'Agents', path: '/agents' },
  { id: 'models', label: 'Models', path: '/models' },
  { id: 'automation', label: 'Automation', path: '/automation' },
  { id: 'history', label: 'History', path: '/history' },
  { id: 'settings', label: 'Settings', path: '/settings' },
]);

export const DEVELOPER_TOOLS = Object.freeze({
  id: 'developer',
  label: 'Developer Tools',
  path: '/developer',
  hiddenByDefault: true,
  tenants: Object.freeze([
    { id: 'specgraph', label: 'SpecGraph', path: '/developer/specgraph' },
  ]),
});

/** True when a path belongs to the hidden Developer Tools container. */
export function isDeveloperPath(path: string): boolean {
  return path === DEVELOPER_TOOLS.path || path.startsWith(`${DEVELOPER_TOOLS.path}/`);
}

/**
 * The spine as rendered.
 *
 * Developer Tools appears only when the operator has revealed it, and never
 * inside the spine array itself — callers render it as a separate affordance so
 * a future `map` over the spine cannot leak it into primary navigation.
 */
export function visibleNav(developerToolsRevealed: boolean): {
  spine: readonly NavItem[];
  developer: typeof DEVELOPER_TOOLS | null;
} {
  return {
    spine: NAV_SPINE,
    developer: developerToolsRevealed ? DEVELOPER_TOOLS : null,
  };
}

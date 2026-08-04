/* SPDX-License-Identifier: AGPL-3.0-only */

import type { Session, Workspace } from '@atropos/web-contracts/session';
import {
  activateSession,
  activeSession,
  addSession,
  closeSession,
  createSession,
  createWorkspace,
} from '@atropos/web-contracts/session';

/**
 * Territory-isolated tabs for the web surface.
 *
 * `SUP.UX.SESSION-TAB-DENSITY` states the rule this exists to keep: "Switching
 * sessions never mixes territory or evidence." The web's tab list was a
 * `string[]` of project ids, which cannot express a territory at all, so
 * nothing in the surface could tell which paths a tab was permitted to act in
 * — every tab implicitly had all of them.
 *
 * The transitions themselves are not reimplemented here. They live in the
 * shared contracts package so the CLI, web and Android surfaces answer "which
 * session is active and what may it touch" identically; this module is the thin
 * adapter that lets the existing React context store that model.
 */
export type { Session, Workspace };

export function emptyWorkspace(): Workspace {
  return createWorkspace();
}

/**
 * Opens a tab for a project, or focuses it when already open.
 *
 * Re-opening focuses rather than throwing: clicking a project twice is an
 * ordinary thing to do, and the underlying model's duplicate refusal exists to
 * stop two *different* sessions sharing an id, not to punish that.
 */
export function openProjectTab(
  workspace: Workspace,
  projectId: string,
  territory: readonly string[] = [],
): Workspace {
  if (workspace.sessions.some((s) => s.id === projectId)) {
    return activateSession(workspace, projectId);
  }
  return addSession(
    workspace,
    createSession({ id: projectId, title: projectId, projectId, territory }),
  );
}

export function closeProjectTab(workspace: Workspace, projectId: string): Workspace {
  return closeSession(workspace, projectId);
}

export function focusProjectTab(workspace: Workspace, projectId: string): Workspace {
  return activateSession(workspace, projectId);
}

/**
 * The territory the active tab may act within.
 *
 * Returns an empty list when nothing is focused. An empty territory means "no
 * paths", never "all paths" — a surface that read absence as permission would
 * invert the guarantee this module exists to provide.
 */
export function activeTerritory(workspace: Workspace): readonly string[] {
  return activeSession(workspace)?.territory ?? [];
}

/** True when the active tab is permitted to act on this path. */
export function activeTabPermits(workspace: Workspace, path: string): boolean {
  return activeTerritory(workspace).some(
    (allowed) => path === allowed || path.startsWith(`${allowed}/`),
  );
}

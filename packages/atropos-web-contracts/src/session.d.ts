/* SPDX-License-Identifier: AGPL-3.0-only */

/** Types for the shared session, tab and layout model. */

export interface Session {
  readonly id: string;
  readonly title: string;
  readonly projectId: string | null;
  /** Paths this session may act within. Never merged across sessions. */
  readonly territory: readonly string[];
  readonly createdAt: number | null;
}

export interface Workspace {
  readonly sessions: readonly Session[];
  readonly activeId: string | null;
}

export interface RecoveryReport {
  readonly restored: number;
  readonly dropped: number;
  readonly message: string;
  readonly clean: boolean;
}

export const MAX_SESSIONS: number;
export const LAYOUT_STORAGE_KEY: string;

export function createSession(input: {
  id: string;
  title?: string;
  projectId?: string | null;
  territory?: readonly string[];
}): Session;

export function createWorkspace(): Workspace;

/** Throws on a duplicate id or when the session limit is reached. */
export function addSession(workspace: Workspace, session: Session): Workspace;

export function closeSession(workspace: Workspace, id: string): Workspace;

/** Throws when the session is not open; never creates one. */
export function activateSession(workspace: Workspace, id: string): Workspace;

export function activeSession(workspace: Workspace): Session | null;

export function serializeLayout(workspace: Workspace): string;

export function restoreLayout(raw: string | null | undefined): {
  workspace: Workspace;
  report: RecoveryReport;
};

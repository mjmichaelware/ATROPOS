/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The session, tab and layout model shared by ATROPOS surfaces.
 *
 * HOE-C03 requires an OpenCode-parity session/tab model whose workspace
 * survives refresh; HOE-A09 requires layout persistence with a recovery report.
 * Both are pure state transitions, so they live here rather than inside a React
 * component: logic embedded in a component can only be exercised by rendering
 * one, and a surface that cannot test its own restore path will ship a broken
 * one.
 *
 * SUP.UX.SESSION-TAB-DENSITY adds the constraint that matters most — switching
 * sessions must never mix territory or evidence. Every function here therefore
 * treats a session's territory as opaque and never merges two of them.
 */

/** Sessions beyond this are refused rather than silently dropped. */
export const MAX_SESSIONS = 12;

export const LAYOUT_STORAGE_KEY = 'atropos.layout.v1';

export function createSession({ id, title, projectId = null, territory = [] }) {
  if (!id || typeof id !== 'string') throw new Error('session id is required');
  return Object.freeze({
    id,
    title: title ?? id,
    projectId,
    territory: Object.freeze([...territory]),
    createdAt: null,
  });
}

export function createWorkspace() {
  return Object.freeze({ sessions: Object.freeze([]), activeId: null });
}

/**
 * Adds a session and focuses it.
 *
 * Refuses a duplicate id instead of replacing: two tabs sharing an id would let
 * a switch land on either one, and the operator could not tell which territory
 * they were about to act in.
 */
export function addSession(workspace, session) {
  if (workspace.sessions.some((s) => s.id === session.id)) {
    throw new Error(`session already open: ${session.id}`);
  }
  if (workspace.sessions.length >= MAX_SESSIONS) {
    throw new Error(`session limit reached (${MAX_SESSIONS})`);
  }
  return Object.freeze({
    sessions: Object.freeze([...workspace.sessions, session]),
    activeId: session.id,
  });
}

export function closeSession(workspace, id) {
  const remaining = workspace.sessions.filter((s) => s.id !== id);
  if (remaining.length === workspace.sessions.length) return workspace;
  const activeId =
    workspace.activeId === id ? (remaining[remaining.length - 1]?.id ?? null) : workspace.activeId;
  return Object.freeze({ sessions: Object.freeze(remaining), activeId });
}

/** Focuses a session, or refuses when it is not open. Never creates one. */
export function activateSession(workspace, id) {
  if (!workspace.sessions.some((s) => s.id === id)) {
    throw new Error(`cannot activate a session that is not open: ${id}`);
  }
  return Object.freeze({ ...workspace, activeId: id });
}

export function activeSession(workspace) {
  return workspace.sessions.find((s) => s.id === workspace.activeId) ?? null;
}

/** Serialises the workspace for durable storage. Territory is carried per session. */
export function serializeLayout(workspace) {
  return JSON.stringify({
    version: 1,
    activeId: workspace.activeId,
    sessions: workspace.sessions.map((s) => ({
      id: s.id,
      title: s.title,
      projectId: s.projectId,
      territory: s.territory,
    })),
  });
}

/**
 * Restores a workspace and reports what actually came back.
 *
 * HOE-E06 forbids a silent resume, so this returns a report rather than just
 * state. `restored` is never inferred from the absence of an error: a payload
 * that parsed but contained nothing usable restores an empty workspace and says
 * so, which is what lets the ribbon tell the operator the truth.
 */
export function restoreLayout(raw) {
  if (raw == null || raw === '') {
    return { workspace: createWorkspace(), report: report(0, 0, 'no saved layout') };
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { workspace: createWorkspace(), report: report(0, 0, 'saved layout was unreadable') };
  }
  if (parsed?.version !== 1 || !Array.isArray(parsed.sessions)) {
    return { workspace: createWorkspace(), report: report(0, 0, 'saved layout had an unknown shape') };
  }

  const usable = parsed.sessions.filter((s) => s && typeof s.id === 'string' && s.id !== '');
  const dropped = parsed.sessions.length - usable.length;
  const sessions = usable.map((s) =>
    createSession({
      id: s.id,
      title: typeof s.title === 'string' ? s.title : s.id,
      projectId: typeof s.projectId === 'string' ? s.projectId : null,
      territory: Array.isArray(s.territory) ? s.territory : [],
    }),
  );

  // A saved active id that is no longer present must not silently focus a
  // different session: the operator would be acting in a territory they did not
  // choose. Focus nothing and report it.
  const savedActive = typeof parsed.activeId === 'string' ? parsed.activeId : null;
  const activeId = sessions.some((s) => s.id === savedActive) ? savedActive : null;
  const notes = [];
  if (dropped > 0) notes.push(`${dropped} unreadable session(s) dropped`);
  if (savedActive && !activeId) notes.push('previously active session is gone; nothing focused');

  return {
    workspace: Object.freeze({ sessions: Object.freeze(sessions), activeId }),
    report: report(sessions.length, dropped, notes.length > 0 ? notes.join('; ') : 'layout restored'),
  };
}

function report(restored, dropped, message) {
  return Object.freeze({ restored, dropped, message, clean: dropped === 0 });
}

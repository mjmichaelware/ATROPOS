/* SPDX-License-Identifier: AGPL-3.0-only */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_SESSIONS,
  createSession,
  createWorkspace,
  addSession,
  closeSession,
  activateSession,
  activeSession,
  serializeLayout,
  restoreLayout,
} from '../src/session.mjs';

const s = (id, territory = []) => createSession({ id, title: id, territory });

test('adding a session focuses it', () => {
  const w = addSession(createWorkspace(), s('a'));
  assert.equal(w.activeId, 'a');
  assert.equal(activeSession(w).id, 'a');
});

test('duplicate session ids are refused, not replaced', () => {
  const w = addSession(createWorkspace(), s('a'));
  assert.throws(() => addSession(w, s('a')), /already open/);
});

test('the session limit is enforced', () => {
  let w = createWorkspace();
  for (let i = 0; i < MAX_SESSIONS; i += 1) w = addSession(w, s(`s${i}`));
  assert.throws(() => addSession(w, s('overflow')), /limit reached/);
});

test('switching sessions never merges territory', () => {
  let w = addSession(createWorkspace(), s('a', ['src/a']));
  w = addSession(w, s('b', ['src/b']));
  w = activateSession(w, 'a');
  assert.deepEqual([...activeSession(w).territory], ['src/a']);
  w = activateSession(w, 'b');
  assert.deepEqual([...activeSession(w).territory], ['src/b']);
});

test('activating a session that is not open refuses rather than creating one', () => {
  const w = addSession(createWorkspace(), s('a'));
  assert.throws(() => activateSession(w, 'ghost'), /not open/);
});

test('closing the active session focuses a remaining one', () => {
  let w = addSession(createWorkspace(), s('a'));
  w = addSession(w, s('b'));
  w = closeSession(w, 'b');
  assert.equal(w.activeId, 'a');
});

test('closing the last session leaves nothing focused', () => {
  const w = closeSession(addSession(createWorkspace(), s('a')), 'a');
  assert.equal(w.activeId, null);
  assert.equal(activeSession(w), null);
});

test('a workspace round-trips through storage', () => {
  let w = addSession(createWorkspace(), s('a', ['src/a']));
  w = addSession(w, s('b', ['src/b']));
  const { workspace, report } = restoreLayout(serializeLayout(w));
  assert.equal(workspace.sessions.length, 2);
  assert.equal(workspace.activeId, 'b');
  assert.equal(report.restored, 2);
  assert.equal(report.clean, true);
});

test('no saved layout restores empty and says so', () => {
  const { workspace, report } = restoreLayout(null);
  assert.equal(workspace.sessions.length, 0);
  assert.match(report.message, /no saved layout/);
});

test('unreadable storage restores empty rather than throwing', () => {
  const { workspace, report } = restoreLayout('{not json');
  assert.equal(workspace.sessions.length, 0);
  assert.match(report.message, /unreadable/);
});

test('an unknown layout version is refused', () => {
  const { report } = restoreLayout(JSON.stringify({ version: 99, sessions: [] }));
  assert.match(report.message, /unknown shape/);
});

test('a missing active session focuses nothing and reports it', () => {
  const raw = JSON.stringify({
    version: 1,
    activeId: 'gone',
    sessions: [{ id: 'a', title: 'a', projectId: null, territory: [] }],
  });
  const { workspace, report } = restoreLayout(raw);
  assert.equal(workspace.activeId, null, 'must not silently focus a different territory');
  assert.match(report.message, /nothing focused/);
});

test('dropped sessions are counted in the recovery report', () => {
  const raw = JSON.stringify({
    version: 1,
    activeId: 'a',
    sessions: [{ id: 'a' }, { id: '' }, { nope: true }],
  });
  const { workspace, report } = restoreLayout(raw);
  assert.equal(workspace.sessions.length, 1);
  assert.equal(report.dropped, 2);
  assert.equal(report.clean, false);
});

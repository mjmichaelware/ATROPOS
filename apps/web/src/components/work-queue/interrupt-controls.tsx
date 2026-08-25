/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Interrupt controls for running work (ADD-W-003).
 *
 * Soft interrupt is live against the bridge's cancel route. Hard interrupt
 * and freeze are now wired to their engine routes. Freeze shows a toggle;
 * hard interrupt is a terminal cancel. All verbs are attributed.
 */
'use client';

import { useEffect, useState } from 'react';
import {
  INTERRUPT_GAPS,
  cancelWork,
  runningWork,
  freezeQueue,
  resumeQueue,
  getFreezeStatus,
  hardInterrupt,
  type RunningWork,
  type FreezeStatus,
} from '@/lib/work-queue/client';

type Load =
  | { kind: 'loading' }
  | { kind: 'fault'; detail: string; remedy: string }
  | { kind: 'idle' }
  | { kind: 'ready'; items: RunningWork[] };

export function InterruptControls() {
  const [state, setState] = useState<Load>({ kind: 'loading' });
  const [busyId, setBusyId] = useState<string | null>(null);
  const [fault, setFault] = useState<string | null>(null);
  const [freezeStatus, setFreezeStatus] = useState<FreezeStatus | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await runningWork();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else if (result.data.length === 0) {
        setState({ kind: 'idle' });
      } else {
        setState({ kind: 'ready', items: result.data });
      }
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await getFreezeStatus();
      if (cancelled) return;
      if (result.ok) {
        setFreezeStatus(result.data);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  async function soft(id: string) {
    setBusyId(id);
    setFault(null);
    const outcome = await cancelWork(id);
    setBusyId(null);
    if (!outcome.ok) {
      setFault(`${outcome.detail} ${outcome.remedy}`);
    }
  }

  async function toggleFreeze() {
    if (!freezeStatus) return;
    if (freezeStatus.frozen) {
      const outcome = await resumeQueue();
      if (outcome.ok && outcome.data.changed) {
        setFreezeStatus({ ok: true, frozen: false, changed: true });
      }
    } else {
      const outcome = await freezeQueue();
      if (outcome.ok && outcome.data.changed) {
        setFreezeStatus({ ok: true, frozen: true, changed: true });
      }
    }
  }

  async function handleHard(id: string) {
    setBusyId(id);
    setFault(null);
    const outcome = await hardInterrupt(id);
    setBusyId(null);
    if (!outcome.ok) {
      setFault(`${outcome.detail} ${outcome.remedy}`);
    }
  }

  return (
    <section aria-label="Interrupt running work" data-testid="interrupt-controls">
      <p className="wb-pane-title">Interrupt</p>
      {state.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
      {state.kind === 'fault' && (
        <div role="status">
          <p className="wb-fault">{state.detail}</p>
          <p className="wb-pane-note">{state.remedy}</p>
        </div>
      )}
      {state.kind === 'idle' && <p className="wb-pane-note">Nothing is running.</p>}
      {fault && (
        <p role="alert" className="wb-fault">
          {fault}
        </p>
      )}
      {freezeStatus && (
        <div className="wb-freeze-status">
          <span className="wb-freeze-label">Queue freeze:</span>
          <button
            type="button"
            className={freezeStatus.frozen ? 'wb-toggle wb-toggle-active' : 'wb-toggle'}
            onClick={toggleFreeze}
            disabled={state.kind !== 'ready'}
          >
            {freezeStatus.frozen ? 'Frozen' : 'Active'}
          </button>
        </div>
      )}
      {state.kind === 'ready' && (
        <ul className="wb-interrupt-list">
          {state.items.map((item) => (
            <li key={item.id} className="wb-interrupt-row">
              <span className="wb-session-title" title={item.id}>
                {item.title}
              </span>
              <div className="wb-interrupt-actions">
                <button
                  type="button"
                  disabled={busyId === item.id}
                  onClick={() => soft(item.id)}
                  className="wb-interrupt-btn"
                >
                  Soft interrupt
                </button>
                <button
                  type="button"
                  disabled={busyId === item.id}
                  onClick={() => handleHard(item.id)}
                  className="wb-interrupt-btn wb-interrupt-btn-hard"
                >
                  Hard interrupt
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {INTERRUPT_GAPS.length > 0 && (
        <p className="wb-pane-note wb-scope-note">
          Not served by this bridge build: {INTERRUPT_GAPS.join(' · ')}.
        </p>
      )}
    </section>
  );
}

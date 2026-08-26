/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The durable pending-approval list (F-WEB-008).
 *
 * Cards render the engine's own projection: who asked, what operation, which
 * territory, and why policy stopped it. The operator decides; this component
 * never decides, never retries silently, and renders every refusal it meets.
 *
 * Three states stay apart on purpose (§4.1): the bridge did not answer, the
 * bridge answered and nothing waits, and the bridge answered with cards. A
 * fetch failure must not read as "nothing to approve".
 */
'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  decideApproval,
  readPendingApprovals,
  sortForDecision,
} from '@/lib/approvals/client';
import type { ApprovalCard } from '@atropos/web-contracts';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'fault'; detail: string; remedy: string }
  | { kind: 'ready'; cards: ApprovalCard[] };

/** Current surface identity for self-approval blocking (W1-02). */
const WEB_SURFACE_IDENTITY = 'web-cockpit';

function isSelfApprove(card: ApprovalCard): boolean {
  // W1-02: Block self-approval when the current surface identity matches the proposer.
  // The proposer is the identity that originated the request (e.g., "patch:patch-123").
  // The current surface identity is "web-cockpit" for the web surface.
  return card.proposer === WEB_SURFACE_IDENTITY;
}

function isDecided(card: ApprovalCard): boolean {
  return card.decision !== null && card.decision !== undefined;
}

function getDecisionStatus(card: ApprovalCard): 'approved' | 'rejected' | 'pending' {
  if (!card.decision) return 'pending';
  return card.decision.approved ? 'approved' : 'rejected';
}

function getApprover(card: ApprovalCard): string | null {
  return card.decision?.approver ?? null;
}

function getDecidedAt(card: ApprovalCard): string | null {
  return card.decision?.decidedAt ?? null;
}

function getDecisionSurface(card: ApprovalCard): string | null {
  return card.decision?.surface ?? null;
}

export function BridgeApprovalList({ onChanged }: { onChanged?: () => void }) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [busyId, setBusyId] = useState<string | null>(null);
  const [decisionFault, setDecisionFault] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const result = await readPendingApprovals();
    if (!result.ok) {
      setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      return;
    }
    setState({ kind: 'ready', cards: result.data });
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await readPendingApprovals();
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: 'fault', detail: result.detail, remedy: result.remedy });
      } else {
        setState({ kind: 'ready', cards: result.data });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function decide(card: ApprovalCard, approved: boolean) {
    // Block self-approval: the actor (proposer) cannot approve their own request
    if (isSelfApprove(card)) {
      setDecisionFault('Self-approval is not permitted. Another operator must review.');
      return;
    }

    setBusyId(card.id);
    setDecisionFault(null);
    const outcome = await decideApproval(card.id, approved);
    setBusyId(null);
    if (!outcome.ok) {
      setDecisionFault(`${outcome.detail} ${outcome.remedy}`);
      return;
    }
    await refresh();
  }

  return (
    <section aria-label="Pending approvals" data-testid="bridge-approval-list">
      <p className="wb-pane-title">Approvals</p>
      {state.kind === 'loading' && <p className="wb-pane-note">Reading…</p>}
      {state.kind === 'fault' && (
        <div role="status">
          <p className="wb-fault">{state.detail}</p>
          <p className="wb-pane-note">{state.remedy}</p>
        </div>
      )}
      {state.kind === 'ready' && state.cards.length === 0 && (
        <p className="wb-pane-note">Nothing waiting on a human decision.</p>
      )}
      {decisionFault && (
        <p role="alert" className="wb-fault">
          {decisionFault}
        </p>
      )}
      {state.kind === 'ready' &&
        sortForDecision(state.cards).map((card) => {
          const decided = isDecided(card);
          const status = getDecisionStatus(card);
          const approver = getApprover(card);
          const decidedAt = getDecidedAt(card);
          const surface = getDecisionSurface(card);

          return (
            <article key={card.id} className={`wb-approval-card ${decided ? 'wb-approval-decided' : ''}`}>
              <header className="wb-approval-head">
                <span className="wb-approval-op">{card.operation}</span>
                <span className="wb-approval-id" title={card.id}>
                  {card.actor}
                </span>
                {card.proposer && (
                  <span className="wb-approval-proposer" title="Proposer">
                    proposed by {card.proposer}
                  </span>
                )}
              </header>
              <p className="wb-approval-reason">{card.reason}</p>
              {card.territory.length > 0 ? (
                <ul className="wb-approval-territory" aria-label="Declared territory">
                  {card.territory.map((path) => (
                    <li key={path}>{path}</li>
                  ))}
                </ul>
              ) : (
                <p className="wb-pane-note">No territory declared.</p>
              )}
              {decided && (
                <div className="wb-approval-decision">
                  <p className="wb-approval-decision-status">
                    <span className={`wb-decision-status wb-decision-${status}`}>
                      {status.charAt(0).toUpperCase() + status.slice(1)}
                    </span>
                    <span className="wb-decision-meta">
                      approved by {approver ?? 'unknown'} · {getDecisionSurface(card) ?? 'unknown'}
                      {decidedAt && ` · ${new Date(decidedAt).toLocaleString()}`}
                    </span>
                  </p>
                </div>
              )}
              <div className="wb-approval-actions">
                {!decided && !isSelfApprove(card) && (
                  <button
                    type="button"
                    disabled={busyId === card.id}
                    onClick={() => decide(card, true)}
                  >
                    Approve
                  </button>
                )}
                {!decided && !isSelfApprove(card) && (
                  <button
                    type="button"
                    disabled={busyId === card.id}
                    onClick={() => decide(card, false)}
                  >
                    Reject
                  </button>
                )}
                {decided && (
                  <span className="wb-approval-action-taken">
                    Action taken — no further action needed
                  </span>
                )}
                {isSelfApprove(card) && !decided && (
                  <p className="wb-pane-note wb-self-approve-blocked">
                    Cannot approve your own request. Another operator must decide.
                  </p>
                )}
              </div>
            </article>
          )})}
    </section>
  );
}
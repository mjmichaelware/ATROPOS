/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Renders S-005 evidence references behind a completion claim.
 *
 * The chip shows the claim id and which gates stood behind it; the CAS hash
 * is the tooltip, not the label — a 64-character digest is proof for a
 * machine and noise for an operator. A dropped count is surfaced when the
 * engine sent refs this build's contract rejects, because silently hiding
 * malformed evidence would flatter the claim (§4.1).
 */
'use client';

import type { EvidenceRef } from '@atropos/web-contracts';

export function EvidenceChips({
  refs,
  dropped = 0,
}: {
  refs: readonly EvidenceRef[];
  /** Rows the contract guard rejected; zero in the normal case. */
  dropped?: number;
}) {
  if (refs.length === 0 && dropped === 0) return null;
  // ADD-W-006: optical focus is an attestation statement, token-driven.
  // Fully validated evidence renders sharp; any dropped row softens the
  // whole set — the claim is only as sharp as its weakest support.
  const strength = dropped > 0 ? 'soft' : 'sharp';
  return (
    <ul
      className="wb-evidence-chips"
      data-focus-strength={strength}
      aria-label={`Evidence references, ${strength}`}
    >
      {refs.map((ref) => (
        <li
          key={`${ref.claimId}:${ref.casHash}`}
          className={dropped > 0 ? 'wb-evidence-chip wb-focus-soft' : 'wb-evidence-chip'}
          title={`cas:${ref.casHash}`}
        >
          <span className="wb-evidence-claim">{ref.claimId}</span>
          {ref.gateIds.length > 0 && (
            <span className="wb-evidence-gates">· {ref.gateIds.join(' · ')}</span>
          )}
        </li>
      ))}
      {dropped > 0 && (
        <li className="wb-evidence-dropped" role="status">
          {dropped} evidence ref{dropped === 1 ? '' : 's'} unreadable — not shown as support.
        </li>
      )}
    </ul>
  );
}

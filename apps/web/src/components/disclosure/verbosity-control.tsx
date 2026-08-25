/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The web verbosity control (ADD-W-004).
 *
 * Four named levels from the shared disclosure contract; writes only the
 * web channel. Mounted in the workbench AI rail so the preference lives next
 * to the surfaces that honour it (ThinkingDrawer reads the same context).
 */
'use client';

import { useWebDisclosure } from '@/lib/contexts/web-disclosure-context';
import { DISCLOSURE_LABELS, DISCLOSURE_LEVELS } from '@/lib/disclosure/levels';

export function VerbosityControl() {
  const { level, setLevel } = useWebDisclosure();
  return (
    <div role="group" aria-label="Detail level (this browser only)" className="wb-verbosity">
      <p className="wb-pane-title">Detail level</p>
      <div className="wb-verbosity-row">
        {DISCLOSURE_LEVELS.map((candidate) => (
          <button
            key={candidate}
            type="button"
            aria-pressed={level === candidate}
            className={level === candidate ? 'wb-toggle wb-toggle-active' : 'wb-toggle'}
            onClick={() => setLevel(candidate)}
          >
            {DISCLOSURE_LABELS[candidate]}
          </button>
        ))}
      </div>
      <p className="wb-pane-note">This browser only — other surfaces keep their own level.</p>
    </div>
  );
}

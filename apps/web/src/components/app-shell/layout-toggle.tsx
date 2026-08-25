/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The layout switch (F-WEB-003): Session ↔ Workbench.
 *
 * A labelled pair, not an unlabeled icon — the two layouts are a vocabulary
 * the operator has to learn once, and the active one is marked with
 * `aria-pressed` rather than color alone (§E).
 */
'use client';

import { useLayoutTheme } from '@/lib/contexts/layout-theme-context';

export function LayoutToggle() {
  const { layout, setLayout } = useLayoutTheme();
  return (
    <div className="wb-layout-toggle" role="group" aria-label="Layout">
      <button
        type="button"
        aria-pressed={layout === 'session'}
        className={layout === 'session' ? 'wb-toggle wb-toggle-active' : 'wb-toggle'}
        onClick={() => setLayout('session')}
      >
        Session
      </button>
      <button
        type="button"
        aria-pressed={layout === 'workbench'}
        className={layout === 'workbench' ? 'wb-toggle wb-toggle-active' : 'wb-toggle'}
        onClick={() => setLayout('workbench')}
      >
        Workbench
      </button>
    </div>
  );
}

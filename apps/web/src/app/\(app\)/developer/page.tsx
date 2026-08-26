/* SPDX-License-Identifier: AGPL-3.0-only */

import { SpecGraphPage } from './specgraph/page';
import { EvidenceLedgerBrowser } from '@/components/evidence/evidence-ledger-browser';

export default function DeveloperPage() {
  return (
    <div className="space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Developer Tools
        </h1>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
          Runtime internals: SpecGraph subsystem and evidence ledger.
        </p>
      </header>

      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            SpecGraph
          </h2>
        </div>
        <SpecGraphPage />
      </section>

      <section className="space-y-4">
        <div className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-3">
          <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Evidence ledger
          </h2>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
            Read-only browser over the engine's evidence list at <code className="font-mono text-xs">/v1/evidence/list</code>.
          </p>
        </div>
        <EvidenceLedgerBrowser />
      </section>
    </div>
  );
}
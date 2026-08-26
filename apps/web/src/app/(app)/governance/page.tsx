/* SPDX-License-Identifier: AGPL-3.0-only */
import { GovernancePanel } from '@/components/governance/governance-panel';
import { AmendmentChain } from '@/components/governance/amendment-chain';
import { OpsDashboard } from '@/components/governance/ops-dashboard';

export default function GovernancePage() {
  return (
    <div className="space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Governance
        </h1>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
          What the system has proposed about changing itself, what was accepted, and whether the
          result was better.
        </p>
      </header>
      <GovernancePanel />
      <AmendmentChain />
      <OpsDashboard />
    </div>
  );
}

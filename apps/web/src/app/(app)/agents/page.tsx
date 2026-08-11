/* SPDX-License-Identifier: AGPL-3.0-only */
import { GlobalSection } from '@/components/workspace/global-section';

export default function AgentsPage() {
  return (
    <GlobalSection
      title="Agents"
      description="Agents dispatched under a project, and the territory each holds."
      segment="agents"
    />
  );
}

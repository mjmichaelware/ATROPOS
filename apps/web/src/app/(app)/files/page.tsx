/* SPDX-License-Identifier: AGPL-3.0-only */
import { ExportPanel } from '@/components/export/export-panel';
import { GlobalSection } from '@/components/workspace/global-section';
import { SovereignIdeWorkstation } from '@/components/ide/sovereign-ide-workstation';

export default function FilesPage() {
  return (
    <GlobalSection
      title="Sovereign IDE & Files"
      description="Full-screen IDE workstation, AST explorer, and persistent file territory."
      segment="files"
    >
      <div className="space-y-6">
        <SovereignIdeWorkstation projectId="atropos-workspace" projectName="ATROPOS Sovereign Core" />
        <ExportPanel />
      </div>
    </GlobalSection>
  );
}

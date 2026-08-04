/* SPDX-License-Identifier: AGPL-3.0-only */
import { ExportPanel } from '@/components/export/export-panel';
import { GlobalSection } from '@/components/workspace/global-section';

export default function FilesPage() {
  return (
    <GlobalSection
      title="Files"
      description="Files the engine has read or written within a project's territory."
      segment="files"
    >
      {/* SUP.ART.ROOT-OR-DOWNLOADS: where an artifact lands is the operator's
          choice, so the picker lives with the files rather than behind an
          export dialog that only appears once there is something to export. */}
      <ExportPanel />
    </GlobalSection>
  );
}

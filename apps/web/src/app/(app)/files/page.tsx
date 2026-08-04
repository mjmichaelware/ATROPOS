/* SPDX-License-Identifier: AGPL-3.0-only */
import { GlobalSection } from '@/components/workspace/global-section';

export default function FilesPage() {
  return (
    <GlobalSection
      title="Files"
      description="Files the engine has read or written within a project's territory."
      segment="files"
    />
  );
}

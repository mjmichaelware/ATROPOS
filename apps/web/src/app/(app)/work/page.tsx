/* SPDX-License-Identifier: AGPL-3.0-only */
import { GlobalSection } from '@/components/workspace/global-section';

export default function WorkPage() {
  return (
    <GlobalSection
      title="Work"
      description="Active and queued work items for the project you are in."
      segment="work"
    />
  );
}

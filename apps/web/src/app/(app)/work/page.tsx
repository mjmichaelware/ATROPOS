/* SPDX-License-Identifier: AGPL-3.0-only */
import { CheckpointRail } from '@/components/checkpoint/checkpoint-rail';
import { GlobalSection } from '@/components/workspace/global-section';

export default function WorkPage() {
  return (
    <GlobalSection
      title="Work"
      description="Active and queued work items for the project you are in."
      segment="work"
    >
      {/* HOE-C04: the checkpoint is a product object, and Resume is its
          primary action. It sits above the fold on Work because that is where
          an operator returns to continue. */}
      <CheckpointRail />
    </GlobalSection>
  );
}

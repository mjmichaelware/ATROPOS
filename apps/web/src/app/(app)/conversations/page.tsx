/* SPDX-License-Identifier: AGPL-3.0-only */
import { GlobalSection } from '@/components/workspace/global-section';

export default function ConversationsPage() {
  return (
    <GlobalSection
      title="Conversations"
      description="Provider and agent conversations recorded against a project."
      segment="conversations"
    />
  );
}

/* SPDX-License-Identifier: AGPL-3.0-only */
import React from 'react';
import { MessageStream } from '@/components/streaming/message-stream';
import { engineBaseUrl } from '@/lib/engine/client';

/**
 * HOE-C03 / UI-DELTA-WEB-024: Project-scoped Conversation View
 */
export function ConversationView({ projectId }: { projectId: string }) {
  const url = `${engineBaseUrl()}/v1/events?project=${encodeURIComponent(projectId)}`;
  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 border rounded-lg p-4">
      <h2 className="text-lg font-semibold mb-4">Conversation</h2>
      <div className="flex-1 overflow-y-auto">
        <MessageStream eventSourceUrl={url} />
      </div>
    </div>
  );
}

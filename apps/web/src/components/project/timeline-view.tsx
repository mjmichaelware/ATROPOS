/* SPDX-License-Identifier: AGPL-3.0-only */
import React from 'react';

/**
 * UI-DELTA-WEB-024: Timeline View
 */
export function TimelineView({ projectId }: { projectId: string }) {
  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 border rounded-lg p-4">
      <h2 className="text-lg font-semibold mb-4">Project Timeline</h2>
      <div className="flex-1 flex items-center justify-center border-2 border-dashed border-gray-200 rounded">
        <p className="text-sm text-gray-500">Timeline events mapped to project goals will appear here.</p>
      </div>
    </div>
  );
}

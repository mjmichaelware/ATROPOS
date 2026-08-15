/* SPDX-License-Identifier: AGPL-3.0-only */
import React, { useEffect, useState } from 'react';
import { engineBaseUrl } from '@/lib/engine/client';

/**
 * UI-DELTA-WEB-024: Execution Monitor View
 * Periodically polls the engine activity stream.
 */
export function ExecutionMonitor({ projectId }: { projectId: string }) {
  const [activity, setActivity] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    const fetchActivity = async () => {
      try {
        const res = await fetch(`${engineBaseUrl()}/v1/activity`);
        if (res.ok) {
          const data = await res.json();
          if (mounted) setActivity(data);
        } else {
          if (mounted) setError('Failed to fetch activity stream');
        }
      } catch (e) {
        if (mounted) setError('Engine unreachable');
      }
    };
    fetchActivity();
    const interval = setInterval(fetchActivity, 3000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  if (error) return <div className="p-4 text-red-500">{error}</div>;
  if (!activity) return <div className="p-4">Loading execution status...</div>;

  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 border rounded-lg p-4">
      <h2 className="text-lg font-semibold mb-4">Execution Monitor</h2>
      <div className="space-y-2 overflow-y-auto flex-1">
        {activity.events?.length === 0 ? (
          <p className="text-sm text-gray-500">No recent activity.</p>
        ) : (
          activity.events?.map((ev: any, idx: number) => (
            <div key={idx} className="flex justify-between items-center p-2 border-b last:border-0">
              <span className="font-mono text-xs text-gray-400">{ev.timestamp}</span>
              <span className="text-sm font-medium">{ev.description || ev.state}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

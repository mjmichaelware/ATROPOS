/* SPDX-License-Identifier: AGPL-3.0-only */
import { ActivityMonitor } from '@/components/activity/activity-monitor';

export default function ActivityPage() {
  return (
    <div className="space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Activity
        </h1>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
          Every plan, provider, tool, diff, test, verifier, artifact and deploy state change, in
          one stream. Stages that have not reported are shown as gaps, not omitted.
        </p>
      </header>
      <ActivityMonitor />
    </div>
  );
}

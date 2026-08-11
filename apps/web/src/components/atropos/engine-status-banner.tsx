'use client';

import { useEngineStatus } from '@/lib/hooks/use-engine-status';
import { FailureVisibility } from '@/components/ui/failure-visibility';

/**
 * Surfaces engine reachability on every ATROPOS route.
 *
 * The web app presents an engine it does not contain. When that engine is
 * missing, every page below this banner is empty for one specific reason, and
 * §4.1 requires that reason to be visible rather than inferred from blankness.
 *
 * When the engine is online this renders nothing: a healthy system should not
 * spend the operator's attention saying so.
 */
export function EngineStatusBanner() {
  const { status, loading, error, refresh } = useEngineStatus();

  if (loading || (!status && !error)) {
    return null;
  }

  if (error) {
    return (
      <div className="px-4 pt-4">
        <FailureVisibility
          failures={[
            {
              title: 'Engine status unknown',
              reason: `The status route could not be reached: ${error}`,
              nextAction: 'Reload the page. If it persists, the web server itself is not serving API routes.',
              recoveryOptions: ['Retry'],
              severity: 'warning',
            },
          ]}
          onAction={refresh}
        />
      </div>
    );
  }

  if (!status || status.online) {
    return null;
  }

  return (
    <div className="px-4 pt-4">
      <FailureVisibility
        failures={[
          {
            title: 'ATROPOS engine unreachable',
            reason:
              status.detail ??
              'The engine did not answer. This surface presents the CLI; it does not replace it.',
            nextAction:
              status.remedy ?? 'Start the engine, then retry. Workspace: ' + status.workspace,
            recoveryOptions: ['Retry'],
            severity: 'error',
          },
        ]}
        onAction={refresh}
      />
    </div>
  );
}

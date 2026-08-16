import type { ControlVerb } from '@/components/ui/control-verbs';

export type OperationSurfaceId = 'snapshots' | 'security' | 'autonomous' | 'swarm' | 'platform';

export interface OperationSurfaceSpec {
  id: string;
  kind: OperationSurfaceId;
  label: string;
  summary: string;
  status: 'ready' | 'not-configured' | 'not-available';
  controlVerbs: readonly ControlVerb[];
}

export interface ConfiguredOperation {
  id: string;
  label: string;
  configured: boolean;
  available: boolean;
}

export const OPERATION_SURFACE_SPECS: readonly OperationSurfaceSpec[] = [
  {
    id: 'snapshots',
    kind: 'snapshots',
    label: 'Snapshots',
    summary: 'Capture gallery and compare state when a verified preview is available.',
    status: 'not-configured',
    controlVerbs: ['inspect', 'export'],
  },
  {
    id: 'security',
    kind: 'security',
    label: 'Security',
    summary: 'Inspect redaction and vault state without rendering secret material.',
    status: 'ready',
    controlVerbs: ['inspect'],
  },
  {
    id: 'autonomous',
    kind: 'autonomous',
    label: 'Autonomous',
    summary: 'Shows which execution mode is driving the active project.',
    status: 'ready',
    controlVerbs: ['inspect', 'pause', 'cancel'],
  },
  {
    id: 'swarm',
    kind: 'swarm',
    label: 'Swarm',
    summary: 'Swarm coordination is not available in this surface yet.',
    status: 'not-available',
    controlVerbs: ['inspect'],
  },
  {
    id: 'platform',
    kind: 'platform',
    label: 'Platform',
    summary: 'Shows the active surface and its bounded capabilities.',
    status: 'ready',
    controlVerbs: ['inspect'],
  },
];

export function projectConfiguredOperations(
  operations: readonly ConfiguredOperation[],
): readonly OperationSurfaceSpec[] {
  return operations
    .filter((operation) => operation.configured)
    .map((operation) => ({
      id: operation.id,
      kind: 'platform' as const,
      label: operation.label,
      summary: operation.available ? `Configured operation: ${operation.id}` : `Declared operation: ${operation.id}`,
      status: operation.available ? 'ready' as const : 'not-configured' as const,
      controlVerbs: ['inspect'] as const,
    }));
}

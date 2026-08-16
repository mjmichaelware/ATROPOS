'use client';

import { OperationSurfaceCard } from './operation-surface-card';
import { OPERATION_SURFACE_SPECS, projectConfiguredOperations, type ConfiguredOperation } from './operation-surface-model';

export function OperationSurfaceRegistry({ operations = [] }: { operations?: readonly ConfiguredOperation[] }) {
  const configured = operations.length === 0 ? OPERATION_SURFACE_SPECS : [
    ...OPERATION_SURFACE_SPECS,
    ...projectConfiguredOperations(operations)
      .filter((spec) => !OPERATION_SURFACE_SPECS.some((existing) => existing.id === spec.id)),
  ];
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3" data-operation-surface-registry>
      {configured.map((spec) => <OperationSurfaceCard key={spec.id} spec={spec} />)}
    </div>
  );
}

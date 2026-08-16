import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OperationSurfaceRegistry } from './operation-surface-registry';
import { OPERATION_SURFACE_SPECS } from './operation-surface-model';

describe('operation surface registry', () => {
  it('renders every developer surface with an honest status', () => {
    const { container } = render(<OperationSurfaceRegistry />);
    for (const spec of OPERATION_SURFACE_SPECS) {
      expect(screen.getByText(spec.label)).toBeInTheDocument();
    }
    expect(container.querySelectorAll('[data-operation-surface]')).toHaveLength(OPERATION_SURFACE_SPECS.length);
    expect(screen.getByText('not available')).toBeInTheDocument();
  });

  it('adds a configured operation without a new surface component', () => {
    render(<OperationSurfaceRegistry operations={[{ id: 'cli.status', label: 'Status', configured: true, available: true }]} />);
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Configured operation: cli.status')).toBeInTheDocument();
  });
});

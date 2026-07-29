import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WorkItemCard } from './work-item-card';
import { SessionStateProvider, useSessionState } from '@/lib/contexts/session-state-context';
import type { WorkItem } from '@/lib/api-atropos/types';
import { useEffect } from 'react';

const item: WorkItem = {
  id: 'w-42',
  project_id: 'p1',
  title: 'Migrate the cascade',
  description: 'Move the provider cascade onto the bounded agency gate.',
  status: 'working',
  priority: 'high',
  created_at: '2026-07-29T00:00:00Z',
  updated_at: '2026-07-29T01:00:00Z',
  progress: 40,
};

/** Sets the persisted level, then renders the card beneath it. */
function AtLevel({ level }: { level: 1 | 2 | 3 | 4 }) {
  const { setInformationLevel } = useSessionState();
  useEffect(() => {
    setInformationLevel(level);
  }, [level, setInformationLevel]);
  return <WorkItemCard item={item} />;
}

function renderAt(level: 1 | 2 | 3 | 4) {
  return render(
    <SessionStateProvider>
      <AtLevel level={level} />
    </SessionStateProvider>
  );
}

/**
 * §5.0: "No information is removed between levels. Each level only reveals
 * additional information." These tests exist so a future change cannot turn
 * the levels into a filter that hides what a lower level showed.
 */
describe('progressive disclosure levels', () => {
  // The provider hydrates from localStorage on mount, so a level persisted by
  // one case would otherwise overwrite the next case's level.
  beforeEach(() => localStorage.clear());

  it('level 1 shows the essentials', () => {
    renderAt(1);
    expect(screen.getByText('Migrate the cascade')).toBeTruthy();
    // The explainability controls are never a detail: §5.3 requires them.
    expect(screen.getByRole('button', { name: /Why\?/ })).toBeTruthy();
  });

  it('level 2 adds description and progress without removing level 1', () => {
    renderAt(2);
    expect(screen.getByText('Migrate the cascade')).toBeTruthy();
    expect(screen.getByText(/Move the provider cascade/)).toBeTruthy();
    expect(screen.getByText('40% complete')).toBeTruthy();
  });

  it('level 3 adds identity without removing levels 1 and 2', () => {
    renderAt(3);
    expect(screen.getByText('Migrate the cascade')).toBeTruthy();
    expect(screen.getByText(/Move the provider cascade/)).toBeTruthy();
    expect(screen.getByText('40% complete')).toBeTruthy();
    expect(screen.getByText('w-42')).toBeTruthy();
  });

  it('level 4 adds the raw record and still keeps everything below it', () => {
    renderAt(4);
    expect(screen.getByText('Migrate the cascade')).toBeTruthy();
    expect(screen.getByText(/Move the provider cascade/)).toBeTruthy();
    expect(screen.getByText('w-42')).toBeTruthy();
    expect(screen.getByText('Raw record')).toBeTruthy();
  });

  it('renders at the safe default when no session provider is present', () => {
    // Chrome must not require the provider to render (level 2 default).
    render(<WorkItemCard item={item} />);
    expect(screen.getByText('Migrate the cascade')).toBeTruthy();
    expect(screen.getByText(/Move the provider cascade/)).toBeTruthy();
  });
});

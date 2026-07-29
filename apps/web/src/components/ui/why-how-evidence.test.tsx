import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { WhyHowEvidence } from './why-how-evidence';
import { WorkItemCard } from '@/components/atropos/work-item-card';
import type { WorkItem } from '@/lib/api-atropos/types';

/**
 * These tests exist to stop a plausible-sounding explanation from ever being
 * substituted for a missing one. §5.3: missing stays missing.
 */
describe('WhyHowEvidence', () => {
  it('states that no rationale was recorded rather than inventing one', () => {
    render(<WhyHowEvidence answers={{}} />);
    fireEvent.click(screen.getByRole('button', { name: /Why\?/ }));

    expect(screen.getByText(/No rationale was recorded/)).toBeTruthy();
    expect(screen.getByText(/Nothing is inferred here/)).toBeTruthy();
  });

  it('shows the recorded rationale verbatim when there is one', () => {
    render(<WhyHowEvidence answers={{ reasoning: '97cff09c [S0013] lines 46-48' }} />);
    fireEvent.click(screen.getByRole('button', { name: /Why\?/ }));

    expect(screen.getByText('97cff09c [S0013] lines 46-48')).toBeTruthy();
  });

  it('reports that no pipeline is provided, because no producer emits one', () => {
    render(<WhyHowEvidence answers={{ reasoning: 'because' }} />);
    fireEvent.click(screen.getByRole('button', { name: /How\?/ }));

    expect(screen.getByText(/does not yet report a pipeline/)).toBeTruthy();
  });

  it('offers the evidence control even when there is no evidence', () => {
    // Gate 5: the affordance is always present. Its absence would be
    // indistinguishable from a surface that simply forgot to offer it.
    render(<WhyHowEvidence evidence={[]} />);
    fireEvent.click(screen.getByRole('button', { name: /Evidence/ }));

    expect(screen.getByText(/No evidence is linked/)).toBeTruthy();
  });
});

describe('WorkItemCard completion claims', () => {
  const base: WorkItem = {
    id: 'w1',
    project_id: 'p1',
    title: 'Migrate the cascade',
    status: 'completed',
    priority: 'high',
    created_at: '2026-07-29T00:00:00Z',
    updated_at: '2026-07-29T00:00:00Z',
  };

  it('flags a completion claim that carries no evidence', () => {
    render(<WorkItemCard item={base} />);
    expect(screen.getByText(/cannot be verified here/)).toBeTruthy();
  });

  it('does not flag a completion that links evidence', () => {
    render(
      <WorkItemCard
        item={{
          ...base,
          evidence: [
            {
              id: 'e1',
              type: 'verification',
              title: 'VerifiedCompletionGate passed',
              timestamp: '2026-07-29T00:00:00Z',
              impact: 'high',
            },
          ],
        }}
      />
    );
    expect(screen.queryByText(/cannot be verified here/)).toBeNull();
  });

  it('does not flag work that has not claimed completion', () => {
    render(<WorkItemCard item={{ ...base, status: 'working' }} />);
    expect(screen.queryByText(/cannot be verified here/)).toBeNull();
  });
});

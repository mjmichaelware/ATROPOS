import { describe, expect, it, vi } from 'vitest';
import { ViewTransitionEvidence } from './view-transition-evidence';

describe('ViewTransitionEvidence', () => {
  it('preserves the update callback when transitions are unavailable', () => {
    const update = vi.fn();
    ViewTransitionEvidence.morph(update, true);
    expect(update).toHaveBeenCalledOnce();
  });
});

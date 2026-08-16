import { describe, expect, it } from 'vitest';
import { accessibilityRequirementsSatisfied } from './requirements';

describe('accessibility requirements', () => {
  it('requires every non-colour and interaction channel', () => {
    const valid = {
      states: [{ id: 'working', label: 'Working', icon: 'spinner' }],
      keyboardReachable: true,
      focusVisible: true,
      labelledControls: true,
      reducedMotionSupported: true,
    };
    expect(accessibilityRequirementsSatisfied(valid)).toBe(true);
    expect(accessibilityRequirementsSatisfied({ ...valid, focusVisible: false })).toBe(false);
    expect(accessibilityRequirementsSatisfied({ ...valid, states: [{ id: 'working' }] })).toBe(false);
  });
});

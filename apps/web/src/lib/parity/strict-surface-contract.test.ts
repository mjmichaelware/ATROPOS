import { describe, expect, it } from 'vitest';
import { CANONICAL_CONTROL_VERBS, validateControlVerbSet } from '@/components/ui/control-verbs';
import { STATUS_DEFINITIONS } from '@/lib/status-system';
import { StatusVocabulary, CrossSurfaceParity } from './cross-surface-parity';
import { DEFAULT_DISCLOSURE_LEVEL } from '@/lib/disclosure/levels';

describe('strict surface acceptance contract', () => {
  it('uses only contract-layer verbs and never exceeds thirteen', () => {
    expect(CANONICAL_CONTROL_VERBS).toHaveLength(13);
    expect(validateControlVerbSet(['inspect', 'export'])).toBe(true);
    expect(validateControlVerbSet(['unknown'])).toBe(false);
    expect(validateControlVerbSet(Array.from({ length: 14 }, () => 'inspect'))).toBe(false);
  });

  it('uses the shared disclosure default without introducing persistence', () => {
    // The shared disclosure contract is the production owner for the default
    // shown on all stream-bearing surfaces; rendering is tested separately.
    expect(DEFAULT_DISCLOSURE_LEVEL).toBe(2);
  });

  it('shares the exact status vocabulary across web and bridge surfaces', () => {
    expect(Object.keys(STATUS_DEFINITIONS).sort()).toEqual(Object.values(StatusVocabulary).sort());
    for (const status of Object.values(StatusVocabulary)) expect(CrossSurfaceParity.validateStatus(status)).toBe(true);
  });
});

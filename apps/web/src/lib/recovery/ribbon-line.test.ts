/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  STORAGE_WARN_FRACTION,
  authorityPart,
  continuityPart,
  ribbonLine,
  storagePart,
} from './ribbon-line';

const clean = {
  continuity: { repaired: false, failed: false, notice: null },
  storageFractionUsed: 0.2,
  authority: { resolved: true, source: 'AUTHORITY.md' },
};

describe('SUP.UX.RECOVERY-RIBBON carries all three concerns', () => {
  it('names continuity, free space and authority in one line', () => {
    const line = ribbonLine({ ...clean, storageFractionUsed: 0.91 });
    expect(line.parts).toHaveLength(3);
    expect(line.text).toContain('continuity');
    expect(line.text).toContain('storage');
    expect(line.text).toContain('authority');
  });

  it('stays silent when all three are clean', () => {
    expect(ribbonLine(clean).silent).toBe(true);
  });
});

describe('unknown is never folded into ok', () => {
  it('an unmeasured storage reading is unknown, not fine', () => {
    expect(storagePart(null).state).toBe('unknown');
    expect(storagePart(null).text).toMatch(/unmeasured/);
  });

  it('an unaskable engine leaves continuity unknown', () => {
    expect(continuityPart(null).state).toBe('unknown');
  });

  it('unknown authority is not treated as resolved', () => {
    expect(authorityPart(null).state).toBe('unknown');
  });

  it('a single unknown breaks the silence', () => {
    // The surface cannot vouch for something it normally would; saying nothing
    // would imply it had checked.
    const line = ribbonLine({ ...clean, storageFractionUsed: null });
    expect(line.silent).toBe(false);
    expect(line.state).toBe('unknown');
  });
});

describe('attention outranks unknown', () => {
  it('one unmeasured reading cannot mute a real warning', () => {
    const line = ribbonLine({
      continuity: { repaired: false, failed: true, notice: null },
      storageFractionUsed: null,
      authority: null,
    });
    expect(line.state).toBe('attention');
  });
});

describe('the storage part mirrors the gate threshold', () => {
  it('warns at the gate boundary rather than after the refusal', () => {
    expect(storagePart(STORAGE_WARN_FRACTION).state).toBe('attention');
    expect(storagePart(STORAGE_WARN_FRACTION - 0.01).state).toBe('ok');
  });

  it('shows the number either way', () => {
    expect(storagePart(0.42).text).toContain('42%');
    expect(storagePart(0.95).text).toContain('95%');
  });
});

describe('authority', () => {
  it('an unresolved authority document needs attention', () => {
    // Absence of a grant is never permission.
    expect(authorityPart({ resolved: false, source: null }).state).toBe('attention');
  });

  it('names the source when there is one', () => {
    expect(authorityPart({ resolved: true, source: 'AUTHORITY.md' }).text).toContain('AUTHORITY.md');
  });
});

describe('continuity', () => {
  it('a repaired start is worth reporting', () => {
    expect(continuityPart({ repaired: true, failed: false, notice: 'x' }).state).toBe('attention');
  });

  it('a failed recovery is worth reporting', () => {
    expect(continuityPart({ repaired: false, failed: true, notice: null }).text).toMatch(/did not run/);
  });
});

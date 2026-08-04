/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { CAPABILITIES, checklist, summarise, type Capability } from './competitive-checklist';
import { outstandingGaps } from './delta-register';

const claim = (overrides: Partial<Capability> = {}): Capability => ({
  id: 'c',
  title: 't',
  matters: 'm',
  state: 'shipped',
  owner: 'src/x.kt',
  surfaces: ['web'],
  ...overrides,
});

describe('a tick has to be checkable', () => {
  it('every shipped capability names an implementation', () => {
    const unowned = CAPABILITIES.filter((c) => c.state === 'shipped' && !c.owner);
    expect(unowned).toEqual([]);
  });

  it('a shipped claim with no owner is downgraded, not rendered', () => {
    const [row] = checklist([claim({ owner: null })]);
    expect(row.renderedState).toBe('claimed');
    expect(row.downgradeReason).toMatch(/names no implementation/);
  });

  it('the downgrade target is unproven, not absent', () => {
    // Reporting an existing-but-unproven capability as missing is a
    // fabrication in the other direction.
    expect(checklist([claim({ owner: null })])[0].renderedState).not.toBe('absent');
  });

  it('an owned claim is left alone', () => {
    const [row] = checklist([claim()]);
    expect(row.renderedState).toBe('shipped');
    expect(row.downgradeReason).toBeNull();
  });
});

describe('the summary counts what is rendered', () => {
  it('counts a downgraded claim as claimed', () => {
    const summary = summarise([claim(), claim({ id: 'd', owner: null })]);
    expect(summary.shipped).toBe(1);
    expect(summary.claimed).toBe(1);
    expect(summary.total).toBe(2);
  });

  it('governance is not claimed as shipped while nothing writes its ledger', () => {
    const row = checklist().find((r) => r.id === 'self-amending-governance');
    expect(row?.renderedState).toBe('claimed');
  });

  it('carries the register’s open gaps rather than recomputing them', () => {
    expect(summarise().outstandingGaps).toBe(outstandingGaps().length);
  });

  it('the shipped list is not empty', () => {
    expect(summarise().shipped).toBeGreaterThan(0);
  });
});

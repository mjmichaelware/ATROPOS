/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { formatBytes } from './system-panel';

describe('ADD-W-010 byte formatting', () => {
  it('renders human units without recomputing engine numbers', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(1536 * 1024)).toBe('1.5 MB');
    expect(formatBytes(3.5 * 1024 ** 3)).toBe('3.5 GB');
  });

  it('refuses to invent a reading for nonsense input', () => {
    expect(formatBytes(Number.NaN)).toBe('unknown');
    expect(formatBytes(-5)).toBe('unknown');
  });
});

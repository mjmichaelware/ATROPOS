/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CompletionChip, UnverifiedClaim, isCompletionTerm } from './completion-chip';

describe('ADD-W-002 completion chip', () => {
  it('accepts exactly the contract terms', () => {
    expect(isCompletionTerm('verified')).toBe(true);
    expect(isCompletionTerm('implemented')).toBe(true);
    expect(isCompletionTerm('done')).toBe(false); // the collapse this atom forbids
    expect(isCompletionTerm('completed')).toBe(false); // run vocab, not completion
  });

  it('renders the term as text beside any glyph (§E)', () => {
    render(<CompletionChip term="tested" />);
    expect(screen.getByText(/tested/)).toBeInTheDocument();
  });

  it('marks verified as the positive claim', () => {
    render(<CompletionChip term="verified" />);
    expect(screen.getByTitle('Backed by gates')).toBeInTheDocument();
  });

  it('renders an unverified claim as a warning, never as done', () => {
    render(<UnverifiedClaim />);
    expect(screen.getByText(/unverified claim/)).toBeInTheDocument();
    expect(screen.getByTitle(/No evidence backs this claim/)).toBeInTheDocument();
  });
});

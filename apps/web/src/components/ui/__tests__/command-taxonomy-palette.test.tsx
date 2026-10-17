/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { CommandTaxonomyPalette } from '../command-taxonomy-palette';

describe('CommandTaxonomyPalette (CMD-PAL-004)', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      <CommandTaxonomyPalette isOpen={false} onClose={vi.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders search input and commands when open', () => {
    render(<CommandTaxonomyPalette isOpen={true} onClose={vi.fn()} />);

    expect(
      screen.getByPlaceholderText(/Type command prefix/i)
    ).toBeInTheDocument();
    expect(screen.getByText('/repo')).toBeInTheDocument();
    expect(screen.getByText('rollback')).toBeInTheDocument();
    expect(screen.getByText('<hash>')).toBeInTheDocument();
  });

  it('filters commands when query is typed', () => {
    render(<CommandTaxonomyPalette isOpen={true} onClose={vi.fn()} />);

    const input = screen.getByPlaceholderText(/Type command prefix/i);
    fireEvent.change(input, { target: { value: 'rollback' } });

    expect(screen.getByText('/repo')).toBeInTheDocument();
    expect(screen.getByText('rollback')).toBeInTheDocument();
  });

  it('invokes onSelectCommand when command is clicked', () => {
    const onSelect = vi.fn();
    const onClose = vi.fn();
    render(
      <CommandTaxonomyPalette
        isOpen={true}
        onClose={onClose}
        onSelectCommand={onSelect}
      />
    );

    const commandEntry = screen.getByText('rollback');
    fireEvent.click(commandEntry);

    expect(onSelect).toHaveBeenCalledWith('/repo rollback <hash>');
    expect(onClose).toHaveBeenCalled();
  });

  it('toggles cheatsheet modal when help button is clicked', () => {
    render(<CommandTaxonomyPalette isOpen={true} onClose={vi.fn()} />);

    const helpBtn = screen.getByTitle('Toggle Cheatsheet');
    fireEvent.click(helpBtn);

    expect(
      screen.getByText(/4-Tier Trie Command Taxonomy Cheatsheet/i)
    ).toBeInTheDocument();
  });
});

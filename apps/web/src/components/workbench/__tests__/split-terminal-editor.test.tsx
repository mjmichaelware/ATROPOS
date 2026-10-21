/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { SplitTerminalEditor } from '../split-terminal-editor';

// Mock xterm to prevent canvas/addon DOM errors in jsdom
vi.mock('xterm', () => {
  return {
    Terminal: vi.fn().mockImplementation(() => ({
      loadAddon: vi.fn(),
      open: vi.fn(),
      write: vi.fn(),
      onData: vi.fn(),
      onResize: vi.fn(),
      dispose: vi.fn(),
    })),
  };
});

vi.mock('@xterm/addon-fit', () => {
  return {
    FitAddon: vi.fn().mockImplementation(() => ({
      fit: vi.fn(),
    })),
  };
});

vi.mock('@xterm/addon-web-links', () => {
  return {
    WebLinksAddon: vi.fn().mockImplementation(() => ({})),
  };
});

describe('SplitTerminalEditor (WB-SPLIT-003)', () => {
  it('renders workbench tabs and default code content', () => {
    render(
      <SplitTerminalEditor
        initialTabs={[
          {
            id: 'tab-1',
            filename: 'TestSpec.kt',
            content: 'class TestSpec',
            language: 'kotlin',
          },
        ]}
      />
    );

    expect(screen.getByText('TestSpec.kt')).toBeInTheDocument();
    expect(screen.getByDisplayValue('class TestSpec')).toBeInTheDocument();
    expect(screen.getByText('Workbench View')).toBeInTheDocument();
  });

  it('triggers onFileSave when Ctrl+S is pressed', () => {
    const onSave = vi.fn();
    render(
      <SplitTerminalEditor
        initialTabs={[
          {
            id: 'tab-1',
            filename: 'TestSpec.kt',
            content: 'class TestSpec',
            language: 'kotlin',
          },
        ]}
        onFileSave={onSave}
      />
    );

    const textarea = screen.getByLabelText('Code Editor Input');
    fireEvent.change(textarea, { target: { value: 'class ModifiedTestSpec' } });
    fireEvent.keyDown(textarea, { key: 's', ctrlKey: true });

    expect(onSave).toHaveBeenCalledWith('tab-1', 'class ModifiedTestSpec');
  });

  it('toggles pane focus when minimize/maximize is clicked', () => {
    render(<SplitTerminalEditor />);

    const focusBtn = screen.getByTitle('Cycle Pane Focus');
    fireEvent.click(focusBtn); // terminal only

    // Click again to cycle to editor only
    fireEvent.click(focusBtn);
    expect(screen.getByLabelText('Code Editor Input')).toBeInTheDocument();
  });
});

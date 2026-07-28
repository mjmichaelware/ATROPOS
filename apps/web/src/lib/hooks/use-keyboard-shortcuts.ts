import { useEffect } from 'react';

export interface KeyboardAction {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  handler: (event: KeyboardEvent) => void;
}

/**
 * Enable keyboard shortcuts for ATROPOS HOE.
 * Supports OpenCode-style navigation: Cmd/Ctrl+K, Cmd/Ctrl+Tab, Cmd/Ctrl+W, etc.
 */
export function useKeyboardShortcuts(shortcuts: KeyboardAction[]) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      for (const shortcut of shortcuts) {
        const keyMatch = event.key.toLowerCase() === shortcut.key.toLowerCase();
        const ctrlMatch = !shortcut.ctrlKey || event.ctrlKey || event.metaKey;
        const shiftMatch = !shortcut.shiftKey || event.shiftKey;
        const altMatch = !shortcut.altKey || event.altKey;

        if (keyMatch && ctrlMatch && shiftMatch && altMatch) {
          // Don't prevent default for special cases
          const isSystem = ['Tab', 'w', 'n', 's', 'o', 'l'].includes(event.key.toLowerCase());
          if (isSystem && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
          }
          shortcut.handler(event);
          break;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [shortcuts]);
}

/**
 * Common keyboard shortcuts for ATROPOS.
 */
export const COMMON_SHORTCUTS = {
  COMMAND_PALETTE: { key: 'k', metaKey: true } as KeyboardAction,
  NEXT_TAB: { key: 'Tab', ctrlKey: true } as KeyboardAction,
  PREV_TAB: { key: 'Tab', ctrlKey: true, shiftKey: true } as KeyboardAction,
  CLOSE_TAB: { key: 'w', ctrlKey: true } as KeyboardAction,
  SAVE: { key: 's', ctrlKey: true } as KeyboardAction,
  FIND: { key: 'f', ctrlKey: true } as KeyboardAction,
  FOCUS_SEARCH: { key: 'l', ctrlKey: true } as KeyboardAction,
} as const;

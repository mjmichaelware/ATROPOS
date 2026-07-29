'use client';

import { useEffect, useRef } from 'react';

/** A key plus the modifiers that must be held with it. */
export interface KeyBinding {
  key: string;
  /** Ctrl on Windows/Linux, Cmd on macOS — treated as the same intent. */
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
}

export interface KeyboardAction extends KeyBinding {
  handler: (event: KeyboardEvent) => void;
}

function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT'
  );
}

function matches(event: KeyboardEvent, binding: KeyBinding): boolean {
  if (event.key.toLowerCase() !== binding.key.toLowerCase()) return false;

  // Modifiers are matched exactly. Previously a binding that did not ask for
  // Ctrl still fired while Ctrl was held, so unrelated browser shortcuts
  // triggered app actions.
  const wantsPrimary = binding.ctrlKey === true;
  const hasPrimary = event.ctrlKey || event.metaKey;
  if (wantsPrimary !== hasPrimary) return false;
  if ((binding.shiftKey === true) !== event.shiftKey) return false;
  if ((binding.altKey === true) !== event.altKey) return false;

  return true;
}

/**
 * Global keyboard shortcuts (§9.2: complete operation without a mouse).
 *
 * Bindings that carry a modifier are app shortcuts and suppress the browser
 * default. Unmodified bindings are ignored while the operator is typing, so a
 * shortcut can never eat text entry.
 */
export function useKeyboardShortcuts(shortcuts: KeyboardAction[]) {
  // Held in a ref so callers do not have to memoise the array to avoid
  // rebinding the listener on every render.
  const latest = useRef(shortcuts);
  latest.current = shortcuts;

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      for (const shortcut of latest.current) {
        if (!matches(event, shortcut)) continue;

        const hasModifier =
          shortcut.ctrlKey === true || shortcut.altKey === true;
        if (!hasModifier && isEditable(event.target)) return;

        if (hasModifier) event.preventDefault();
        shortcut.handler(event);
        return;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);
}

/**
 * Canonical bindings, without handlers.
 *
 * These were previously cast to `KeyboardAction` while carrying no `handler`,
 * so passing one straight to the hook would have thrown on
 * `shortcut.handler(event)`. They are bindings; the call site supplies the
 * behaviour.
 */
export const COMMON_SHORTCUTS = {
  COMMAND_PALETTE: { key: 'k', ctrlKey: true },
  NEXT_TAB: { key: 'Tab', ctrlKey: true },
  PREV_TAB: { key: 'Tab', ctrlKey: true, shiftKey: true },
  CLOSE_TAB: { key: 'w', ctrlKey: true },
  SAVE: { key: 's', ctrlKey: true },
  FIND: { key: 'f', ctrlKey: true },
  FOCUS_SEARCH: { key: 'l', ctrlKey: true },
} as const satisfies Record<string, KeyBinding>;

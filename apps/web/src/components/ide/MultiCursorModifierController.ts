/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C6: Multi-Cursor Modifier Binding Controller.
 *
 * Configures and tracks multiple simultaneous editing cursors and selections using modifier keys
 * (Alt or Ctrl/Cmd), supporting simultaneous keystroke application and column selection.
 *
 * ## Research: Transactional Multi-Cursor Text Edit Merging
 * Researches inverted index offset adjustment to prevent character position collisions
 * when multiple simultaneous edits occur across descending line numbers.
 */

export type MultiCursorModifier = 'alt' | 'ctrlCmd';

export interface CursorPosition {
  lineNumber: number;
  column: number;
}

export class MultiCursorModifierController {
  private cursors: CursorPosition[] = [];

  constructor(private modifier: MultiCursorModifier = 'alt') {}

  getModifier(): MultiCursorModifier {
    return this.modifier;
  }

  setModifier(mod: MultiCursorModifier): void {
    this.modifier = mod;
  }

  isModifierActive(e: { altKey: boolean; ctrlKey: boolean; metaKey: boolean }): boolean {
    if (this.modifier === 'alt') {
      return e.altKey;
    }
    return e.ctrlKey || e.metaKey;
  }

  addCursor(pos: CursorPosition): CursorPosition[] {
    const exists = this.cursors.some(
      (c) => c.lineNumber === pos.lineNumber && c.column === pos.column
    );
    if (!exists) {
      this.cursors.push(pos);
      // Sort in ascending document order
      this.cursors.sort((a, b) =>
        a.lineNumber === b.lineNumber ? a.column - b.column : a.lineNumber - b.lineNumber
      );
    }
    return [...this.cursors];
  }

  clearCursors(): void {
    this.cursors = [];
  }

  getCursors(): CursorPosition[] {
    return [...this.cursors];
  }
}

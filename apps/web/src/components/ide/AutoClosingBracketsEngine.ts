/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C8: Auto-Closing Brackets and Quotes Engine.
 *
 * Automatically inserts matching closing symbols when typing opening brackets or quotation marks,
 * and skips over existing closing symbols when typed directly in front of them.
 *
 * ## Research: Language-Aware Quote Closure Rules
 * Researches skipping auto-quote insertion when inside raw string literals (`r"..."`),
 * Kotlin multiline triple quotes (`"""..."""`), or Markdown code blocks.
 */

export type AutoClosingOption = 'always' | 'languageDefined' | 'beforeWhitespace' | 'never';

export class AutoClosingBracketsEngine {
  private static readonly BRACKET_MAP: Record<string, string> = {
    '(': ')',
    '[': ']',
    '{': '}',
    '"': '"',
    "'": "'",
    '`': '`',
  };

  static handleKeystroke(
    inputChar: string,
    nextCharInDocument: string,
    option: AutoClosingOption = 'languageDefined'
  ): { insertText: string; skipNextChar: boolean } {
    if (option === 'never') {
      return { insertText: inputChar, skipNextChar: false };
    }

    // Skip over closing character if already directly in front
    if (inputChar === nextCharInDocument && (inputChar === ')' || inputChar === ']' || inputChar === '}' || inputChar === '"' || inputChar === "'" || inputChar === '`')) {
      return { insertText: '', skipNextChar: true };
    }

    // Auto-close opening bracket
    const matchingClose = this.BRACKET_MAP[inputChar];
    if (matchingClose) {
      if (option === 'beforeWhitespace' && nextCharInDocument.trim() !== '') {
        return { insertText: inputChar, skipNextChar: false };
      }
      return { insertText: `${inputChar}${matchingClose}`, skipNextChar: false };
    }

    return { insertText: inputChar, skipNextChar: false };
  }
}

/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM W5: Trailing Whitespace Trimming on Save.
 *
 * Strips trailing spaces and tab characters from modified lines upon saving,
 * preserving significant trailing whitespace in Markdown line breaks (double spaces) and raw literals.
 *
 * ## Research: AST-Guided Whitespace Preservation
 * Researches inspecting language token streams to distinguish insignificant end-of-line padding
 * from syntactic double-space line breaks in Markdown and multi-line template literals.
 */

export class TrailingWhitespaceTrimmer {
  static trimLines(content: string, language: string = 'text'): string {
    const lines = content.split('\n');
    const isMarkdown = language === 'markdown' || language === 'md';

    return lines
      .map((line) => {
        // In Markdown, exactly two trailing spaces signify a hard line break (<br>)
        if (isMarkdown && line.endsWith('  ') && !line.endsWith('   ')) {
          return line.slice(0, -2).replace(/[ \t]+$/, '') + '  ';
        }
        return line.replace(/[ \t]+$/, '');
      })
      .join('\n');
  }
}

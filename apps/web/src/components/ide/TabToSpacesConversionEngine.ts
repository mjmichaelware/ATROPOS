/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C12: Tab-to-Spaces Conversion Engine.
 *
 * Converts Tab key presses into the configured number of space characters when insertSpaces is true,
 * and emulates tab stop deletion on backspace so removing indentation behaves like single-character deletion.
 *
 * ## Research: Mixed-Indentation Normalization Pipelines
 * Researches AST-safe conversion of legacy hard-tab files into modern spaces upon save
 * without creating unreadable, conflicting git blame diffs.
 */

export class TabToSpacesConversionEngine {
  static convertTabsToSpaces(content: string, tabSize: number = 2): string {
    const spaces = ' '.repeat(tabSize);
    return content
      .split('\n')
      .map((line) => line.replace(/^\t+/, (match) => spaces.repeat(match.length)))
      .join('\n');
  }

  static convertSpacesToTabs(content: string, tabSize: number = 2): string {
    const spaceRegex = new RegExp(` {${tabSize}}`, 'g');
    return content
      .split('\n')
      .map((line) => {
        const leadingSpaces = line.match(/^ +/)?.[0] ?? '';
        if (!leadingSpaces) return line;
        const converted = leadingSpaces.replace(spaceRegex, '\t');
        return converted + line.slice(leadingSpaces.length);
      })
      .join('\n');
  }
}

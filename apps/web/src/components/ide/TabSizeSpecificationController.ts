/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C11: Indentation Tab Size Specification Controller.
 *
 * Enforces the exact number of spaces represented by a single tab indentation level,
 * with statistical indentation inference from existing buffer lines.
 *
 * ## Research: Statistical Indentation Detection Heuristics
 * Researches histogram-based delta analysis across leading whitespace lines
 * to detect whether a codebase adheres to 2-space, 4-space, or tab formatting.
 */

export class TabSizeSpecificationController {
  static detectIndentation(lines: string[]): { tabSize: number; insertSpaces: boolean } {
    let twoSpaceCount = 0;
    let fourSpaceCount = 0;
    let tabCount = 0;

    for (const line of lines.slice(0, 100)) {
      if (!line.trim() || line.startsWith('//') || line.startsWith('/*')) continue;
      const leadingSpaces = line.match(/^ +/)?.[0].length ?? 0;
      const leadingTabs = line.match(/^\t+/)?.[0].length ?? 0;

      if (leadingTabs > 0) {
        tabCount++;
      } else if (leadingSpaces > 0) {
        if (leadingSpaces % 4 === 0) fourSpaceCount++;
        else if (leadingSpaces % 2 === 0) twoSpaceCount++;
      }
    }

    if (tabCount > twoSpaceCount && tabCount > fourSpaceCount) {
      return { tabSize: 4, insertSpaces: false };
    }
    if (fourSpaceCount > twoSpaceCount) {
      return { tabSize: 4, insertSpaces: true };
    }
    return { tabSize: 2, insertSpaces: true };
  }
}

/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C9: Simultaneous Linked Editing for Tag Pairs.
 *
 * Automatically updates matching opening and closing HTML or XML tags simultaneously
 * when either tag name is modified, highlighting paired tag ranges visually.
 *
 * ## Research: Incremental XML/JSX CST Parsing
 * Researches tracking matching tag pairs using lightweight cursor-bounded regex scans
 * rather than full DOM parsing to prevent main-thread latency during fast tag renaming.
 */

export interface LinkedTagRange {
  openTagNameRange: { start: number; end: number };
  closeTagNameRange: { start: number; end: number };
  tagName: string;
}

export class LinkedTagPairEditor {
  static findLinkedTagRanges(content: string, cursorOffset: number): LinkedTagRange | null {
    // Basic JSX/HTML tag pair matcher around cursor
    const tagRegex = /<([a-zA-Z0-9_\-]+)([^>]*)>([\s\S]*?)<\/\1>/g;
    let match: RegExpExecArray | null;

    while ((match = tagRegex.exec(content)) !== null) {
      const fullStart = match.index;
      const fullEnd = match.index + match[0].length;
      if (cursorOffset >= fullStart && cursorOffset <= fullEnd) {
        const tagName = match[1];
        const openStart = fullStart + 1;
        const openEnd = openStart + tagName.length;
        const closeEnd = fullEnd - 1;
        const closeStart = closeEnd - tagName.length;

        return {
          openTagNameRange: { start: openStart, end: openEnd },
          closeTagNameRange: { start: closeStart, end: closeEnd },
          tagName,
        };
      }
    }

    return null;
  }
}

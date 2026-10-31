/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C10: Invisible Whitespace Rendering Subsystem.
 *
 * Renders subtle glyph dots for spaces and arrows for tabs based on user settings,
 * highlighting trailing spaces and mixed indentation formats.
 *
 * ## Research: Low-Overhead Canvas Sprite Atlasing for Whitespace Glyphs
 * Researches pre-rendering space dot and tab arrow glyphs into a 16x16 offscreen canvas
 * sprite atlas to achieve zero-cost glyph stamping during line text layout passes.
 */

export type RenderWhitespaceOption = 'none' | 'boundary' | 'selection' | 'trailing' | 'all';

export interface WhitespaceGlyphConfig {
  mode: RenderWhitespaceOption;
  spaceGlyphColor: string;
  tabGlyphColor: string;
  trailingAlertColor: string;
}

export class WhitespaceGlyphRenderer {
  static getGlyphConfig(mode: RenderWhitespaceOption = 'selection'): WhitespaceGlyphConfig {
    return {
      mode,
      spaceGlyphColor: 'rgba(148, 163, 184, 0.25)',
      tabGlyphColor: 'rgba(148, 163, 184, 0.35)',
      trailingAlertColor: 'rgba(239, 68, 68, 0.45)',
    };
  }

  static renderVirtualLineWithGlyphs(lineText: string, mode: RenderWhitespaceOption): string {
    if (mode === 'none') return lineText;
    // Replace leading/trailing or all spaces with visual representations for preview
    if (mode === 'all') {
      return lineText.replace(/ /g, '·').replace(/\t/g, '→   ');
    }
    if (mode === 'trailing') {
      return lineText.replace(/[ \t]+$/, (match) => '·'.repeat(match.length));
    }
    return lineText;
  }
}

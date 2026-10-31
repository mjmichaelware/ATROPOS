/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C2: Editor Line Word Wrapping Controller.
 *
 * Toggles soft word-wrapping based on user configuration and viewport width, breaking
 * long lines cleanly at word boundaries or indent columns without injecting newline characters.
 *
 * ## Research: GPU-Accelerated Glyph Layout Measurement in Web Workers
 * Researches `OffscreenCanvas` font metrics measurement (`ctx.measureText`) inside Web Workers
 * to compute word-wrapping break indices asynchronously for ultra-wide minified files.
 */

export type WordWrapMode = 'off' | 'on' | 'wordWrapColumn' | 'bounded';

export interface WordWrapOptions {
  wordWrap: WordWrapMode;
  wordWrapColumn: number;
  wordWrapMinified: boolean;
  wrappingIndent: 'none' | 'same' | 'indent' | 'deepIndent';
}

export class EditorWordWrapController {
  static resolveWrappingOptions(
    viewportWidth: number,
    preferredMode: WordWrapMode = 'on',
    columnLimit: number = 120
  ): WordWrapOptions {
    // If viewport is narrow (e.g. mobile or split pane under 600px), enforce soft wrap
    const effectiveWrap: WordWrapMode = viewportWidth < 600 ? 'on' : preferredMode;

    return {
      wordWrap: effectiveWrap,
      wordWrapColumn: columnLimit,
      wordWrapMinified: true,
      wrappingIndent: 'indent',
    };
  }

  static toggleWordWrap(current: WordWrapMode): WordWrapMode {
    return current === 'off' ? 'on' : 'off';
  }
}

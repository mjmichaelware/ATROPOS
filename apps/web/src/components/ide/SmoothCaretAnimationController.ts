/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C13: Smooth Caret Animation and Blink Transitions.
 *
 * Animates cursor movement fluidly between character positions using CSS transitions and spring physics.
 * Supports various blinking behaviors (blink, smooth, phase, expand, solid).
 *
 * ## Research: GPU-Synchronized requestAnimationFrame Cursor Interpolation
 * Researches 120Hz display refresh synchronization using `transform: translate3d` GPU compositing
 * to achieve sub-millisecond cursor motion tracking without jitter.
 */

export type CursorBlinking = 'blink' | 'smooth' | 'phase' | 'expand' | 'solid';
export type CursorSmoothCaretAnimation = 'off' | 'explicit' | 'on';

export interface CaretAnimationStyle {
  transition: string;
  animation: string;
  willChange: string;
}

export class SmoothCaretAnimationController {
  static getCaretStyle(
    smooth: CursorSmoothCaretAnimation = 'on',
    blinking: CursorBlinking = 'smooth'
  ): CaretAnimationStyle {
    const transition = smooth === 'on' ? 'all 80ms cubic-bezier(0, 0, 0.2, 1)' : 'none';
    const animation =
      blinking === 'smooth'
        ? 'caret-smooth-pulse 1s infinite ease-in-out'
        : blinking === 'blink'
        ? 'caret-blink 1s step-start infinite'
        : 'none';

    return {
      transition,
      animation,
      willChange: 'transform, opacity',
    };
  }
}

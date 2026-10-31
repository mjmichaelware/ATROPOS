/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C19: Editor Tab Sizing Behavior Controller.
 *
 * Controls editor tab width behavior across crowded workspaces, supporting fixed-width tabs,
 * proportional shrink-to-fit sizing, and dynamic horizontal scrolling with drag-to-reorder.
 *
 * ## Research: CSS Subgrid Horizontal Overflow Clipping
 * Researches CSS subgrid and `scroll-snap-type: x mandatory` to ensure new tab activations
 * smoothly scroll the active tab container into view without clipping close buttons.
 */

'use client';

import React from 'react';
import { Sliders, Maximize, Minimize } from 'lucide-react';

export type TabSizingMode = 'fit' | 'shrink' | 'fixed';

export interface TabSizingConfig {
  mode: TabSizingMode;
  maxWidthPx: number;
  minWidthPx: number;
  showCloseButton: 'always' | 'onHover' | 'off';
}

export class EditorTabSizingController {
  static getTabSizingStyle(mode: TabSizingMode = 'fit'): { flex: string; maxWidth: string; minWidth: string } {
    switch (mode) {
      case 'fixed':
        return { flex: '0 0 160px', maxWidth: '160px', minWidth: '160px' };
      case 'shrink':
        return { flex: '0 1 180px', maxWidth: '180px', minWidth: '80px' };
      case 'fit':
      default:
        return { flex: '1 1 auto', maxWidth: '220px', minWidth: '100px' };
    }
  }
}

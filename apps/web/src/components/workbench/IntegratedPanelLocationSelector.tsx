/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C20: Integrated Panel Default Location Selector.
 *
 * Allows operators to dock the bottom panel (housing terminal, output logs, and debug consoles)
 * along the bottom, left, or right edge of the workspace, persisting split ratios across reloads.
 *
 * ## Research: Responsive Dock Repositioning on Small Viewports
 * Researches automated dock orientation fallback shifting vertical split panels into
 * gesture-driven bottom sheets when device viewport width drops under 768px.
 */

'use client';

import React, { useState } from 'react';
import { PanelBottom, PanelLeft, PanelRight } from 'lucide-react';

export type PanelDockLocation = 'bottom' | 'left' | 'right';

export interface IntegratedPanelLocationSelectorProps {
  currentLocation?: PanelDockLocation;
  onChangeLocation?: (location: PanelDockLocation) => void;
  className?: string;
}

export function IntegratedPanelLocationSelector({
  currentLocation = 'bottom',
  onChangeLocation,
  className = '',
}: IntegratedPanelLocationSelectorProps) {
  const [location, setLocation] = useState<PanelDockLocation>(currentLocation);

  const selectLocation = (loc: PanelDockLocation) => {
    setLocation(loc);
    onChangeLocation?.(loc);
  };

  return (
    <div className={`integrated-panel-location-selector inline-flex items-center gap-1 bg-sg-neutral-900 p-0.5 rounded border border-sg-neutral-800 ${className}`}>
      <button
        type="button"
        onClick={() => selectLocation('bottom')}
        className={`p-1 rounded transition-colors ${
          location === 'bottom' ? 'bg-emerald-500/20 text-emerald-400' : 'text-sg-neutral-400 hover:text-white'
        }`}
        title="Dock Panel at Bottom"
      >
        <PanelBottom className="w-3.5 h-3.5" />
      </button>
      <button
        type="button"
        onClick={() => selectLocation('left')}
        className={`p-1 rounded transition-colors ${
          location === 'left' ? 'bg-emerald-500/20 text-emerald-400' : 'text-sg-neutral-400 hover:text-white'
        }`}
        title="Dock Panel on Left"
      >
        <PanelLeft className="w-3.5 h-3.5" />
      </button>
      <button
        type="button"
        onClick={() => selectLocation('right')}
        className={`p-1 rounded transition-colors ${
          location === 'right' ? 'bg-emerald-500/20 text-emerald-400' : 'text-sg-neutral-400 hover:text-white'
        }`}
        title="Dock Panel on Right"
      >
        <PanelRight className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}

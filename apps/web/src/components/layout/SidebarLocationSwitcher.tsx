/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C18: Primary Explorer Sidebar Location Switcher.
 *
 * Allows operators to position the primary Activity Bar and Explorer sidebar on either
 * the left or right side of the workspace without editor code shifting or split divider jump.
 *
 * ## Research: CSS Flexbox Inversion with Persistent Divider Coordinates
 * Researches CSS `flex-direction: row` vs `row-reverse` layout switching coupled with
 * inverted mouse drag delta computations for bidirectional split pane dividers.
 */

'use client';

import React, { useState } from 'react';
import { PanelLeft, PanelRight } from 'lucide-react';

export type SidebarLocation = 'left' | 'right';

export interface SidebarLocationSwitcherProps {
  currentLocation?: SidebarLocation;
  onChangeLocation?: (location: SidebarLocation) => void;
  className?: string;
}

export function SidebarLocationSwitcher({
  currentLocation = 'left',
  onChangeLocation,
  className = '',
}: SidebarLocationSwitcherProps) {
  const [location, setLocation] = useState<SidebarLocation>(currentLocation);

  const toggleLocation = () => {
    const next: SidebarLocation = location === 'left' ? 'right' : 'left';
    setLocation(next);
    onChangeLocation?.(next);
  };

  return (
    <button
      type="button"
      onClick={toggleLocation}
      className={`sidebar-location-switcher inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-mono rounded bg-sg-neutral-900 hover:bg-sg-neutral-800 text-sg-neutral-300 border border-sg-neutral-800 transition-colors ${className}`}
      title={`Current Sidebar Location: ${location}. Click to switch.`}
    >
      {location === 'left' ? (
        <PanelLeft className="w-3.5 h-3.5 text-emerald-400" />
      ) : (
        <PanelRight className="w-3.5 h-3.5 text-emerald-400" />
      )}
      <span>Sidebar: {location.toUpperCase()}</span>
    </button>
  );
}

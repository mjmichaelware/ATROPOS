/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM M1.1: Activity Bar and Primary View Container Switcher.
 *
 * Implements a persistent left-edge Activity Bar replicating modern IDE view switching.
 * Provides dedicated icon triggers for Explorer, Global Search, Version Control, Debug Console,
 * Extensions, and Settings with instant visual active-state indicators and keyboard accesskey support.
 *
 * ## Research: ARIA Tablist Patterns and Keyboard Navigation Accesskeys
 * Researches keyboard navigation accesskey standards and ARIA tablist patterns (`role="tablist"`,
 * `aria-selected`, `aria-controls`, and `arrowKeySelection`) for rapid keyboard-only activity bar tab switching.
 */

'use client';

import React, { useState, useCallback } from 'react';
import {
  Files,
  Search,
  GitBranch,
  Bug,
  Puzzle,
  Settings,
  LucideIcon
} from 'lucide-react';

export type ActivityBarTabId =
  | 'explorer'
  | 'search'
  | 'git'
  | 'debug'
  | 'extensions'
  | 'settings';

export interface ActivityBarTabItem {
  id: ActivityBarTabId;
  label: string;
  icon: LucideIcon;
  shortcutKey: string;
  badgeCount?: number;
}

export const ACTIVITY_BAR_TABS: ActivityBarTabItem[] = [
  { id: 'explorer', label: 'Explorer (Ctrl+Shift+E)', icon: Files, shortcutKey: 'e' },
  { id: 'search', label: 'Search (Ctrl+Shift+F)', icon: Search, shortcutKey: 'f' },
  { id: 'git', label: 'Source Control (Ctrl+Shift+G)', icon: GitBranch, shortcutKey: 'g' },
  { id: 'debug', label: 'Debug & Verify (Ctrl+Shift+D)', icon: Bug, shortcutKey: 'd' },
  { id: 'extensions', label: 'MCP & Extensions (Ctrl+Shift+X)', icon: Puzzle, shortcutKey: 'x' },
  { id: 'settings', label: 'Settings (Ctrl+,)', icon: Settings, shortcutKey: ',' },
];

export interface ActivityBarContainerSwitcherProps {
  activeTab?: ActivityBarTabId;
  onTabSelect?: (tabId: ActivityBarTabId) => void;
  className?: string;
}

export function ActivityBarContainerSwitcher({
  activeTab = 'explorer',
  onTabSelect,
  className = '',
}: ActivityBarContainerSwitcherProps) {
  const [currentTab, setCurrentTab] = useState<ActivityBarTabId>(activeTab);

  const handleSelect = useCallback(
    (tabId: ActivityBarTabId) => {
      setCurrentTab(tabId);
      onTabSelect?.(tabId);
    },
    [onTabSelect]
  );

  return (
    <nav
      className={`activity-bar-container-switcher flex flex-col justify-between w-12 bg-sg-neutral-950 border-r border-sg-neutral-800 text-sg-neutral-400 select-none py-2 ${className}`}
      role="tablist"
      aria-label="Activity Bar View Containers"
      aria-orientation="vertical"
    >
      <div className="flex flex-col gap-1 items-center">
        {ACTIVITY_BAR_TABS.slice(0, 5).map((tab) => {
          const isActive = currentTab === tab.id;
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              aria-label={tab.label}
              data-tab-id={tab.id}
              accessKey={tab.shortcutKey}
              onClick={() => handleSelect(tab.id)}
              className={`relative flex items-center justify-center w-10 h-10 rounded transition-colors ${
                isActive
                  ? 'text-emerald-400 bg-sg-neutral-900 border-l-2 border-emerald-400'
                  : 'hover:text-sg-neutral-200 hover:bg-sg-neutral-900/60'
              }`}
            >
              <Icon className="w-5 h-5" />
              {tab.badgeCount !== undefined && tab.badgeCount > 0 && (
                <span className="absolute top-1 right-1 px-1 min-w-[14px] h-[14px] text-[9px] font-bold bg-emerald-500 text-black rounded-full flex items-center justify-center">
                  {tab.badgeCount}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="flex flex-col gap-1 items-center">
        {ACTIVITY_BAR_TABS.slice(5).map((tab) => {
          const isActive = currentTab === tab.id;
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              aria-label={tab.label}
              data-tab-id={tab.id}
              accessKey={tab.shortcutKey}
              onClick={() => handleSelect(tab.id)}
              className={`relative flex items-center justify-center w-10 h-10 rounded transition-colors ${
                isActive
                  ? 'text-emerald-400 bg-sg-neutral-900 border-l-2 border-emerald-400'
                  : 'hover:text-sg-neutral-200 hover:bg-sg-neutral-900/60'
              }`}
            >
              <Icon className="w-5 h-5" />
            </button>
          );
        })}
      </div>
    </nav>
  );
}

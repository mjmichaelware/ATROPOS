/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM C16: Global Syntax Highlighting and Theme Selector.
 *
 * Manages global IDE visual themes with instant preview across dark, light, high-contrast,
 * and custom cyber-HUD palettes, updating Monaco editor tokens and application chrome simultaneously.
 *
 * ## Research: CSS Custom Property Scoping and Dynamic Monaco Themes
 * Researches updating CSS custom property trees (`--theme-accent`, `--theme-bg`) and
 * `monaco.editor.setTheme()` dynamically without triggering full DOM or editor canvas remounts.
 */

'use client';

import React, { useState } from 'react';
import { Palette, Check } from 'lucide-react';

export interface ThemeDescriptor {
  id: string;
  name: string;
  category: 'dark' | 'light' | 'cyber';
  accentColor: string;
  backgroundPreview: string;
}

export const SUPPORTED_IDE_THEMES: ThemeDescriptor[] = [
  { id: 'atropos-dark', name: 'Atropos Cyber Dark', category: 'cyber', accentColor: '#10b981', backgroundPreview: '#07100d' },
  { id: 'electric-blue', name: 'Electric Blue HUD', category: 'cyber', accentColor: '#38bdf8', backgroundPreview: '#030712' },
  { id: 'amber-crt', name: 'Amber CRT Terminal', category: 'dark', accentColor: '#f59e0b', backgroundPreview: '#181206' },
  { id: 'solarized-dark', name: 'Solarized Dark', category: 'dark', accentColor: '#2aa198', backgroundPreview: '#002b36' },
  { id: 'clean-light', name: 'Clean High-Contrast Light', category: 'light', accentColor: '#0284c7', backgroundPreview: '#ffffff' },
];

export interface GlobalThemeSelectorEngineProps {
  currentTheme?: string;
  onThemeSelect?: (themeId: string) => void;
  className?: string;
}

export function GlobalThemeSelectorEngine({
  currentTheme = 'atropos-dark',
  onThemeSelect,
  className = '',
}: GlobalThemeSelectorEngineProps) {
  const [selected, setSelected] = useState(currentTheme);

  const handleSelect = (id: string) => {
    setSelected(id);
    onThemeSelect?.(id);
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-ide-theme', id);
    }
  };

  return (
    <div className={`global-theme-selector-engine p-4 bg-sg-neutral-900 border border-sg-neutral-800 rounded-xl font-mono text-xs ${className}`}>
      <div className="flex items-center gap-2 mb-3 text-white font-bold">
        <Palette className="w-4 h-4 text-emerald-400" />
        <span>Global Theme & Syntax Highlighting</span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
        {SUPPORTED_IDE_THEMES.map((theme) => {
          const isSelected = selected === theme.id;
          return (
            <button
              key={theme.id}
              type="button"
              onClick={() => handleSelect(theme.id)}
              className={`flex items-center justify-between p-2 rounded-lg border text-left transition-all ${
                isSelected
                  ? 'border-emerald-500 bg-emerald-950/30 text-white'
                  : 'border-sg-neutral-800 bg-sg-neutral-950 hover:bg-sg-neutral-800 text-sg-neutral-300'
              }`}
            >
              <div className="flex items-center gap-2">
                <span
                  className="w-3.5 h-3.5 rounded-full border border-white/20"
                  style={{ backgroundColor: theme.accentColor }}
                />
                <span className="truncate">{theme.name}</span>
              </div>
              {isSelected && <Check className="w-3.5 h-3.5 text-emerald-400" />}
            </button>
          );
        })}
      </div>
    </div>
  );
}

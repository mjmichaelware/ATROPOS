'use client';

import { useEffect, useState } from 'react';
import { loadThemeCustomization, saveThemeCustomization, applyThemeCustomization, getAvailablePalettes } from '@atropos/design-tokens';

interface ThemeCustomizerProps {
  onClose?: () => void;
}

export function ThemeCustomizer({ onClose }: ThemeCustomizerProps) {
  const [theme, setTheme] = useState<any>(null);
  const [palettes, setPalettes] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const saved = loadThemeCustomization();
    setTheme(saved);
    setPalettes(getAvailablePalettes());
    setLoading(false);
  }, []);

  const handleThemeModeChange = (mode: 'light' | 'dark' | 'high-contrast' | 'system') => {
    const newTheme = { ...theme, mode };
    setTheme(newTheme);
    saveThemeCustomization(newTheme);
    applyThemeCustomization(newTheme);
  };

  const handleColorChange = (color: string) => {
    const newTheme = { ...theme, primaryColor: color };
    setTheme(newTheme);
    saveThemeCustomization(newTheme);
    applyThemeCustomization(newTheme);
  };

  if (loading || !theme) return null;

  return (
    <div className="theme-customizer">
      <div className="theme-customizer-header">
        <h3>Theme Settings</h3>
        {onClose && (
          <button
            className="close-btn"
            onClick={onClose}
            aria-label="Close theme customizer"
          >
            ✕
          </button>
        )}
      </div>

      <div className="theme-customizer-content">
        <div className="setting-group">
          <label className="setting-label">Theme Mode</label>
          <div className="theme-modes">
            {['light', 'dark', 'high-contrast', 'system'].map(mode => (
              <button
                key={mode}
                className={`mode-btn ${theme.mode === mode ? 'active' : ''}`}
                onClick={() => handleThemeModeChange(mode as any)}
                aria-pressed={theme.mode === mode}
              >
                {mode === 'high-contrast' ? 'High Contrast' : mode.charAt(0).toUpperCase() + mode.slice(1)}
              </button>
            ))}
          </div>
        </div>

        <div className="setting-group">
          <label className="setting-label">Primary Color</label>
          <div className="color-palettes">
            {palettes.map(palette => (
              <button
                key={palette}
                className={`color-btn ${theme.primaryColor === palette ? 'active' : ''}`}
                onClick={() => handleColorChange(palette)}
                aria-pressed={theme.primaryColor === palette}
                title={palette.charAt(0).toUpperCase() + palette.slice(1)}
              >
                <span className="color-sample" data-color={palette} />
                <span className="color-name">{palette}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <style jsx>{`
        .theme-customizer {
          display: flex;
          flex-direction: column;
          gap: var(--sg-space-3);
          padding: var(--sg-space-4);
          background: var(--sg-elevated);
          border-radius: var(--sg-radius-lg);
          border: 1px solid var(--sg-border);
        }

        .theme-customizer-header {
          display: flex;
          justify-content: space-between;
          align-items: center;

          h3 {
            margin: 0;
            font-size: var(--sg-type-sm);
            font-weight: var(--sg-weight-bold);
          }
        }

        .close-btn {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 24px;
          height: 24px;
          background: transparent;
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-sm);
          cursor: pointer;
          color: var(--sg-text-secondary);

          &:hover {
            background: var(--sg-border);
            color: var(--sg-text-primary);
          }
        }

        .theme-customizer-content {
          display: flex;
          flex-direction: column;
          gap: var(--sg-space-4);
        }

        .setting-group {
          display: flex;
          flex-direction: column;
          gap: var(--sg-space-2);
        }

        .setting-label {
          font-size: var(--sg-type-xs);
          font-weight: var(--sg-weight-medium);
          text-transform: uppercase;
          letter-spacing: 0.08em;
          color: var(--sg-text-muted);
        }

        .theme-modes {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: var(--sg-space-2);
        }

        .mode-btn {
          padding: var(--sg-space-2) var(--sg-space-3);
          background: var(--sg-surface);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-md);
          cursor: pointer;
          font-size: var(--sg-type-sm);
          color: var(--sg-text-primary);
          transition: all 0.2s;

          &:hover {
            border-color: var(--sg-accent);
            background: var(--sg-surface-hover, var(--sg-elevated));
          }

          &.active {
            background: var(--sg-accent);
            border-color: var(--sg-accent);
            color: var(--sg-text-inverse);
          }
        }

        .color-palettes {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
          gap: var(--sg-space-2);
        }

        .color-btn {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: var(--sg-space-2);
          padding: var(--sg-space-2);
          background: var(--sg-surface);
          border: 2px solid var(--sg-border);
          border-radius: var(--sg-radius-md);
          cursor: pointer;
          font-size: var(--sg-type-xs);
          color: var(--sg-text-primary);
          transition: all 0.2s;

          &:hover {
            border-color: var(--sg-accent);
          }

          &.active {
            border-color: var(--sg-accent);
            background: color-mix(in srgb, var(--sg-accent) 14%, var(--sg-surface));
          }
        }

        .color-sample {
          display: flex;
          width: 40px;
          height: 40px;
          border-radius: var(--sg-radius-md);
          background: var(--sg-red-600);

          &[data-color='blue'] {
            background: var(--sg-colors-blue-600, #2563eb);
          }

          &[data-color='green'] {
            background: var(--sg-colors-green-600, #16a34a);
          }

          &[data-color='purple'] {
            background: var(--sg-colors-purple-600, #9333ea);
          }
        }

        .color-name {
          font-weight: var(--sg-weight-medium);
          text-transform: capitalize;
        }
      `}</style>
    </div>
  );
}

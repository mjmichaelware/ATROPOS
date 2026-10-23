'use client';

import React, { useState } from 'react';
import {
  Sliders,
  Sparkles,
  Palette,
  Eye,
  Copy,
  Check,
  RotateCcw,
  Sun,
  Moon,
  Laptop
} from 'lucide-react';

export interface CyberThemeConfig {
  blurIntensity: number; // in px
  glassOpacity: number; // 0 to 1
  accentColor: string; // hex
  borderColor: string;
  fontDensity: 'compact' | 'standard' | 'relaxed';
  glowIntensity: number; // 0 to 1
}

const DEFAULT_THEME: CyberThemeConfig = {
  blurIntensity: 16,
  glassOpacity: 0.75,
  accentColor: '#00F0FF',
  borderColor: '#1f293d',
  fontDensity: 'standard',
  glowIntensity: 0.6
};

const ACCENT_PRESETS = [
  { name: 'Sovereign Cyan', hex: '#00F0FF' },
  { name: 'Neon Emerald', hex: '#00E676' },
  { name: 'Quantum Amber', hex: '#FFB800' },
  { name: 'Plasma Violet', hex: '#A855F7' },
  { name: 'Cyber Ruby', hex: '#FF1744' },
  { name: 'Hyper Cobalt', hex: '#3B82F6' }
];

export function ThemePlayground() {
  const [theme, setTheme] = useState<CyberThemeConfig>(DEFAULT_THEME);
  const [copied, setCopied] = useState<boolean>(false);

  const cssExport = `
/* Atropos Cyber-HUD Design Tokens */
:root {
  --sg-glass-blur: ${theme.blurIntensity}px;
  --sg-glass-opacity: ${theme.glassOpacity};
  --sg-accent-glow: ${theme.accentColor};
  --sg-border-dim: ${theme.borderColor};
  --sg-glow-intensity: ${theme.glowIntensity};
  --sg-font-density: ${theme.fontDensity};
}
`.trim();

  const handleCopyCss = () => {
    navigator.clipboard.writeText(cssExport);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="flex flex-col lg:flex-row h-full w-full bg-slate-950 text-slate-100 divide-y lg:divide-y-0 lg:divide-x divide-slate-800">
      {/* Left Column: Theme Controls */}
      <div className="w-full lg:w-80 flex flex-col bg-slate-900/60 p-5 space-y-6 shrink-0 overflow-y-auto">
        <div className="flex items-center gap-2">
          <Palette className="h-5 w-5 text-cyan-400" />
          <h2 className="text-sm font-mono font-bold uppercase tracking-wider text-white">
            Cyber-HUD Tokens
          </h2>
        </div>

        {/* Accent Color Palette */}
        <div className="space-y-2">
          <label className="text-[11px] font-mono text-slate-400 font-semibold uppercase">
            Neon Accent Hue
          </label>
          <div className="grid grid-cols-3 gap-2">
            {ACCENT_PRESETS.map((preset) => {
              const isSelected = theme.accentColor === preset.hex;
              return (
                <button
                  key={preset.hex}
                  onClick={() => setTheme({ ...theme, accentColor: preset.hex })}
                  className={`p-2 rounded-xl border flex flex-col items-center gap-1.5 transition-all ${
                    isSelected
                      ? 'border-white bg-slate-800 shadow-md ring-1 ring-white/50'
                      : 'border-slate-800 bg-slate-900/80 hover:bg-slate-800'
                  }`}
                >
                  <div
                    className="w-5 h-5 rounded-full shadow-lg"
                    style={{ backgroundColor: preset.hex, boxShadow: `0 0 10px ${preset.hex}` }}
                  />
                  <span className="text-[9px] font-mono text-slate-300 text-center leading-tight">
                    {preset.name}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Frosted Glass Blur Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs font-mono text-slate-400">
            <span>Glass Blur Radius</span>
            <span className="text-cyan-400 font-bold">{theme.blurIntensity}px</span>
          </div>
          <input
            type="range"
            min="0"
            max="40"
            value={theme.blurIntensity}
            onChange={(e) => setTheme({ ...theme, blurIntensity: Number(e.target.value) })}
            className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
        </div>

        {/* Glass Surface Opacity Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs font-mono text-slate-400">
            <span>Surface Opacity</span>
            <span className="text-cyan-400 font-bold">{Math.round(theme.glassOpacity * 100)}%</span>
          </div>
          <input
            type="range"
            min="0.1"
            max="1.0"
            step="0.05"
            value={theme.glassOpacity}
            onChange={(e) => setTheme({ ...theme, glassOpacity: Number(e.target.value) })}
            className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
        </div>

        {/* Glow Intensity */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs font-mono text-slate-400">
            <span>Neon Glow Intensity</span>
            <span className="text-cyan-400 font-bold">{Math.round(theme.glowIntensity * 100)}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="1"
            step="0.1"
            value={theme.glowIntensity}
            onChange={(e) => setTheme({ ...theme, glowIntensity: Number(e.target.value) })}
            className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
        </div>

        {/* Typography Density */}
        <div className="space-y-1.5">
          <label className="text-[11px] font-mono text-slate-400 font-semibold uppercase">
            Typography Density
          </label>
          <div className="grid grid-cols-3 gap-1">
            {(['compact', 'standard', 'relaxed'] as const).map((density) => (
              <button
                key={density}
                onClick={() => setTheme({ ...theme, fontDensity: density })}
                className={`py-1.5 text-[11px] font-mono rounded border capitalize transition-colors ${
                  theme.fontDensity === density
                    ? 'border-cyan-400 bg-cyan-500/10 text-cyan-300 font-bold'
                    : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'
                }`}
              >
                {density}
              </button>
            ))}
          </div>
        </div>

        {/* Reset & Export Actions */}
        <div className="space-y-2 pt-2 border-t border-slate-800">
          <button
            onClick={handleCopyCss}
            className="w-full flex items-center justify-center gap-1.5 py-2 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-black font-bold text-xs font-mono transition-colors shadow-lg shadow-cyan-500/20"
          >
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            <span>{copied ? 'CSS Copied!' : 'Export CSS Variables'}</span>
          </button>
          <button
            onClick={() => setTheme(DEFAULT_THEME)}
            className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-white text-xs font-mono transition-colors"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            <span>Reset Defaults</span>
          </button>
        </div>
      </div>

      {/* Right Column: Live Styled Preview Canvas */}
      <div className="flex-1 flex flex-col p-8 overflow-y-auto bg-slate-950">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div>
            <h1 className="text-lg font-bold font-mono text-white">Live Design System Canvas</h1>
            <p className="text-xs text-slate-400 mt-0.5">
              Testing amorphic glass, telemetry cards, and buttons with active theme values.
            </p>
          </div>
        </div>

        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-6 max-w-4xl">
          {/* Card 1: Acrylic Glass Surface */}
          <div
            style={{
              backdropFilter: `blur(${theme.blurIntensity}px)`,
              backgroundColor: `rgba(15, 23, 42, ${theme.glassOpacity})`,
              borderColor: theme.borderColor,
              boxShadow: `0 0 ${20 * theme.glowIntensity}px ${theme.accentColor}20`
            }}
            className="p-6 rounded-2xl border transition-all space-y-4"
          >
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-sm text-white flex items-center gap-2">
                <Sparkles className="h-4 w-4" style={{ color: theme.accentColor }} />
                Acrylic Glass Card
              </span>
              <span
                style={{
                  color: theme.accentColor,
                  borderColor: `${theme.accentColor}40`,
                  backgroundColor: `${theme.accentColor}10`
                }}
                className="text-[10px] font-mono px-2 py-0.5 rounded border font-bold"
              >
                TELEMETRY
              </span>
            </div>
            <p className="text-xs text-slate-300 font-sans leading-relaxed">
              Real-time glass card with customizable blur radius and ambient glow back-channel.
            </p>
            <div className="flex gap-2 pt-2">
              <button
                style={{
                  backgroundColor: theme.accentColor,
                  color: '#000000',
                  boxShadow: `0 0 15px ${theme.accentColor}40`
                }}
                className="px-4 py-1.5 rounded-lg text-xs font-mono font-bold transition-transform active:scale-95"
              >
                Primary Trigger
              </button>
              <button className="px-4 py-1.5 rounded-lg border border-slate-700 bg-slate-900 text-xs font-mono text-slate-300 hover:text-white">
                Secondary
              </button>
            </div>
          </div>

          {/* Card 2: Interactive Terminal Preview */}
          <div
            style={{
              backdropFilter: `blur(${theme.blurIntensity}px)`,
              backgroundColor: `rgba(10, 15, 30, ${theme.glassOpacity})`,
              borderColor: theme.borderColor
            }}
            className="p-6 rounded-2xl border transition-all space-y-3 font-mono text-xs"
          >
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <span className="text-slate-400 text-[11px]">CONSOLE PREVIEW</span>
              <span style={{ color: theme.accentColor }} className="text-[10px] font-bold">
                CONNECTED :4317
              </span>
            </div>
            <div className="space-y-1.5 text-[11px]">
              <p className="text-slate-500">$ atropos --version</p>
              <p className="text-white">ATROPOS 2.0 Sovereign (commit 3ea114b2)</p>
              <p className="text-slate-500">$ atropos /providers</p>
              <p style={{ color: theme.accentColor }}>[OK] 6 providers online · Latency 14ms</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

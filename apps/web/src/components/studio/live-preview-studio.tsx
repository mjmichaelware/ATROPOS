'use client';

import { useState, useRef, useEffect } from 'react';
import {
  Smartphone,
  Tablet,
  Monitor,
  Maximize2,
  RotateCw,
  RefreshCw,
  ExternalLink,
  Sliders,
  Code2,
  Terminal,
  Layers,
  ChevronDown,
  ChevronUp,
  Activity,
  CheckCircle2,
  AlertTriangle,
  Palette,
  GitBranch,
  Box
} from 'lucide-react';
import { ComponentSandbox } from './component-sandbox';
import { TelemetryInspector } from './telemetry-inspector';
import { ThemePlayground } from './theme-playground';
import { SpecGraphLiveBridge } from './specgraph-live-bridge';
import { DeviceFrameContainer, DevicePreset } from './device-frames';

export type DeviceViewport = 'mobile' | 'tablet' | 'desktop' | 'fluid';
export type Orientation = 'portrait' | 'landscape';
export type StudioTabMode = 'emulator' | 'sandbox' | 'telemetry' | 'theme' | 'specgraph';

interface LivePreviewStudioProps {
  initialUrl?: string;
  onOpenCodeEditor?: () => void;
}

/**
 * Sovereign Workstation & Live Preview Studio.
 *
 * Supports:
 * - Multi-viewport hardware emulation frames (iPhone 16 Pro, iPad Pro, Desktop)
 * - Component sandbox for direct live prop manipulation
 * - Real-time SSE / WebSocket telemetry event stream
 * - Cyber-HUD design token playground with real-time acrylic glass blur
 * - SpecGraph Foundry DAG optimization & Vercel production bridge
 */
export function LivePreviewStudio({ initialUrl = '/', onOpenCodeEditor }: LivePreviewStudioProps) {
  const [activeMode, setActiveMode] = useState<StudioTabMode>('emulator');
  const [viewport, setViewport] = useState<DeviceViewport>('mobile');
  const [activeDevice, setActiveDevice] = useState<DevicePreset>('iphone-16-pro');
  const [orientation, setOrientation] = useState<Orientation>('portrait');
  const [scale, setScale] = useState<number>(100);
  const [previewPath, setPreviewPath] = useState(initialUrl);
  const [isConsoleOpen, setIsConsoleOpen] = useState(false);
  const [consoleLogs, setConsoleLogs] = useState<Array<{ type: 'log' | 'warn' | 'error'; message: string; timestamp: string }>>([
    { type: 'log', message: 'Sovereign Live Preview Frame mounted in sandbox.', timestamp: '00:00:01' },
    { type: 'log', message: 'Cyber-HUD CSS tokens and Glassmorphism initialized.', timestamp: '00:00:02' },
    { type: 'log', message: 'Responsive canvas listener bound to resize events.', timestamp: '00:00:03' }
  ]);

  const iframeRef = useRef<HTMLIFrameElement>(null);

  const reloadFrame = () => {
    if (iframeRef.current) {
      iframeRef.current.src = previewPath;
      addLog('Frame manually reloaded.', 'log');
    }
  };

  const addLog = (message: string, type: 'log' | 'warn' | 'error' = 'log') => {
    const timestamp = new Date().toTimeString().split(' ')[0];
    setConsoleLogs((prev) => [...prev.slice(-30), { type, message, timestamp }]);
  };

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 text-slate-100 rounded-2xl border border-slate-800 overflow-hidden shadow-2xl">
      {/* Studio Master Header Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 bg-slate-900 border-b border-slate-800">
        {/* Left: Studio Mode Switcher */}
        <div className="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800 font-mono text-xs">
          <button
            onClick={() => setActiveMode('emulator')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              activeMode === 'emulator'
                ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Smartphone className="h-3.5 w-3.5" />
            <span>Emulator</span>
          </button>
          <button
            onClick={() => setActiveMode('sandbox')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              activeMode === 'sandbox'
                ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Box className="h-3.5 w-3.5" />
            <span>Sandbox</span>
          </button>
          <button
            onClick={() => setActiveMode('telemetry')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              activeMode === 'telemetry'
                ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Activity className="h-3.5 w-3.5" />
            <span>Telemetry</span>
          </button>
          <button
            onClick={() => setActiveMode('theme')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              activeMode === 'theme'
                ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/40'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Palette className="h-3.5 w-3.5" />
            <span>Theme HUD</span>
          </button>
          <button
            onClick={() => setActiveMode('specgraph')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              activeMode === 'specgraph'
                ? 'bg-purple-500/20 text-purple-300 font-bold border border-purple-500/40'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <GitBranch className="h-3.5 w-3.5" />
            <span>SpecGraph</span>
          </button>
        </div>

        {/* Center/Right: Mode-Specific Actions */}
        {activeMode === 'emulator' && (
          <div className="flex items-center gap-3 font-mono text-xs">
            {/* Target Path Selector */}
            <select
              value={previewPath}
              onChange={(e) => {
                setPreviewPath(e.target.value);
                addLog(`Navigating preview to: ${e.target.value}`, 'log');
              }}
              className="px-2.5 py-1.5 text-xs font-mono rounded-lg bg-slate-900 border border-slate-700 text-cyan-400 focus:outline-none focus:border-cyan-400"
            >
              <option value="/">Home Dashboard</option>
              <option value="/projects">Projects Workspace</option>
              <option value="/work">Workstream Console</option>
              <option value="/files">Workspace Files</option>
              <option value="/settings">Settings Matrix</option>
            </select>

            {/* Device Switcher */}
            <div className="flex items-center gap-1 bg-slate-950 p-1 rounded-lg border border-slate-800">
              <button
                onClick={() => {
                  setActiveDevice('iphone-16-pro');
                  setViewport('mobile');
                }}
                title="iPhone 16 Pro"
                className={`p-1.5 rounded transition-colors ${
                  activeDevice === 'iphone-16-pro' ? 'bg-cyan-400 text-black font-bold' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Smartphone className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => {
                  setActiveDevice('ipad-pro-11');
                  setViewport('tablet');
                }}
                title="iPad Pro"
                className={`p-1.5 rounded transition-colors ${
                  activeDevice === 'ipad-pro-11' ? 'bg-cyan-400 text-black font-bold' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Tablet className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={() => {
                  setActiveDevice('desktop-ultrawide');
                  setViewport('desktop');
                }}
                title="Desktop Canvas"
                className={`p-1.5 rounded transition-colors ${
                  activeDevice === 'desktop-ultrawide' ? 'bg-cyan-400 text-black font-bold' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Monitor className="h-3.5 w-3.5" />
              </button>
            </div>

            {/* Rotate & Scale */}
            <button
              onClick={() => setOrientation((prev) => (prev === 'portrait' ? 'landscape' : 'portrait'))}
              title="Rotate Device"
              className="p-1.5 rounded-lg border border-slate-700 bg-slate-800 text-slate-300 hover:text-white transition-colors"
            >
              <RotateCw className="h-3.5 w-3.5" />
            </button>

            <div className="flex items-center gap-1 text-[11px] font-mono bg-slate-950 px-2 py-1 rounded-lg border border-slate-800">
              <button onClick={() => setScale((s) => Math.max(50, s - 25))} className="text-slate-400 hover:text-white">
                -
              </button>
              <span className="text-cyan-400 w-10 text-center">{scale}%</span>
              <button onClick={() => setScale((s) => Math.min(150, s + 25))} className="text-slate-400 hover:text-white">
                +
              </button>
            </div>

            <button
              onClick={reloadFrame}
              title="Reload Preview Frame"
              className="p-1.5 rounded-lg border border-slate-700 bg-slate-800 text-slate-300 hover:text-cyan-400 transition-colors"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </button>
          </div>
        )}

        {/* SpecGraph External Cross-Link */}
        <a
          href="https://specgraph-foundry.vercel.app"
          target="_blank"
          rel="noopener noreferrer"
          title="Open SpecGraph Foundry Vercel Production"
          className="flex items-center gap-1 px-3 py-1.5 text-xs font-mono font-bold rounded-lg bg-purple-500/20 border border-purple-500/40 text-purple-300 hover:bg-purple-500/30 transition-colors hidden sm:flex"
        >
          <span>SpecGraph Vercel ↗</span>
        </a>
      </div>

      {/* Main Studio Viewport Area */}
      <div className="relative flex-1 overflow-hidden flex flex-col bg-slate-950">
        {activeMode === 'emulator' && (
          <div className="relative flex-1 overflow-auto flex items-center justify-center p-8 bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:16px_16px]">
            <DeviceFrameContainer
              device={activeDevice}
              orientation={orientation}
              scale={scale}
            >
              <iframe
                ref={iframeRef}
                src={previewPath}
                title="Atropos Sovereign Live Preview Sandbox"
                className="w-full h-full border-0 bg-slate-950"
                onLoad={() => addLog(`Frame rendered: ${previewPath}`, 'log')}
              />
            </DeviceFrameContainer>
          </div>
        )}

        {activeMode === 'sandbox' && <ComponentSandbox />}
        {activeMode === 'telemetry' && <TelemetryInspector />}
        {activeMode === 'theme' && <ThemePlayground />}
        {activeMode === 'specgraph' && <SpecGraphLiveBridge />}
      </div>

      {/* Bottom Collapsible Console & Event Stream */}
      {activeMode === 'emulator' && (
        <div className="border-t border-slate-800 bg-slate-900">
          <button
            onClick={() => setIsConsoleOpen((prev) => !prev)}
            className="w-full flex items-center justify-between px-5 py-2 text-xs font-mono text-slate-400 hover:text-white transition-colors"
          >
            <div className="flex items-center gap-2">
              <Terminal className="h-3.5 w-3.5 text-cyan-400" />
              <span className="font-bold text-white">STUDIO EVENT STREAM</span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 border border-slate-700 text-cyan-400">
                {consoleLogs.length} events
              </span>
            </div>
            {isConsoleOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
          </button>

          {isConsoleOpen && (
            <div className="p-3 bg-slate-950 max-h-48 overflow-y-auto font-mono text-xs space-y-1.5 border-t border-slate-800">
              {consoleLogs.map((log, idx) => (
                <div key={idx} className="flex items-start gap-2">
                  <span className="text-slate-500 text-[10px] w-14 shrink-0">{log.timestamp}</span>
                  <span
                    className={`text-[11px] ${
                      log.type === 'error'
                        ? 'text-rose-400'
                        : log.type === 'warn'
                        ? 'text-amber-400'
                        : 'text-slate-300'
                    }`}
                  >
                    {log.message}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}


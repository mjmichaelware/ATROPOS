/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useRef } from 'react';
import { RotateCw, ExternalLink, Smartphone, Monitor, Tablet, Globe } from 'lucide-react';

/**
 * ATOM CC9: INTEGRATED BROWSER PREVIEW WORKSPACE TAB
 * Path: apps/web/src/components/studio/IntegratedBrowserPreviewTab.tsx
 *
 * Renders a lightweight, sandboxed browser viewport directly inside the IDE to display
 * running local web applications with hot-reload refreshes, responsive viewport resizing,
 * and console log capture.
 */
export type DeviceFrameType = 'responsive' | 'mobile' | 'tablet' | 'desktop';

export interface IntegratedBrowserPreviewProps {
  initialUrl?: string;
  onUrlChange?: (url: string) => void;
  className?: string;
}

export const IntegratedBrowserPreviewTab: React.FC<IntegratedBrowserPreviewProps> = ({
  initialUrl = 'http://localhost:3000',
  onUrlChange,
  className = '',
}) => {
  const [url, setUrl] = useState(initialUrl);
  const [inputUrl, setInputUrl] = useState(initialUrl);
  const [deviceFrame, setDeviceFrame] = useState<DeviceFrameType>('responsive');
  const [reloadKey, setReloadKey] = useState(0);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const handleNavigate = (e: React.FormEvent) => {
    e.preventDefault();
    setUrl(inputUrl);
    onUrlChange?.(inputUrl);
  };

  const handleHotReload = () => {
    setReloadKey((prev) => prev + 1);
  };

  const frameWidthClass = {
    responsive: 'w-full h-full',
    mobile: 'w-[375px] h-[667px] border-8 border-slate-700 rounded-3xl shadow-2xl',
    tablet: 'w-[768px] h-[1024px] border-8 border-slate-700 rounded-2xl shadow-2xl',
    desktop: 'w-[1280px] h-[800px] border-4 border-slate-700 rounded-lg shadow-xl',
  }[deviceFrame];

  return (
    <div className={`flex flex-col h-full bg-slate-950 text-slate-100 ${className}`}>
      {/* Navigation and Viewport Control Bar */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-slate-900 border-b border-slate-800 gap-2">
        <div className="flex items-center gap-1.5">
          <button
            onClick={handleHotReload}
            className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200 transition-colors"
            title="Hot Reload Preview"
            aria-label="Reload Preview"
          >
            <RotateCw className="w-4 h-4" />
          </button>
        </div>

        {/* Address Bar */}
        <form onSubmit={handleNavigate} className="flex-1 max-w-xl">
          <div className="flex items-center bg-slate-950 border border-slate-800 rounded px-2 py-0.5 text-xs text-slate-300 focus-within:border-cyan-500">
            <Globe className="w-3.5 h-3.5 mr-1.5 text-slate-500 shrink-0" />
            <input
              type="text"
              value={inputUrl}
              onChange={(e) => setInputUrl(e.target.value)}
              className="w-full bg-transparent outline-none font-mono text-[11px]"
              placeholder="http://localhost:3000"
            />
          </div>
        </form>

        {/* Device Mode Switchers */}
        <div className="flex items-center gap-1">
          <button
            onClick={() => setDeviceFrame('responsive')}
            className={`p-1 rounded ${deviceFrame === 'responsive' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400 hover:text-slate-200'}`}
            title="Responsive Fluid"
          >
            <Monitor className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setDeviceFrame('mobile')}
            className={`p-1 rounded ${deviceFrame === 'mobile' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400 hover:text-slate-200'}`}
            title="Mobile (375x667)"
          >
            <Smartphone className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setDeviceFrame('tablet')}
            className={`p-1 rounded ${deviceFrame === 'tablet' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400 hover:text-slate-200'}`}
            title="Tablet (768x1024)"
          >
            <Tablet className="w-3.5 h-3.5" />
          </button>
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200"
            title="Open in Native Browser Tab"
          >
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>

      {/* Frame Container */}
      <div className="flex-1 overflow-auto flex items-center justify-center p-2 bg-slate-900/40">
        <iframe
          key={reloadKey}
          ref={iframeRef}
          src={url}
          className={`bg-white transition-all ${frameWidthClass}`}
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
          title="Atropos In-IDE Preview"
        />
      </div>
    </div>
  );
};

'use client';

import React, { ReactNode } from 'react';
import {
  Wifi,
  Battery,
  Signal,
  RotateCw,
  Maximize2,
  Smartphone,
  Tablet,
  Monitor
} from 'lucide-react';

export type DevicePreset =
  | 'iphone-16-pro'
  | 'iphone-16-pro-max'
  | 'pixel-9-pro'
  | 'ipad-pro-11'
  | 'ipad-pro-13'
  | 'macbook-pro-16'
  | 'desktop-ultrawide';

export interface DeviceSpec {
  id: DevicePreset;
  name: string;
  category: 'mobile' | 'tablet' | 'desktop';
  width: number;
  height: number;
  cornerRadius: number;
  bezelWidth: number;
  hasDynamicIsland?: boolean;
  hasNotch?: boolean;
  hasPunchHole?: boolean;
  hasHomeIndicator?: boolean;
  aspectRatio: string;
}

export const DEVICE_PRESETS: Record<DevicePreset, DeviceSpec> = {
  'iphone-16-pro': {
    id: 'iphone-16-pro',
    name: 'iPhone 16 Pro',
    category: 'mobile',
    width: 393,
    height: 852,
    cornerRadius: 54,
    bezelWidth: 8,
    hasDynamicIsland: true,
    hasHomeIndicator: true,
    aspectRatio: '9:19.5'
  },
  'iphone-16-pro-max': {
    id: 'iphone-16-pro-max',
    name: 'iPhone 16 Pro Max',
    category: 'mobile',
    width: 430,
    height: 932,
    cornerRadius: 56,
    bezelWidth: 8,
    hasDynamicIsland: true,
    hasHomeIndicator: true,
    aspectRatio: '9:19.5'
  },
  'pixel-9-pro': {
    id: 'pixel-9-pro',
    name: 'Google Pixel 9 Pro',
    category: 'mobile',
    width: 412,
    height: 924,
    cornerRadius: 48,
    bezelWidth: 7,
    hasPunchHole: true,
    hasHomeIndicator: true,
    aspectRatio: '9:20'
  },
  'ipad-pro-11': {
    id: 'ipad-pro-11',
    name: 'iPad Pro 11-inch',
    category: 'tablet',
    width: 834,
    height: 1194,
    cornerRadius: 32,
    bezelWidth: 12,
    hasHomeIndicator: true,
    aspectRatio: '3:4.3'
  },
  'ipad-pro-13': {
    id: 'ipad-pro-13',
    name: 'iPad Pro 13-inch',
    category: 'tablet',
    width: 1024,
    height: 1366,
    cornerRadius: 34,
    bezelWidth: 14,
    hasHomeIndicator: true,
    aspectRatio: '3:4'
  },
  'macbook-pro-16': {
    id: 'macbook-pro-16',
    name: 'MacBook Pro 16-inch',
    category: 'desktop',
    width: 1280,
    height: 832,
    cornerRadius: 16,
    bezelWidth: 6,
    hasNotch: true,
    aspectRatio: '16:10'
  },
  'desktop-ultrawide': {
    id: 'desktop-ultrawide',
    name: 'Studio Display Ultrawide',
    category: 'desktop',
    width: 1440,
    height: 900,
    cornerRadius: 12,
    bezelWidth: 4,
    aspectRatio: '16:10'
  }
};

interface DeviceFrameContainerProps {
  device: DevicePreset;
  orientation: 'portrait' | 'landscape';
  scale: number;
  children: ReactNode;
  currentTime?: string;
  batteryPercent?: number;
}

/**
 * Pixel-Perfect Sovereign Hardware Emulation Frame.
 *
 * Renders hardware bezels, titanium outer bands, dynamic island,
 * status bars with network/battery, and gesture indicators.
 */
export function DeviceFrameContainer({
  device,
  orientation,
  scale,
  children,
  currentTime = '09:41',
  batteryPercent = 98
}: DeviceFrameContainerProps) {
  const spec = DEVICE_PRESETS[device];
  const isLandscape = orientation === 'landscape';

  const frameWidth = isLandscape ? spec.height : spec.width;
  const frameHeight = isLandscape ? spec.width : spec.height;

  return (
    <div
      style={{
        width: `${frameWidth}px`,
        height: `${frameHeight}px`,
        transform: `scale(${scale / 100})`,
        transformOrigin: 'center center',
        transition: 'width 0.35s cubic-bezier(0.16, 1, 0.3, 1), height 0.35s cubic-bezier(0.16, 1, 0.3, 1), transform 0.2s ease-out',
        borderRadius: `${spec.cornerRadius}px`,
        borderWidth: `${spec.bezelWidth}px`,
        borderColor: '#1e293b'
      }}
      className="relative flex flex-col overflow-hidden bg-black shadow-[0_25px_60px_-15px_rgba(0,0,0,0.8)] border border-slate-700/60 transition-shadow duration-300"
    >
      {/* Outer Titanium Trim Chamfer */}
      <div
        className="absolute inset-0 pointer-events-none rounded-[inherit] border border-cyan-500/20 shadow-inner z-40"
      />

      {/* Hardware Status Bar (Mobile / Tablet) */}
      {spec.category !== 'desktop' && (
        <div
          className={`relative w-full z-30 flex items-center justify-between px-6 pt-2 pb-1 text-[11px] font-mono tracking-tight text-white select-none ${
            isLandscape ? 'h-6' : 'h-10'
          }`}
        >
          {/* Left: Time & Location Indicator */}
          <div className="flex items-center gap-1 font-semibold pl-1">
            <span>{currentTime}</span>
          </div>

          {/* Center: Dynamic Island or Notch */}
          {spec.hasDynamicIsland && !isLandscape && (
            <div className="absolute left-1/2 top-2.5 -translate-x-1/2 w-28 h-7 bg-black rounded-full flex items-center justify-between px-3 z-50 shadow-md border border-neutral-900">
              <div className="w-2.5 h-2.5 rounded-full bg-neutral-900 border border-neutral-800" />
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                <div className="w-2.5 h-2.5 rounded-full bg-neutral-900 border border-neutral-800" />
              </div>
            </div>
          )}

          {spec.hasPunchHole && !isLandscape && (
            <div className="absolute left-1/2 top-2 -translate-x-1/2 w-3.5 h-3.5 bg-black rounded-full z-50 border border-neutral-800" />
          )}

          {/* Right: Telemetry Badges (Signal, Wi-Fi, Battery) */}
          <div className="flex items-center gap-2 pr-1">
            <Signal className="w-3.5 h-3.5 text-white" />
            <Wifi className="w-3.5 h-3.5 text-white" />
            <div className="flex items-center gap-1">
              <span className="text-[10px] text-neutral-300">{batteryPercent}%</span>
              <Battery className="w-4 h-4 text-white" />
            </div>
          </div>
        </div>
      )}

      {/* Screen Viewport Content Area */}
      <div className="relative flex-1 w-full h-full overflow-hidden bg-slate-950 flex flex-col">
        {children}
      </div>

      {/* Mobile Gesture Bar (Home Indicator) */}
      {spec.hasHomeIndicator && (
        <div className="relative w-full h-5 flex items-center justify-center pointer-events-none z-30 bg-transparent">
          <div className="w-32 h-1 bg-white/40 rounded-full" />
        </div>
      )}
    </div>
  );
}

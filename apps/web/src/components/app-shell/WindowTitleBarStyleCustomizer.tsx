'use client';

import React, { useState, useEffect } from 'react';

/**
 * ATOM W15: TITLE BAR PRESENTATION STYLE SWITCHER
 * Path: apps/web/src/components/app-shell/WindowTitleBarStyleCustomizer.tsx
 *
 * Provides switching between a custom electron/web title bar integrated with menu items
 * and breadcrumbs versus the OS-native window frame. Synchronizes window control buttons.
 */

export type TitleBarStyle = 'custom' | 'native';

export interface WindowTitleBarStyleCustomizerProps {
  initialStyle?: TitleBarStyle;
  onStyleChange?: (style: TitleBarStyle) => void;
  title?: string;
  children?: React.ReactNode;
}

export class WindowStyleManager {
  private static STORAGE_KEY = 'atropos.window.titleBarStyle';

  public static getStoredStyle(): TitleBarStyle {
    if (typeof window === 'undefined') return 'custom';
    try {
      const stored = window.localStorage.getItem(this.STORAGE_KEY);
      return stored === 'native' ? 'native' : 'custom';
    } catch {
      return 'custom';
    }
  }

  public static persistStyle(style: TitleBarStyle): void {
    if (typeof window === 'undefined') return;
    try {
      window.localStorage.setItem(this.STORAGE_KEY, style);
      if (style === 'custom') {
        document.documentElement.classList.add('custom-titlebar');
        document.documentElement.classList.remove('native-titlebar');
      } else {
        document.documentElement.classList.add('native-titlebar');
        document.documentElement.classList.remove('custom-titlebar');
      }
    } catch {
      // Ignore storage errors in restricted contexts
    }
  }

  public static isElectronContext(): boolean {
    if (typeof window === 'undefined') return false;
    return typeof (window as unknown as { electron?: unknown }).electron !== 'undefined';
  }
}

export const WindowTitleBarStyleCustomizer: React.FC<WindowTitleBarStyleCustomizerProps> = ({
  initialStyle = 'custom',
  onStyleChange,
  title = 'ATROPOS',
  children,
}) => {
  const [style, setStyle] = useState<TitleBarStyle>(initialStyle);

  useEffect(() => {
    const stored = WindowStyleManager.getStoredStyle();
    setStyle(stored);
    WindowStyleManager.persistStyle(stored);
  }, []);

  const handleToggleStyle = (newStyle: TitleBarStyle) => {
    setStyle(newStyle);
    WindowStyleManager.persistStyle(newStyle);
    onStyleChange?.(newStyle);
  };

  const handleMinimize = () => {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('atropos:window:minimize'));
    }
  };

  const handleMaximize = () => {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('atropos:window:maximize'));
    }
  };

  const handleClose = () => {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('atropos:window:close'));
    }
  };

  if (style === 'native') {
    return (
      <header className="w-full flex items-center justify-between px-3 py-1 bg-background border-b border-border/40 text-xs select-none">
        <div className="flex items-center gap-2">{children}</div>
        <div className="font-semibold text-muted-foreground">{title}</div>
        <div className="w-12 text-right">
          <button
            onClick={() => handleToggleStyle('custom')}
            className="text-[10px] px-1.5 py-0.5 rounded border border-border/50 hover:bg-muted"
            title="Switch to Custom Title Bar"
          >
            Custom UI
          </button>
        </div>
      </header>
    );
  }

  return (
    <header className="w-full flex items-center justify-between px-2 h-8 bg-background border-b border-border select-none app-drag-region">
      <div className="flex items-center gap-2 no-drag">
        <span className="font-bold text-xs tracking-wider text-primary">{title}</span>
        {children}
      </div>

      <div className="flex items-center gap-1 no-drag">
        <button
          onClick={() => handleToggleStyle('native')}
          className="text-[10px] px-1.5 py-0.5 rounded border border-border/40 hover:bg-muted mr-2"
          title="Switch to OS Native Title Bar"
        >
          OS Frame
        </button>
        <button
          onClick={handleMinimize}
          className="w-6 h-6 flex items-center justify-center hover:bg-muted rounded text-muted-foreground hover:text-foreground"
          aria-label="Minimize Window"
        >
          &minus;
        </button>
        <button
          onClick={handleMaximize}
          className="w-6 h-6 flex items-center justify-center hover:bg-muted rounded text-muted-foreground hover:text-foreground text-[10px]"
          aria-label="Maximize Window"
        >
          &#9634;
        </button>
        <button
          onClick={handleClose}
          className="w-6 h-6 flex items-center justify-center hover:bg-destructive/80 hover:text-destructive-foreground rounded text-muted-foreground"
          aria-label="Close Window"
        >
          &times;
        </button>
      </div>
    </header>
  );
};

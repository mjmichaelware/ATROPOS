/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

/**
 * ADD-W-004: Independent Surface Verbosity Channel & Local Disclosure State
 * Section 1.3 mandate: Web reasoning disclosure level is isolated from CLI terminal depth.
 */

export enum DisclosureLevel {
  SIMPLE = 1,
  STANDARD = 2,
  DETAILED = 3,
  INTERNAL = 4,
}

export interface DisclosureField<T> {
  level: DisclosureLevel;
  value: T;
}

export interface WebDisclosureContextValue {
  level: DisclosureLevel;
  setLevel: (level: DisclosureLevel) => void;
  canExpandFurther: boolean;
}

const STORAGE_KEY = 'atropos.disclosure.web';

export function coerceDisclosureLevel(rawStoredValue: unknown): DisclosureLevel {
  if (typeof rawStoredValue === 'number') {
    if (rawStoredValue in DisclosureLevel) {
      return rawStoredValue as DisclosureLevel;
    }
  }
  if (typeof rawStoredValue === 'string') {
    const parsed = parseInt(rawStoredValue, 10);
    if (!isNaN(parsed) && parsed in DisclosureLevel) {
      return parsed as DisclosureLevel;
    }
    const upper = rawStoredValue.toUpperCase();
    if (upper === 'SIMPLE') return DisclosureLevel.SIMPLE;
    if (upper === 'STANDARD') return DisclosureLevel.STANDARD;
    if (upper === 'DETAILED') return DisclosureLevel.DETAILED;
    if (upper === 'INTERNAL') return DisclosureLevel.INTERNAL;
  }
  return DisclosureLevel.STANDARD;
}

export function filterFieldsByLevel<T>(
  fields: DisclosureField<T>[],
  level: DisclosureLevel
): T[] {
  return fields
    .filter((f) => f.level <= level)
    .map((f) => f.value);
}

const WebDisclosureContext = createContext<WebDisclosureContextValue>({
  level: DisclosureLevel.STANDARD,
  setLevel: () => {},
  canExpandFurther: true,
});

export function WebDisclosureProvider({ children }: { children: ReactNode }) {
  const [level, setLevelState] = useState<DisclosureLevel>(DisclosureLevel.STANDARD);

  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        setLevelState(coerceDisclosureLevel(stored));
      }
    } catch {
      // Ignore localStorage restrictions
    }
  }, []);

  const setLevel = (newLevel: DisclosureLevel) => {
    setLevelState(newLevel);
    try {
      localStorage.setItem(STORAGE_KEY, String(newLevel));
    } catch {
      // Ignore
    }
  };

  const canExpandFurther = level < DisclosureLevel.INTERNAL;

  return (
    <WebDisclosureContext.Provider value={{ level, setLevel, canExpandFurther }}>
      {children}
    </WebDisclosureContext.Provider>
  );
}

export function useWebDisclosure(): WebDisclosureContextValue {
  return useContext(WebDisclosureContext);
}

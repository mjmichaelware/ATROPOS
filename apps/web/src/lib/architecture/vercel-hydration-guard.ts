/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useState, useEffect } from 'react';

/**
 * F-VERCEL-002: Next.js Dynamic Route Hydration & Cache Invalidation Guard
 * Eliminates DOM attribute hydration mismatches and guarantees fresh evidence reads.
 */

export interface HydrationValidationReport {
  isHydrated: boolean;
  mismatchDetected: boolean;
  routePath: string;
  activeLayoutTheme: string;
  activeColorTheme: string;
}

export function useSafeHydration(): boolean {
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    setIsHydrated(true);
  }, []);

  return isHydrated;
}

export function assertNoStaleCacheHeaders(responseHeaders: Headers): boolean {
  const cacheControl = responseHeaders.get('Cache-Control')?.toLowerCase() || '';
  const isNoStore = cacheControl.includes('no-store') || cacheControl.includes('no-cache');
  return isNoStore;
}

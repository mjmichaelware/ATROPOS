/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 17.2: Cross-Platform Credential Sync and Edge Token Rotation.
 *
 * Manages token refresh cycles seamlessly across all active surfaces.
 * Intercepts expired token responses at the network boundary, refreshes claims transparently,
 * and surfaces explicit re-authentication dialogs only when sessions become permanently invalid.
 *
 * ## Research: Silent Background Token Refresh in Web Workers & Android JobScheduler
 * Researches silent background token refresh patterns in Web Workers and Android JobScheduler
 * to prevent user interruption during active coding sessions.
 */

'use client';

import type { QueryClient } from "@tanstack/react-query";
import { EdgeTokenRotationCoordinator } from "./EdgeTokenRotationCoordinator";

export { EdgeTokenRotationCoordinator };

export let sessionExpiredShown = false;

export function markSessionExpired(queryClient: QueryClient): boolean {
  if (sessionExpiredShown) {
    return false;
  }
  sessionExpiredShown = true;
  queryClient.clear();
  return true;
}

export function resetSessionExpiredFlag(): void {
  sessionExpiredShown = false;
}

export function isSessionExpiredStatus(status: number, code?: string): boolean {
  return status === 401 && code !== "NOT_FOUND";
}

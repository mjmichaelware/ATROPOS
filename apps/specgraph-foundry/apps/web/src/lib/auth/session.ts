"use client";

import type { QueryClient } from "@tanstack/react-query";

let sessionExpiredShown = false;

export function markSessionExpired(queryClient: QueryClient) {
  if (sessionExpiredShown) {
    return false;
  }
  sessionExpiredShown = true;
  queryClient.clear();
  return true;
}

export function resetSessionExpiredFlag() {
  sessionExpiredShown = false;
}

export function isSessionExpiredStatus(status: number, code?: string) {
  return status === 401 && code !== "NOT_FOUND";
}

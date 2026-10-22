"use client";

import { useSyncExternalStore } from "react";

function subscribeToMediaQuery(query: string) {
  return (onChange: () => void) => {
    if (typeof window === "undefined" || !("matchMedia" in window)) return () => {};
    const media = window.matchMedia(query);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  };
}

function readMediaQuery(query: string) {
  return () => (typeof window !== "undefined" && "matchMedia" in window ? window.matchMedia(query).matches : false);
}

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const subscribeReducedMotion = subscribeToMediaQuery(REDUCED_MOTION_QUERY);
const readReducedMotion = readMediaQuery(REDUCED_MOTION_QUERY);

export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribeReducedMotion, readReducedMotion, () => false);
}

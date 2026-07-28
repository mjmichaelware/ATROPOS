"use client";

import { useEffect, type ReactNode } from "react";
import { applyTheme, readTheme } from "@/lib/theme/storage";

export function ThemeProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    applyTheme(document.documentElement, readTheme(window.localStorage));
  }, []);
  return children;
}

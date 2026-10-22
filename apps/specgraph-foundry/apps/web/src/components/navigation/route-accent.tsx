"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { activeProjectSection } from "./routes";
import { projectIdFromPathname } from "./route-utils";

/**
 * Sets the shell's route-context accent (data-accent on <html>) from the
 * currently active project section. Pure DOM synchronization, not React
 * state, so this does not participate in render — it only tags the root
 * element for themes.css's [data-accent] rules to read.
 */
export function RouteAccent() {
  const pathname = usePathname() ?? "/";
  const projectId = projectIdFromPathname(pathname);
  const section = projectId ? activeProjectSection(pathname, projectId) : undefined;

  useEffect(() => {
    document.documentElement.dataset.accent = section?.accent ?? "neutral";
    return () => {
      delete document.documentElement.dataset.accent;
    };
  }, [section?.accent]);

  return null;
}

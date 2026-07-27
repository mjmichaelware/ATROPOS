import type { ReactNode } from "react";

export function CommandDock({ children }: { children: ReactNode }) {
  return <nav className="sg-command-dock" aria-label="Source commands">{children}</nav>;
}

import type { ReactNode } from "react";
import { HudFrame } from "./hud-frame";

export function HudPanel({ title, status, children }: { title?: string; status?: string; children: ReactNode }) {
  return (
    <section className="sg-hud-panel" aria-label={title}>
      <HudFrame />
      {title ? (
        <header className="sg-hud-panel-header">
          <span>{title}</span>
          {status ? <strong>{status}</strong> : null}
        </header>
      ) : null}
      {children}
    </section>
  );
}

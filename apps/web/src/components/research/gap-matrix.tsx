"use client";

import { useState } from "react";
import { HudPanel } from "@/components/visual/hud-panel";
import { gapRows } from "@/lib/research/gaps";
import type { GapCell, GapMatrix as GapMatrixType } from "@/lib/research/schemas";
import { GapMatrixCell } from "./gap-matrix-cell";

export function GapMatrix({ matrix }: { matrix?: GapMatrixType }) {
  const [selected, setSelected] = useState<GapCell | null>(null);
  const rows = gapRows(matrix);
  if (rows.length === 0) {
    return <HudPanel title="Gap field" status="Empty"><p>No atom/dimension gaps were returned by the API.</p></HudPanel>;
  }
  return (
    <section className="sg-gap-field" aria-labelledby="gap-matrix-title">
      <HudPanel title="Gap field" status={`${rows.length} atom rows`}>
        <h2 id="gap-matrix-title">Atom by dimension matrix</h2>
        <p className="sg-muted">Cells are built only from the gap-matrix response. The list below is the accessible equivalent of the HUD field.</p>
      </HudPanel>
      {/* This is a labeled collection of independently-focusable status
          buttons, not an interactive spreadsheet with arrow-key cell
          navigation, so it intentionally does not use role="grid"/"row" —
          those roles impose keyboard-navigation and strict-child
          requirements this widget does not implement. */}
      <div className="sg-gap-grid" aria-label="Research gap matrix">
        {rows.map((row) => (
          <article key={row.atom.id} className="sg-gap-row">
            <header>
              <strong>{String(row.atom.label ?? row.atom.text ?? row.atom.id)}</strong>
              <span>{row.cells.length} dimensions</span>
            </header>
            <div className="sg-gap-cells">
              {row.cells.map((cell, index) => <GapMatrixCell key={`${row.atom.id}-${cell.dimension ?? index}`} cell={cell} onSelect={setSelected} />)}
            </div>
          </article>
        ))}
      </div>
      <aside className="sg-gap-inspector" aria-live="polite">
        <strong>Selected gap</strong>
        {selected ? (
          <p>{String(selected.dimension ?? selected.dimension_id ?? "Dimension")} is {String(selected.status ?? "UNKNOWN")}.</p>
        ) : (
          <p>Select a cell to inspect its backend-returned status.</p>
        )}
      </aside>
    </section>
  );
}

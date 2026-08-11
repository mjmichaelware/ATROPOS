"use client";

import { useState } from "react";
import { HudPanel } from "@/components/visual/hud-panel";
import { gapRows } from "@/lib/research/gaps";
import type { GapCell, GapMatrix as GapMatrixType } from "@/lib/research/schemas";
import { GapMatrixCell } from "./gap-matrix-cell";

export function GapMatrix({ projectId, matrix }: { projectId: string; matrix?: GapMatrixType }) {
  const [selected, setSelected] = useState<GapCell | null>(null);
  const rows = gapRows(matrix);
  if (rows.length === 0) {
    return <HudPanel title="Nothing to check yet" status="Empty"><p>Extract atoms from a source document and their research gaps will show up here.</p></HudPanel>;
  }
  return (
    <section className="sg-gap-field" aria-labelledby="gap-matrix-title">
      <HudPanel title="Research gaps at a glance" status={`${rows.length} atoms`}>
        <h2 id="gap-matrix-title">Every atom, every dimension, one view</h2>
        <p className="sg-muted">Each row is an atom; each cell is one of the 16 dimensions it&apos;s checked against. Click a cell to see its status and jump to that task.</p>
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
              <strong>{String(row.atom.canonical_statement ?? row.atom.label ?? row.atom.text ?? row.atom.id)}</strong>
              <span>{row.cells.length} dimensions</span>
            </header>
            <div className="sg-gap-cells">
              {row.cells.map((cell, index) => (
                <GapMatrixCell key={`${row.atom.id}-${cell.dimension ?? index}`} projectId={projectId} cell={cell} onSelect={setSelected} />
              ))}
            </div>
          </article>
        ))}
      </div>
      <aside className="sg-gap-inspector" aria-live="polite">
        <strong>Selected dimension</strong>
        {selected ? (
          <p>{String(selected.dimension ?? selected.dimension_id ?? "This dimension")} is currently {String(selected.status ?? "UNKNOWN").toLowerCase()}.</p>
        ) : (
          <p>Click any cell above to see its status here.</p>
        )}
      </aside>
    </section>
  );
}

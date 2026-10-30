import { HudPanel } from "@/components/visual/hud-panel";
import type { GapMatrix } from "@/lib/research/schemas";

export function DimensionPanel({ matrix }: { matrix?: GapMatrix }) {
  const dimensions = matrix?.dimensions ?? [];
  return (
    <HudPanel title="Dimensions" status={String(dimensions.length)}>
      <p className="sg-muted">
        A dimension is one specific question every atom gets checked against — things like whether it&apos;s still current, whether it conflicts with another atom, or whether it needs a named
        source. Every atom is checked against the same fixed set, so nothing gets silently skipped.
      </p>
      {dimensions.length ? (
        <ul className="sg-research-list">
          {dimensions.map((dimension) => <li key={dimension}>{dimension}</li>)}
        </ul>
      ) : (
        <p>Dimension names are inferred only when returned by the API.</p>
      )}
    </HudPanel>
  );
}

import { HudPanel } from "@/components/visual/hud-panel";
import type { GapMatrix } from "@/lib/research/schemas";

export function DimensionPanel({ matrix }: { matrix?: GapMatrix }) {
  const dimensions = matrix?.dimensions ?? [];
  return (
    <HudPanel title="Dimensions" status={String(dimensions.length)}>
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

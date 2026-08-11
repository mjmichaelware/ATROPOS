import { HudPanel } from "@/components/visual/hud-panel";

export function ResearchEmptyState({ message = "No research tasks yet." }: { message?: string }) {
  return (
    <HudPanel title="Nothing here yet" status="Empty">
      <p>{message}</p>
      <p className="sg-muted">Add a source document and extract its atoms first — research tasks are generated from those.</p>
    </HudPanel>
  );
}

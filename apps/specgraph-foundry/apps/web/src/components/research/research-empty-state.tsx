import { HudPanel } from "@/components/visual/hud-panel";

export function ResearchEmptyState({ message = "No research tasks are available yet." }: { message?: string }) {
  return (
    <HudPanel title="No inquiry field" status="Empty">
      <p>{message}</p>
      <p className="sg-muted">Create source atoms first; research will only display backend-created gaps and tasks.</p>
    </HudPanel>
  );
}

import { HudPanel } from "@/components/visual/hud-panel";

export function HandoffEmptyState() {
  return (
    <HudPanel title="Nothing sent out yet" status="Empty">
      <p>Once you have a verified plan, come back here to export it or hand it off to a connected system.</p>
    </HudPanel>
  );
}

import { HudPanel } from "@/components/visual/hud-panel";

export function HandoffEmptyState() {
  return (
    <HudPanel title="No handoff activity yet" status="Empty">
      <p>No bindings, exports, or execution runs exist for this project yet. Configure a binding or generate an export once a verified plan exists.</p>
    </HudPanel>
  );
}

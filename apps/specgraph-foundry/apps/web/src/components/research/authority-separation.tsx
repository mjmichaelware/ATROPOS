import { HudPanel } from "@/components/visual/hud-panel";

export function AuthoritySeparation() {
  return (
    <HudPanel title="Authority boundary" status="Separated">
      <div className="sg-authority-separation">
        <span data-channel="source">Source authority: immutable uploaded source and provenance.</span>
        <span data-channel="evidence">Research evidence: supporting external material, never source authority.</span>
        <span data-channel="conclusion">Conclusion: derived determination after backend completion.</span>
      </div>
    </HudPanel>
  );
}

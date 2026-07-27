import { HudPanel } from "@/components/visual/hud-panel";

export function GraphEmptyState({ mode }: { mode: "authority" | "execution" }) {
  return (
    <HudPanel title="No graph to render yet" status="Empty">
      <div className="sg-graph-empty">
        <h2>{mode === "authority" ? "No authority relations recorded" : "No execution plan synthesized"}</h2>
        <p>
          {mode === "authority"
            ? "Authority relations appear here once research produces them. This foundation only displays backend-created relations."
            : "The execution graph is generated when a plan is synthesized. No plan exists for this project yet."}
        </p>
      </div>
    </HudPanel>
  );
}

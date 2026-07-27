import { HudPanel } from "@/components/visual/hud-panel";

export function GraphEmptyState({ mode }: { mode: "authority" | "execution" }) {
  return (
    <HudPanel title="Nothing to map yet" status="Waiting">
      <div className="sg-graph-empty" data-tone={mode}>
        <span className="sg-graph-empty-icon" aria-hidden="true">
          {mode === "authority" ? "◈" : "▷"}
        </span>
        <h2>{mode === "authority" ? "Your source's connections will show up here" : "Your first plan will show up here"}</h2>
        <p>
          {mode === "authority"
            ? "As research resolves each dimension of an atom, this app draws the links between what your sources actually say. Finish a few more research tasks and this space fills in on its own."
            : "Synthesize a plan from the Plans tab and its full execution map will appear here, ready to walk through step by step."}
        </p>
      </div>
    </HudPanel>
  );
}

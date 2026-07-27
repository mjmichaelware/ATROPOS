export const READINESS_LABELS = {
  SOURCE_REQUIRED: "Source required",
  EXTRACTION_REQUIRED: "Extraction required",
  RESEARCH_REQUIRED: "Research required",
  READY_TO_PLAN: "Ready to plan",
  READY_TO_EXPORT: "Ready to export",
  INTEGRATION_BINDING_REQUIRED: "Integration binding required",
  READY_TO_EXECUTE: "Ready to execute",
  VERIFIED: "Verified",
} as const;

export type ReadinessState = keyof typeof READINESS_LABELS;

export function readinessLabel(value: string) {
  return READINESS_LABELS[value as ReadinessState] ?? "Unknown readiness";
}

export function readinessTone(value: string): "success" | "warning" | "info" | "neutral" {
  if (value === "VERIFIED") {
    return "success";
  }
  if (value === "SOURCE_REQUIRED" || value === "EXTRACTION_REQUIRED") {
    return "warning";
  }
  if (value in READINESS_LABELS) {
    return "info";
  }
  return "neutral";
}

export function readinessNextAction(value: string) {
  switch (value) {
    case "SOURCE_REQUIRED":
      return "Add source documents in the source workspace.";
    case "EXTRACTION_REQUIRED":
      return "Extract atoms from accepted source documents.";
    case "RESEARCH_REQUIRED":
      return "Resolve open research dimensions.";
    case "READY_TO_PLAN":
      return "Synthesize and verify a plan.";
    case "READY_TO_EXPORT":
      return "Generate and verify a handoff export.";
    case "INTEGRATION_BINDING_REQUIRED":
      return "Configure an enabled integration binding.";
    case "READY_TO_EXECUTE":
      return "Start and independently verify execution.";
    case "VERIFIED":
      return "The project has verified execution evidence.";
    default:
      return "Review project state before continuing.";
  }
}

// The 7 pipeline stages the backend computes in
// ProjectWorkspaceService._readiness() (workspace.py) - source of truth for
// both the stage order and every status string a given stage can carry.
export const READINESS_STAGE_LABELS: Record<string, string> = {
  SOURCE: "Source",
  ATOMS: "Extraction",
  RESEARCH: "Research",
  PLANNING: "Plan",
  INTEGRATION: "Integration",
  EXPORT: "Export",
  EXECUTION: "Execution",
};

export function stageTone(status: string): "success" | "warning" | "info" | "neutral" {
  if (status === "COMPLETE" || status === "VERIFIED" || status === "CONFIGURED") {
    return "success";
  }
  if (status === "READY") {
    return "info";
  }
  if (status === "PENDING") {
    return "warning";
  }
  return "neutral";
}

// next_action values come from ProjectWorkspaceService._readiness()'s
// overall/next_action branch (workspace.py) - each maps to the screen that
// actually resolves it.
export function nextActionRoute(nextAction: string | undefined, routes: { sources: string; research: string; graph: string; handoffExports: string; handoffBindings: string; handoffRuns: string }): string | undefined {
  switch (nextAction) {
    case "INGEST_SOURCE":
    case "EXTRACT_ATOMS":
      return routes.sources;
    case "COMPLETE_OPEN_DIMENSIONS":
      return routes.research;
    case "SYNTHESIZE_PLAN":
      return routes.graph;
    case "EXPORT_PLAN":
      return routes.handoffExports;
    case "CONFIGURE_INTEGRATION":
      return routes.handoffBindings;
    case "START_EXECUTION":
      return routes.handoffRuns;
    default:
      return undefined;
  }
}

export function nextActionLabel(nextAction: string | undefined): string {
  switch (nextAction) {
    case "INGEST_SOURCE":
      return "Add a source";
    case "EXTRACT_ATOMS":
      return "Extract atoms";
    case "COMPLETE_OPEN_DIMENSIONS":
      return "Resolve research";
    case "SYNTHESIZE_PLAN":
      return "Synthesize plan";
    case "EXPORT_PLAN":
      return "Generate export";
    case "CONFIGURE_INTEGRATION":
      return "Configure integration";
    case "START_EXECUTION":
      return "Start execution";
    default:
      return "Review project";
  }
}

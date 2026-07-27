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

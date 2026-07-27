import type { ConclusionInput } from "./schemas";

export function validateConclusion(input: ConclusionInput) {
  const errors: Partial<Record<keyof ConclusionInput, string>> = {};
  if (!input.conclusion.trim()) {
    errors.conclusion = input.applicability === "NOT_APPLICABLE" ? "A NOT_APPLICABLE conclusion requires explicit justification." : "Conclusion is required.";
  }
  if (input.applicability === "NOT_APPLICABLE" && input.conclusion.trim().length < 12) {
    errors.conclusion = "Explain why the dimension is not applicable; do not use this as unknown or unresolved.";
  }
  if (input.confidence < 0 || input.confidence > 1) {
    errors.confidence = "Confidence must be between 0 and 1.";
  }
  if (input.evidence_ids.length < 1) {
    errors.evidence_ids = "At least one real evidence record is required.";
  }
  return errors;
}

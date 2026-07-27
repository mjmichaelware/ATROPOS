import { validateEvidenceUrl } from "./security";
import type { EvidenceInput } from "./schemas";

export const EVIDENCE_TYPES = ["STANDARD", "SPECIFICATION", "DOCUMENTATION", "OBSERVATION", "OTHER"] as const;

export function validateEvidence(input: EvidenceInput) {
  const errors: Partial<Record<keyof EvidenceInput, string>> = {};
  const urlError = validateEvidenceUrl(input.source_uri);
  if (urlError) errors.source_uri = urlError;
  if (!input.source_title.trim()) errors.source_title = "Evidence title is required.";
  if (!input.excerpt.trim()) errors.excerpt = "Evidence excerpt is required.";
  if (input.reliability !== undefined && (input.reliability < 0 || input.reliability > 1)) {
    errors.reliability = "Reliability must be between 0 and 1.";
  }
  return errors;
}

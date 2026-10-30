import { RELATION_TYPES, type RelationInput } from "./schemas";

const MAX_RATIONALE_LENGTH = 2000;
const OPAQUE_ID_PATTERN = /^[A-Za-z0-9._:-]{1,160}$/;

export function isSupportedRelationType(value: string): value is RelationInput["relation_type"] {
  return (RELATION_TYPES as readonly string[]).includes(value);
}

export function validateRelationInput(input: RelationInput): Partial<Record<keyof RelationInput, string>> {
  const errors: Partial<Record<keyof RelationInput, string>> = {};

  if (!input.from_atom_id || !OPAQUE_ID_PATTERN.test(input.from_atom_id)) {
    errors.from_atom_id = "Select a source atom.";
  }
  if (!input.to_atom_id || !OPAQUE_ID_PATTERN.test(input.to_atom_id)) {
    errors.to_atom_id = "Select a target atom.";
  }
  if (input.from_atom_id && input.to_atom_id && input.from_atom_id === input.to_atom_id) {
    errors.to_atom_id = "Source and target atoms must be different.";
  }
  if (!isSupportedRelationType(input.relation_type)) {
    errors.relation_type = "Select a supported relation type.";
  }
  if (input.rationale !== undefined && input.rationale.length > MAX_RATIONALE_LENGTH) {
    errors.rationale = `Rationale must be ${MAX_RATIONALE_LENGTH} characters or fewer.`;
  }
  if (input.confidence !== undefined && (!Number.isFinite(input.confidence) || input.confidence < 0 || input.confidence > 1)) {
    errors.confidence = "Confidence must be a number between 0 and 1.";
  }
  return errors;
}

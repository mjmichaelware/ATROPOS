export function isSafeIdentifier(value: string) {
  return /^[A-Za-z0-9._:-]{1,160}$/.test(value);
}

export function isValidProjectId(projectId: string | undefined | null): projectId is string {
  return typeof projectId === "string" && isSafeIdentifier(projectId);
}

const MAX_LAYOUT_STATE_BYTES = 64_000;

/**
 * Bounds how much layout-preference data is persisted client-side. Layout
 * state never contains source content, evidence, conclusions, or bearer
 * material by construction (see VisualLayoutState), so this only guards
 * against unbounded growth, not confidentiality.
 */
export function isSafeToPersist(serialized: string): boolean {
  return serialized.length > 0 && serialized.length <= MAX_LAYOUT_STATE_BYTES;
}

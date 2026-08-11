export function isSafeIdentifier(value: string) {
  return /^[A-Za-z0-9._:-]{1,160}$/.test(value);
}

const MAX_BINDING_CONFIG_BYTES = 8_000;

export function isBoundedBindingConfig(config: Record<string, unknown>): boolean {
  try {
    return JSON.stringify(config).length <= MAX_BINDING_CONFIG_BYTES;
  } catch {
    return false;
  }
}

export function createResearchWorkerId() {
  const uuid = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  return `research-tab-${uuid}`;
}

export function validateEvidenceUrl(value: string) {
  if (!value || value.length > 2048 || /[\u0000-\u001f\u007f]/.test(value)) {
    return "Enter a bounded HTTPS evidence URL.";
  }
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.hash) {
      return "Evidence URLs must be HTTPS, without credentials or fragments.";
    }
    return undefined;
  } catch {
    return "Enter a valid HTTPS evidence URL.";
  }
}

export function boundedPlainText(value: string, max = 4000) {
  return value.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "").slice(0, max);
}

export function isSafeIdentifier(value: string) {
  return /^[A-Za-z0-9._:-]{1,160}$/.test(value);
}

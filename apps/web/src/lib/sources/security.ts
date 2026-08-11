const CONTROL = /[\u0000-\u001f\u007f]/;

export function isSafeIdentifier(value: string) {
  return /^[0-9a-fA-F-]{16,80}$/.test(value) && !CONTROL.test(value);
}

export function sanitizeFilename(value: string) {
  const leaf = value.split(/[/\\]/).at(-1)?.trim() ?? "";
  return leaf.replace(CONTROL, "").slice(0, 160) || "source";
}

export function validateSignedUploadUrl(value: string, supabaseUrl: string) {
  const target = new URL(value);
  const supabase = new URL(supabaseUrl);
  if (target.protocol !== "https:" && target.hostname !== "127.0.0.1" && target.hostname !== "localhost") {
    throw new Error("signed upload target must use HTTPS");
  }
  if (target.origin !== supabase.origin) {
    throw new Error("signed upload target origin does not match Supabase");
  }
  if (target.username || target.password || target.hash) {
    throw new Error("signed upload target is not safe");
  }
  return target;
}

export function safeDisplay(value: unknown, fallback = "Unknown") {
  if (typeof value !== "string" || !value.trim()) {
    return fallback;
  }
  return value.replace(CONTROL, "").slice(0, 180);
}

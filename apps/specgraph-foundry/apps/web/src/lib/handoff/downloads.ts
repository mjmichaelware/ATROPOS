const CONTROL_PATTERN = new RegExp("[\\x00-\\x1f\\x7f]", "g");

/**
 * Validates an ephemeral signed export-artifact download URL before it is
 * opened. Signed URLs are single-purpose and time-limited; this function
 * only validates shape/origin, it never persists the URL.
 */
export function validateSignedDownloadUrl(value: string, supabaseUrl: string) {
  const target = new URL(value);
  const supabase = new URL(supabaseUrl);
  if (target.protocol !== "https:" && target.hostname !== "127.0.0.1" && target.hostname !== "localhost") {
    throw new Error("signed download target must use HTTPS");
  }
  if (target.origin !== supabase.origin) {
    throw new Error("signed download target origin does not match Supabase");
  }
  if (target.username || target.password || target.hash) {
    throw new Error("signed download target is not safe");
  }
  return target;
}

/**
 * Opens a validated signed download URL via an explicit user action. The
 * URL itself is never returned, stored, or logged by this function — only a
 * boolean success is reported, so callers cannot accidentally persist it.
 */
export function openSignedDownload(
  value: string,
  supabaseUrl: string,
  openImpl: (url: string) => void = (url) => window.open(url, "_blank", "noopener,noreferrer"),
): boolean {
  try {
    const target = validateSignedDownloadUrl(value, supabaseUrl);
    openImpl(target.toString());
    return true;
  } catch {
    return false;
  }
}

export function safeArtifactName(value: unknown, fallback = "artifact") {
  if (typeof value !== "string" || !value.trim()) {
    return fallback;
  }
  return value.replace(CONTROL_PATTERN, "").slice(0, 180);
}

const EXPIRY_WARNING_MARGIN_SECONDS = 15;

export function isDownloadLikelyExpired(expiresAt: string, now: number = Date.now()): boolean {
  const expiry = Date.parse(expiresAt);
  if (!Number.isFinite(expiry)) return false;
  return expiry - now <= EXPIRY_WARNING_MARGIN_SECONDS * 1000;
}

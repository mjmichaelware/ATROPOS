export async function sha256Hex(file: Blob) {
  const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function shortHash(value?: string, visible = 12) {
  if (!value) {
    return "hash unavailable";
  }
  return value.length <= visible * 2 ? value : `${value.slice(0, visible)}…${value.slice(-visible)}`;
}

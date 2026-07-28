import { describe, expect, it, vi } from "vitest";
import { isDownloadLikelyExpired, openSignedDownload, safeArtifactName, validateSignedDownloadUrl } from "./downloads";
import { isBoundedBindingConfig, isSafeIdentifier } from "./security";

const SUPABASE_URL = "https://example.supabase.co";

describe("signed download URL validation", () => {
  it("accepts a same-origin HTTPS signed URL", () => {
    expect(validateSignedDownloadUrl(SUPABASE_URL + "/storage/v1/object/sign/x", SUPABASE_URL).origin).toBe(SUPABASE_URL);
  });

  it("rejects a cross-origin download target", () => {
    expect(() => validateSignedDownloadUrl("https://evil.example/storage/v1/object/sign/x", SUPABASE_URL)).toThrow(/origin/);
  });

  it("rejects a URL carrying embedded credentials", () => {
    const host = new URL(SUPABASE_URL).host;
    expect(() => validateSignedDownloadUrl("https://user:pass@" + host + "/x", SUPABASE_URL)).toThrow(/safe/);
  });

  it("opens a validated URL via the provided explicit-action implementation and never returns the URL itself", () => {
    const openImpl = vi.fn();
    const url = SUPABASE_URL + "/storage/v1/object/sign/x";
    const result = openSignedDownload(url, SUPABASE_URL, undefined, openImpl);
    expect(result).toBe(true);
    expect(openImpl).toHaveBeenCalledWith(url);
  });

  it("accepts a URL matching the API origin when apiUrl is provided", () => {
    const API_URL = "https://api.example.run";
    const openImpl = vi.fn();
    const url = API_URL + "/v1/artifact-downloads/tok.sig";
    const result = openSignedDownload(url, SUPABASE_URL, API_URL, openImpl);
    expect(result).toBe(true);
    expect(openImpl).toHaveBeenCalledWith(url);
  });

  it("rejects a URL that matches neither Supabase nor API origin", () => {
    const API_URL = "https://api.example.run";
    expect(openSignedDownload("https://evil.example/x", SUPABASE_URL, API_URL, vi.fn())).toBe(false);
  });

  it("fails closed (returns false) for an invalid signed URL instead of throwing", () => {
    expect(openSignedDownload("https://evil.example/x", SUPABASE_URL, undefined, vi.fn())).toBe(false);
  });

  it("flags a download as likely expired near its expiry boundary", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    expect(isDownloadLikelyExpired("2026-01-01T00:00:10Z", now)).toBe(true);
    expect(isDownloadLikelyExpired("2026-01-01T00:05:00Z", now)).toBe(false);
  });
});

describe("safe artifact naming", () => {
  it("strips control characters and bounds the length", () => {
    const withControlChar = "manifest" + String.fromCharCode(7) + ".json";
    expect(safeArtifactName(withControlChar)).toBe("manifest.json");
    expect(safeArtifactName("x".repeat(300))).toHaveLength(180);
    expect(safeArtifactName("")).toBe("artifact");
    expect(safeArtifactName(undefined)).toBe("artifact");
  });
});

describe("identifier and binding config bounds", () => {
  it("validates opaque identifiers", () => {
    expect(isSafeIdentifier("binding-123")).toBe(true);
    expect(isSafeIdentifier("<script>")).toBe(false);
  });

  it("bounds binding configuration payload size", () => {
    expect(isBoundedBindingConfig({ key: "value" })).toBe(true);
    expect(isBoundedBindingConfig({ key: "x".repeat(20000) })).toBe(false);
  });
});

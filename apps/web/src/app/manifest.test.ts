import { describe, expect, it } from "vitest";
import manifest from "./manifest";

describe("web app manifest", () => {
  const result = manifest();

  it("has truthful, complete identity fields", () => {
    expect(result.name).toBe("SpecGraph Foundry");
    expect(result.short_name).toBe("SpecGraph");
    expect(result.start_url).toBe("/");
    expect(result.scope).toBe("/");
    expect(result.display).toBe("standalone");
  });

  it("declares real icon assets with a 192x192 and a 512x512 size", () => {
    const sizes = (result.icons ?? []).map((icon) => icon.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    for (const icon of result.icons ?? []) {
      expect(icon.src.startsWith("/icon")).toBe(true);
      expect(icon.type).toBe("image/png");
    }
  });

  it("declares at least one maskable-purpose icon", () => {
    const purposes = (result.icons ?? []).map((icon) => icon.purpose);
    expect(purposes).toContain("maskable");
  });

  it("never references an authenticated project id in shortcuts or share targets", () => {
    expect((result as { shortcuts?: unknown }).shortcuts).toBeUndefined();
    expect((result as { share_target?: unknown }).share_target).toBeUndefined();
    expect((result as { protocol_handlers?: unknown }).protocol_handlers).toBeUndefined();
    expect((result as { screenshots?: unknown }).screenshots).toBeUndefined();
  });

  it("uses stable, opaque theme/background colors matching the graphite shell", () => {
    expect(result.theme_color).toBe("#07100d");
    expect(result.background_color).toBe("#07100d");
  });
});

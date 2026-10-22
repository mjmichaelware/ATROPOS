import { describe, expect, it, vi } from "vitest";
import { safeRedirectPath } from "./proxy";

vi.mock("@/lib/config/client-env", () => ({
  readPublicEnv: () => ({
    NEXT_PUBLIC_SUPABASE_URL: "https://example.supabase.co",
    NEXT_PUBLIC_SUPABASE_ANON_KEY: "anon-key-123456",
    NEXT_PUBLIC_SPECGRAPH_API_URL: "http://127.0.0.1:8787",
  }),
}));

describe("auth foundation", () => {
  it("keeps redirects same-origin and path-only", () => {
    expect(safeRedirectPath("/projects?next=1")).toBe("/projects?next=1");
    expect(safeRedirectPath("https://evil.example/projects")).toBe("/");
    expect(safeRedirectPath(null, "/fallback")).toBe("/fallback");
  });
});

import { describe, expect, it } from "vitest";
import { readPublicEnv } from "./client-env";
import { assertNoServerSecretsInPublicEnv, readServerEnv } from "./server-env";

const valid = {
  NEXT_PUBLIC_SPECGRAPH_API_URL: "http://127.0.0.1:8787",
  NEXT_PUBLIC_SUPABASE_URL: "https://example.supabase.co",
  NEXT_PUBLIC_SUPABASE_ANON_KEY: "anon-key-123456",
};

describe("environment validation", () => {
  it("accepts local public URLs and separates API from Supabase", () => {
    expect(readPublicEnv(valid).NEXT_PUBLIC_SPECGRAPH_API_URL).toBe("http://127.0.0.1:8787");
    expect(readServerEnv({ ...valid, SPECGRAPH_WEB_BASE_URL: "http://127.0.0.1:3000" }).public).toBeDefined();
  });

  it("rejects credentials, fragments, and same API/Supabase origins in strict/Vercel mode", () => {
    expect(() => readPublicEnv({ ...valid, NEXT_PUBLIC_SPECGRAPH_API_URL: "http://user@127.0.0.1:8787" }, { strict: true })).toThrow(
      "Public web configuration is invalid",
    );
    expect(() => readPublicEnv({ ...valid, NEXT_PUBLIC_SPECGRAPH_API_URL: "http://127.0.0.1:8787#x" }, { strict: true })).toThrow(
      "Public web configuration is invalid",
    );
    expect(() => readPublicEnv({ ...valid, NEXT_PUBLIC_SUPABASE_URL: "http://127.0.0.1:8787" }, { strict: true })).toThrow(
      "Public web configuration is invalid",
    );
  });

  it("gracefully falls back with warnings in local standalone mode without cloud keys", () => {
    const fallback = readPublicEnv({});
    expect(fallback.NEXT_PUBLIC_SPECGRAPH_API_URL).toBe("http://127.0.0.1:8787");
    expect(fallback.NEXT_PUBLIC_SUPABASE_URL).toBe("http://127.0.0.1:8000/mock");
    expect(fallback.NEXT_PUBLIC_SUPABASE_ANON_KEY).toBeDefined();
  });

  it("prevents secret-shaped public environment names", () => {
    expect(() => assertNoServerSecretsInPublicEnv({ NEXT_PUBLIC_SERVICE_ROLE_KEY: "nope" })).toThrow(
      "Public web configuration is invalid",
    );
  });
});

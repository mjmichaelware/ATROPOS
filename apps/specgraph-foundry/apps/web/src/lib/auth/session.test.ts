import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { isSessionExpiredStatus, markSessionExpired, resetSessionExpiredFlag } from "./session";

describe("session expiry", () => {
  it("clears private cache once", () => {
    const client = new QueryClient();
    client.setQueryData(["project", "one"], { secret: true });
    resetSessionExpiredFlag();
    expect(markSessionExpired(client)).toBe(true);
    expect(client.getQueryCache().getAll()).toHaveLength(0);
    expect(markSessionExpired(client)).toBe(false);
  });

  it("distinguishes 401 from not-found", () => {
    expect(isSessionExpiredStatus(401, "AUTHENTICATION_FAILED")).toBe(true);
    expect(isSessionExpiredStatus(404, "NOT_FOUND")).toBe(false);
  });
});

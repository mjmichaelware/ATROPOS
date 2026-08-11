import { describe, expect, it } from "vitest";
import { readRecentProjectId, writeRecentProjectId } from "./selection";

describe("active project selection", () => {
  it("stores only non-sensitive recent project id convenience state", () => {
    const map = new Map<string, string>();
    const storage = {
      getItem: (key: string) => map.get(key) ?? null,
      setItem: (key: string, value: string) => map.set(key, value),
    };
    writeRecentProjectId(storage, "12345678-1234-1234-1234-123456789abc");
    expect(readRecentProjectId(storage)).toBe("12345678-1234-1234-1234-123456789abc");
    map.set("specgraph.recentProjectId", "Bearer token");
    expect(readRecentProjectId(storage)).toBeNull();
  });
});

import { describe, expect, it } from "vitest";
import { applyTheme, readTheme, writeTheme } from "./storage";

describe("theme storage", () => {
  it("stores only non-sensitive theme choices", () => {
    const map = new Map<string, string>();
    const storage = {
      getItem: (key: string) => map.get(key) ?? null,
      setItem: (key: string, value: string) => map.set(key, value),
    };
    writeTheme(storage, "high-contrast");
    expect(readTheme(storage)).toBe("high-contrast");
  });

  it("applies theme attributes", () => {
    const element = document.createElement("html");
    applyTheme(element, "light");
    expect(element.dataset.theme).toBe("light");
  });
});

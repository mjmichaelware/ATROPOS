import { describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "./keys";

describe("query foundation", () => {
  it("rejects unsafe query key parts", () => {
    expect(queryKeys.project("project-1")).toEqual(["project", "project-1"]);
    expect(() => queryKeys.project("Bearer token")).toThrow("unsafe query key part");
  });

  it("does not blindly retry mutations", () => {
    const client = new QueryClient({
      defaultOptions: {
        mutations: {
          retry: false,
        },
      },
    });
    expect(client.getDefaultOptions().mutations?.retry).toBe(false);
  });
});

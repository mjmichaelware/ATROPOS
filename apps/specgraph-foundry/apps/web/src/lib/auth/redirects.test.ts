import { describe, expect, it } from "vitest";
import { authErrorMessage, sanitizeReturnPath } from "./redirects";
import { isPublicPath } from "./proxy";

describe("auth redirects", () => {
  it("accepts only safe relative return paths", () => {
    expect(sanitizeReturnPath("/projects/123?tab=x")).toBe("/projects/123?tab=x");
    expect(sanitizeReturnPath("//evil.example")).toBe("/projects");
    expect(sanitizeReturnPath("https://evil.example")).toBe("/projects");
    expect(sanitizeReturnPath("/auth/callback?code=x")).toBe("/projects");
    expect(sanitizeReturnPath("/projects%2f%2fevil")).toBe("/projects");
    expect(sanitizeReturnPath("/projects\\x")).toBe("/projects");
  });

  it("maps provider failures to safe messages", () => {
    expect(authErrorMessage("callback")).not.toContain("code");
    expect(authErrorMessage("expired")).toContain("expired");
  });

  it("classifies public proxy paths", () => {
    expect(isPublicPath("/auth/sign-in")).toBe(true);
    expect(isPublicPath("/projects")).toBe(false);
  });
});

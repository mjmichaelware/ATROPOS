import { render, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const mockUsePathname = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => mockUsePathname() }));

afterEach(() => {
  cleanup();
  delete document.documentElement.dataset.accent;
});

describe("RouteAccent", () => {
  it("tags the root element with the active project section's accent", async () => {
    const { RouteAccent } = await import("./route-accent");
    mockUsePathname.mockReturnValue("/projects/proj-1/sources");
    render(<RouteAccent />);
    expect(document.documentElement.dataset.accent).toBe("sources");
  });

  it("falls back to neutral outside any project section", async () => {
    const { RouteAccent } = await import("./route-accent");
    mockUsePathname.mockReturnValue("/projects");
    render(<RouteAccent />);
    expect(document.documentElement.dataset.accent).toBe("neutral");
  });

  it("reflects the routing section's accent on the routing route", async () => {
    const { RouteAccent } = await import("./route-accent");
    mockUsePathname.mockReturnValue("/projects/proj-1/routing");
    render(<RouteAccent />);
    expect(document.documentElement.dataset.accent).toBe("routing");
  });
});

import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import OfflinePage from "./page";

describe("offline page", () => {
  it("has exactly one page-level heading and a main landmark", () => {
    render(<OfflinePage />);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("main")).toBeInTheDocument();
  });

  it("explains that the network is unavailable and private data is not stored offline", () => {
    render(<OfflinePage />);
    expect(screen.getByText(/could not reach the network/i)).toBeInTheDocument();
    expect(screen.getByText(/intentionally never stored for offline use/i)).toBeInTheDocument();
  });

  it("offers Retry and a return-to-root action as real links, not JavaScript-only controls", () => {
    render(<OfflinePage />);
    const retry = screen.getByRole("link", { name: "Retry" });
    const home = screen.getByRole("link", { name: "Return to SpecGraph Foundry" });
    expect(retry).toHaveAttribute("href", "/offline");
    expect(home).toHaveAttribute("href", "/");
  });

  it("contains no real project identifier, source content, or plan payload", () => {
    render(<OfflinePage />);
    // A UUID-shaped project id or any stringified JSON payload would indicate
    // leaked private data; the honest disclosure text itself is expected and fine.
    expect(document.body.innerHTML).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
    expect(document.body.innerHTML).not.toContain("{");
  });
});

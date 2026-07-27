import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { buildFixtureGraph } from "@/lib/graph/fixtures";
import { filterGraphContent, emptyGraphFilterState } from "@/lib/graph/search";
import { semanticGraphToRendererContent } from "@/lib/graph/transform";
import { graphSizeTier, requiresLargeGraphSafeMode } from "@/lib/graph/zoom";
import { GraphAccessibleList } from "./graph-accessible-list";
import { GraphLargeModeNotice } from "./graph-large-mode-notice";

describe("fixture-driven bounded rendering", () => {
  it("renders full detail for the 100-node fixture without a safe-mode notice", () => {
    const content = semanticGraphToRendererContent(buildFixtureGraph(100));
    expect(content.nodes).toHaveLength(100);
    expect(graphSizeTier(content.nodes.length)).toBe("small");
    expect(requiresLargeGraphSafeMode(content.nodes.length)).toBe(false);
    const { container } = render(<GraphLargeModeNotice size={graphSizeTier(content.nodes.length)} nodeCount={content.nodes.length} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("enters simplified/bounded mode for the 1,000-node fixture", () => {
    const content = semanticGraphToRendererContent(buildFixtureGraph(1000));
    expect(content.nodes).toHaveLength(1000);
    expect(graphSizeTier(content.nodes.length)).toBe("medium");
    render(<GraphLargeModeNotice size="medium" nodeCount={content.nodes.length} />);
    expect(screen.getByText("Simplified rendering active")).toBeInTheDocument();
  });

  it("enters explicit large-graph safe mode for the 10,000-node fixture and bounds the accessible list to one page", () => {
    const content = semanticGraphToRendererContent(buildFixtureGraph(10000));
    expect(content.nodes).toHaveLength(10000);
    expect(requiresLargeGraphSafeMode(content.nodes.length)).toBe(true);
    render(<GraphLargeModeNotice size="large" nodeCount={content.nodes.length} />);
    expect(screen.getByText("Large-graph safe mode active")).toBeInTheDocument();

    render(<GraphAccessibleList content={content} onSelect={() => {}} />);
    const rows = screen.getAllByRole("row");
    // Header row + a single bounded page (50 rows), never all 10,000 rows at once.
    expect(rows.length).toBeLessThanOrEqual(51);
    expect(screen.getByText("Page 1 of 200")).toBeInTheDocument();
  });

  it("keeps filtering deterministic and bounded even over the largest fixture", () => {
    const content = semanticGraphToRendererContent(buildFixtureGraph(10000));
    const filtered = filterGraphContent(content, { ...emptyGraphFilterState(), status: "COMPLETE" });
    expect(filtered.nodes.length).toBeLessThan(content.nodes.length);
    expect(filtered.nodes.every((node) => node.data.status === "COMPLETE")).toBe(true);
  });
});

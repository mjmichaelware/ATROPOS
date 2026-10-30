"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { VisuallyHidden } from "@/components/ui/visually-hidden";
import { connectedRelationshipCount, neighborhood } from "@/lib/graph/search";
import type { RendererGraphContent } from "@/lib/graph/transform";

const PAGE_SIZE = 50;

export function GraphAccessibleList({
  content,
  selectedId,
  onSelect,
}: {
  content: RendererGraphContent;
  selectedId?: string;
  onSelect: (id: string) => void;
}) {
  const [page, setPage] = useState(0);
  const start = page * PAGE_SIZE;
  const pageNodes = content.nodes.slice(start, start + PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(content.nodes.length / PAGE_SIZE));

  if (content.nodes.length === 0) {
    return (
      <div className="sg-graph-accessible-list">
        <p>No nodes match the current search and filters.</p>
      </div>
    );
  }

  return (
    <div className="sg-graph-accessible-list">
      <h2 className="sg-micro-label">Accessible graph list ({content.nodes.length} node{content.nodes.length === 1 ? "" : "s"})</h2>
      <table className="sg-graph-table">
        <caption>
          <VisuallyHidden>Loaded and filtered graph nodes with type, status, and relationship counts</VisuallyHidden>
        </caption>
        <thead>
          <tr>
            <th scope="col">Label</th>
            <th scope="col">Type</th>
            <th scope="col">Status</th>
            <th scope="col">Relationships</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {pageNodes.map((node) => {
            const relationships = connectedRelationshipCount(node.id, content.edges);
            const related = neighborhood(node.id, content).nodes.filter((neighbor) => neighbor.id !== node.id);
            const isSelected = node.id === selectedId;
            return (
              <tr key={node.id} data-selected={isSelected || undefined}>
                <th scope="row">{node.data.label}</th>
                <td>{node.data.category}</td>
                <td>{node.data.status ? <StatusBadge tone="neutral" label={node.data.status} /> : "—"}</td>
                <td>
                  {relationships}
                  {related.length > 0 ? <VisuallyHidden> connected to {related.map((neighbor) => neighbor.data.label).join(", ")}</VisuallyHidden> : null}
                </td>
                <td>
                  <Button type="button" variant={isSelected ? "verified" : "quiet"} aria-pressed={isSelected} onClick={() => onSelect(node.id)}>
                    {isSelected ? "Selected" : "Inspect"}
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {totalPages > 1 ? (
        <nav className="sg-pagination" aria-label="Accessible list pagination">
          <Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>
            Previous
          </Button>
          <span role="status">
            Page {page + 1} of {totalPages}
          </span>
          <Button type="button" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))}>
            Next
          </Button>
        </nav>
      ) : null}
    </div>
  );
}

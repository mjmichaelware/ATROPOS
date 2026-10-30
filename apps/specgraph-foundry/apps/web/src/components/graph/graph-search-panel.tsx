"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { RendererNodeCategory } from "@/lib/graph/transform";
import type { GraphFilterState } from "@/lib/graph/search";

export function GraphSearchPanel({
  filters,
  categories,
  statuses,
  resultCount,
  onChange,
  onClear,
}: {
  filters: GraphFilterState;
  categories: RendererNodeCategory[];
  statuses: string[];
  resultCount: number;
  onChange: (filters: GraphFilterState) => void;
  onClear: () => void;
}) {
  const active = filters.query !== "" || filters.category !== "all" || filters.status !== "all";
  return (
    <div role="search" className="sg-graph-search" aria-label="Search and filter the loaded graph">
      <div className="sg-field">
        <Label htmlFor="graph-search-query">Search loaded nodes</Label>
        <Input
          id="graph-search-query"
          type="search"
          value={filters.query}
          placeholder="Search by label or ID"
          onChange={(event) => onChange({ ...filters, query: event.target.value })}
        />
      </div>
      <div className="sg-field">
        <Label htmlFor="graph-filter-category">Node type</Label>
        <select
          id="graph-filter-category"
          className="sg-select"
          value={filters.category}
          onChange={(event) => onChange({ ...filters, category: event.target.value as GraphFilterState["category"] })}
        >
          <option value="all">All types</option>
          {categories.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </select>
      </div>
      <div className="sg-field">
        <Label htmlFor="graph-filter-status">Status</Label>
        <select id="graph-filter-status" className="sg-select" value={filters.status} onChange={(event) => onChange({ ...filters, status: event.target.value })}>
          <option value="all">All statuses</option>
          {statuses.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
      </div>
      <p role="status" className="sg-micro-label">
        {resultCount} loaded node{resultCount === 1 ? "" : "s"} match. Search covers only the currently loaded, bounded subset — not the full server-side dataset.
      </p>
      {active ? (
        <Button type="button" variant="quiet" onClick={onClear}>
          Clear filters
        </Button>
      ) : null}
    </div>
  );
}

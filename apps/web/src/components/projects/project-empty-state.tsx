import type { Route } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export function ProjectEmptyState() {
  return (
    <section className="sg-card sg-graph-empty">
      <span className="sg-graph-empty-icon" aria-hidden="true">
        ◈
      </span>
      <h2>Your first project starts here</h2>
      <p>Upload a source document and SpecGraph will extract it into addressable, provenance-tracked atoms — then research, plan, and verify from there.</p>
      <Button asChild>
        <Link href={"/projects/new" as Route}>Create project</Link>
      </Button>
    </section>
  );
}

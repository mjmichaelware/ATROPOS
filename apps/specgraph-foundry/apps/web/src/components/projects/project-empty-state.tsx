import type { Route } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export function ProjectEmptyState() {
  return (
    <section className="sg-card">
      <h2>No projects yet</h2>
      <p>Create a project to start building authority-backed workspaces.</p>
      <Button asChild>
        <Link href={"/projects/new" as Route}>Create project</Link>
      </Button>
    </section>
  );
}

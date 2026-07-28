import type { ResearchTask } from "@/lib/research/schemas";

export function ResearchTimeline({ task }: { task?: ResearchTask }) {
  return (
    <ol className="sg-research-timeline" aria-label="Research task timeline">
      <li data-state="source">Source atom selected</li>
      <li data-state="evidence">Evidence records {task?.evidence?.length ? "available" : "pending"}</li>
      <li data-state="conclusion">Conclusion {task?.conclusion ? "recorded" : "not complete"}</li>
    </ol>
  );
}

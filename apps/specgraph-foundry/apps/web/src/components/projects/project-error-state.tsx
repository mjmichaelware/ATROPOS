import { Button } from "@/components/ui/button";

export function ProjectErrorState({ title = "Projects unavailable", onRetry }: { title?: string; onRetry?: () => void }) {
  return (
    <section className="sg-card" role="alert">
      <h2>{title}</h2>
      <p>Project data could not be loaded safely. Retry when the connection is available.</p>
      {onRetry ? (
        <Button type="button" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </section>
  );
}

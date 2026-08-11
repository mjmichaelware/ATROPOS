import { Button } from "@/components/ui/button";

export function ProjectErrorState({
  title = "Projects unavailable",
  detail,
  onRetry,
}: {
  title?: string;
  detail?: string;
  onRetry?: () => void;
}) {
  return (
    <section className="sg-card" role="alert">
      <h2>{title}</h2>
      <p>Project data could not be loaded safely. Retry when the connection is available.</p>
      {detail ? (
        <p className="sg-mono" style={{ whiteSpace: "pre-wrap" }}>
          {detail}
        </p>
      ) : null}
      {onRetry ? (
        <Button type="button" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </section>
  );
}

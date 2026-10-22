import type { SourceDocument } from "@/lib/sources/schemas";

export function DocumentContent({ document }: { document: SourceDocument }) {
  const content = typeof document.content === "string" ? document.content : "";
  if (!content) {
    return <p className="sg-muted">Derived text is not included in this response.</p>;
  }
  return (
    <pre className="sg-document-content" aria-label="Derived text">
      {content}
    </pre>
  );
}

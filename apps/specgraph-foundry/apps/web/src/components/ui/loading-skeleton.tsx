import { Skeleton } from "./skeleton";

const col: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: "var(--sg-space-3)",
};

const row: React.CSSProperties = {
  display: "flex",
  gap: "var(--sg-space-3)",
  alignItems: "center",
};

/** Hero header: label + title + subtitle + action */
export function SkeletonHero() {
  return (
    <div style={{ ...col, marginBottom: "var(--sg-space-5)" }}>
      <Skeleton style={{ width: "6rem", height: "0.75rem" }} />
      <Skeleton style={{ width: "55%", height: "2rem" }} />
      <Skeleton style={{ width: "80%", height: "1rem" }} />
      <Skeleton style={{ width: "5rem", height: "2rem", borderRadius: "var(--sg-radius-pill)" }} />
    </div>
  );
}

/** A fake tab bar */
export function SkeletonTabs({ count = 4 }: { count?: number }) {
  return (
    <div style={{ ...row, marginBottom: "var(--sg-space-4)" }}>
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} style={{ width: "4.5rem", height: "1.75rem", borderRadius: "var(--sg-radius-pill)" }} />
      ))}
    </div>
  );
}

/** A single card-shaped row (list item placeholder) */
export function SkeletonCard({ wide = false }: { wide?: boolean }) {
  return (
    <div style={{ ...col, padding: "var(--sg-space-4)", border: "1px solid var(--sg-border)", borderRadius: "var(--sg-radius-md)", gap: "var(--sg-space-2)" }}>
      <div style={row}>
        <Skeleton style={{ width: "0.75rem", height: "0.75rem", borderRadius: "999px", flexShrink: 0 }} />
        <Skeleton style={{ width: wide ? "60%" : "40%", height: "0.875rem" }} />
        <Skeleton style={{ marginLeft: "auto", width: "3.5rem", height: "1.5rem", borderRadius: "var(--sg-radius-pill)" }} />
      </div>
      <Skeleton style={{ width: "85%", height: "0.75rem" }} />
    </div>
  );
}

/** Text content block (paragraph placeholder) */
export function SkeletonParagraph({ lines = 3 }: { lines?: number }) {
  const widths = ["100%", "88%", "72%", "95%", "60%"];
  return (
    <div style={col}>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} style={{ width: widths[i % widths.length], height: "0.75rem" }} />
      ))}
    </div>
  );
}

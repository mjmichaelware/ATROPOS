import { ByteRange } from "./byte-range";
import { LineRange } from "./line-range";

export function DocumentSections({ sections = [] }: { sections?: Array<Record<string, unknown>> }) {
  return <PreviewList title="Sections" items={sections} />;
}

export function PreviewList({ title, items }: { title: string; items: Array<Record<string, unknown>> }) {
  return (
    <section className="sg-preview-list" aria-labelledby={`${title}-title`}>
      <h2 id={`${title}-title`}>{title}</h2>
      {items.length === 0 ? <p className="sg-muted">No preview records returned.</p> : null}
      {items.slice(0, 5).map((item, index) => (
        <article key={String(item.id ?? index)}>
          <strong>{String(item.title ?? item.kind ?? `${title} ${index + 1}`)}</strong>
          <ByteRange range={item} />
          <LineRange range={item} />
        </article>
      ))}
    </section>
  );
}

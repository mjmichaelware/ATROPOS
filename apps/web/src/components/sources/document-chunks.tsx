import { PreviewList } from "./document-sections";

export function DocumentChunks({ chunks = [] }: { chunks?: Array<Record<string, unknown>> }) {
  return <PreviewList title="Chunks" items={chunks} />;
}

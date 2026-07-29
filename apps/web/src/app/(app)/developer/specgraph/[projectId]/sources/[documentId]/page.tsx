import { DocumentInspector } from "@/components/sources/document-inspector";

export default async function SourceDocumentPage({ params }: { params: Promise<{ projectId: string; documentId: string }> }) {
  const { projectId, documentId } = await params;
  // key={documentId}: Next.js App Router reuses this client component
  // across navigations between different documentId values under the same
  // route pattern rather than remounting it, so without an explicit key
  // its local state (atom pagination, extraction-status banner) would
  // carry over from whichever document was viewed previously.
  return <DocumentInspector key={documentId} projectId={projectId} documentId={documentId} />;
}

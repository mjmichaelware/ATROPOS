import { DocumentInspector } from "@/components/sources/document-inspector";

export default async function SourceDocumentPage({ params }: { params: Promise<{ projectId: string; documentId: string }> }) {
  const { projectId, documentId } = await params;
  return <DocumentInspector projectId={projectId} documentId={documentId} />;
}

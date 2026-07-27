import { SourceWorkspace } from "@/components/sources/source-workspace";

export default async function SourcesPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <SourceWorkspace projectId={projectId} />;
}

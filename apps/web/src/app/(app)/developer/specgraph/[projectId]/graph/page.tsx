import { GraphWorkspace } from "@/components/graph/graph-workspace";

export default async function GraphPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <GraphWorkspace projectId={projectId} />;
}

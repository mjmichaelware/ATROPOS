import { ExecutionRunDetail } from "@/components/execution/execution-run-detail";

export default async function ExecutionRunPage({ params }: { params: Promise<{ projectId: string; runId: string }> }) {
  const { projectId, runId } = await params;
  return <ExecutionRunDetail projectId={projectId} runId={runId} />;
}

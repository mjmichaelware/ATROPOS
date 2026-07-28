import { ResearchWorkspace } from "@/components/research/research-workspace";

export default async function ResearchPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <ResearchWorkspace projectId={projectId} />;
}

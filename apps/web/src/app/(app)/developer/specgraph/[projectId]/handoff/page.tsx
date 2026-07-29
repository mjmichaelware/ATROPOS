import { HandoffWorkspace } from "@/components/handoff/handoff-workspace";

export default async function HandoffPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <HandoffWorkspace projectId={projectId} />;
}

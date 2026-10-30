import { RoutingWorkspace } from "@/components/routing/routing-workspace";

export default async function RoutingPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <RoutingWorkspace projectId={projectId} />;
}

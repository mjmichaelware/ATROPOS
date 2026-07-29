import { TaskInspector } from "@/components/research/task-inspector";

export default async function ResearchTaskPage({ params }: { params: Promise<{ projectId: string; taskId: string }> }) {
  const { projectId, taskId } = await params;
  return <TaskInspector projectId={projectId} taskId={taskId} />;
}

import { ProjectCommandCenter } from "@/components/projects/project-command-center";

export default async function ProjectPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <ProjectCommandCenter projectId={projectId} />;
}

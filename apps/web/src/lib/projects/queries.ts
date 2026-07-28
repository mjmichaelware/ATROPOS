import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient, getOperations, getProject, getReadiness, getWorkspace, listProjects } from "./api";
import { queryKeys } from "@/lib/query/keys";

export function useProjectsPage(cursor?: string, pageIndex = 0) {
  return useQuery({
    queryKey: ["projects", pageIndex],
    queryFn: () => listProjects(createProjectApiClient(), { limit: 12, cursor }),
  });
}

export function useProjectCommandCenter(projectId: string) {
  return {
    project: useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => getProject(createProjectApiClient(), projectId) }),
    workspace: useQuery({ queryKey: queryKeys.workspace(projectId), queryFn: () => getWorkspace(createProjectApiClient(), projectId) }),
    readiness: useQuery({ queryKey: ["project", projectId, "readiness"], queryFn: () => getReadiness(createProjectApiClient(), projectId) }),
    operations: useQuery({ queryKey: ["project", projectId, "operations"], queryFn: () => getOperations(createProjectApiClient(), projectId) }),
  };
}

import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getGapMatrix, getResearchTask, getResearchWorkspace, listResearchTasks } from "./api";

export function useResearchWorkspace(projectId: string) {
  return useQuery({
    queryKey: queryKeys.researchWorkspace(projectId),
    queryFn: () => getResearchWorkspace(createProjectApiClient(), projectId),
  });
}

export function useGapMatrix(projectId: string) {
  return useQuery({
    queryKey: queryKeys.researchGapMatrix(projectId),
    queryFn: () => getGapMatrix(createProjectApiClient(), projectId),
  });
}

export function useResearchTasks(projectId: string, cursor?: string, pageIndex = 0) {
  return useQuery({
    queryKey: queryKeys.researchTasks(projectId, pageIndex),
    queryFn: () => listResearchTasks(createProjectApiClient(), projectId, { limit: 12, cursor }),
  });
}

export function useResearchTask(taskId: string) {
  return useQuery({
    queryKey: queryKeys.researchTask(taskId),
    queryFn: () => getResearchTask(createProjectApiClient(), taskId),
  });
}

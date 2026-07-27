import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getExecutionRun, listProjectExecutionRuns } from "./api";

export function useProjectExecutionRuns(projectId: string) {
  return useQuery({
    queryKey: queryKeys.executionRunList(projectId),
    queryFn: () => listProjectExecutionRuns(createProjectApiClient(), projectId),
  });
}

export function useExecutionRunDetail(runId: string) {
  return useQuery({
    queryKey: queryKeys.executionRunDetail(runId),
    queryFn: () => getExecutionRun(createProjectApiClient(), runId),
  });
}

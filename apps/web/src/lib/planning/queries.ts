import { useQuery } from "@tanstack/react-query";
import { listProjectPlans } from "@/lib/graph/api";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";

export function usePlanList(projectId: string) {
  return useQuery({
    queryKey: queryKeys.planList(projectId),
    queryFn: () => listProjectPlans(createProjectApiClient(), projectId),
  });
}

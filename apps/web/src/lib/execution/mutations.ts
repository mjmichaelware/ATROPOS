import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { verifyExecutionRun } from "./api";
import type { FreeformRecord } from "./schemas";

export function useVerifyExecutionRunMutation(projectId: string, runId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const client = createProjectApiClient();
      const accepted = await verifyExecutionRun(client, runId, client.createIdempotencyKey());
      return accepted.location ? client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location) : accepted;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.executionRunDetail(runId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.executionRunList(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

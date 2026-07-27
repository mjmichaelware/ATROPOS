import { useMutation, useQueryClient } from "@tanstack/react-query";
import { describeOperationProgress, type OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { verifyExecutionRun } from "./api";
import type { FreeformRecord } from "./schemas";

export function useVerifyExecutionRunMutation(projectId: string, runId: string, onProgress?: (message: string) => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const client = createProjectApiClient();
      onProgress?.("Execution run verification queued — waiting for a worker to pick it up.");
      const accepted = await verifyExecutionRun(client, runId, client.createIdempotencyKey());
      return accepted.location
        ? client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location, {
            onProgress: (operation) => onProgress?.(describeOperationProgress("Execution run verification", operation)),
          })
        : accepted;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.executionRunDetail(runId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.executionRunList(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { createOrUpdateBinding, downloadExportArtifacts, exportPlan, startExecutionRun, verifyExport } from "./api";
import type { BindingInput, ExecutionRunStartInput, FreeformRecord } from "./schemas";

export function useCreateOrUpdateBindingMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey, ifMatch }: { input: BindingInput; idempotencyKey: string; ifMatch?: string }) =>
      createOrUpdateBinding(createProjectApiClient(), projectId, input, idempotencyKey, ifMatch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.bindings(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

export function useExportPlanMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ planId, outputRoot }: { planId: string; outputRoot?: string }) => {
      const client = createProjectApiClient();
      const accepted = await exportPlan(client, planId, outputRoot, client.createIdempotencyKey());
      return accepted.location ? client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location) : accepted;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.exports(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

export function useVerifyExportMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (exportId: string) => {
      const client = createProjectApiClient();
      const accepted = await verifyExport(client, exportId, client.createIdempotencyKey());
      const terminal = accepted.location ? await client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location) : accepted;
      return { exportId, terminal };
    },
    onSuccess: ({ exportId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.exportDetail(exportId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.exports(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

/**
 * Modeled as a mutation (not a cached query) because it is an explicit,
 * user-initiated action that returns ephemeral signed URLs which must
 * never be cached, refetched automatically, or persisted.
 */
export function useDownloadExportArtifactsMutation() {
  return useMutation({
    mutationFn: (exportId: string) => downloadExportArtifacts(createProjectApiClient(), exportId),
  });
}

export function useStartExecutionRunMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ planId, input }: { planId: string; input: ExecutionRunStartInput }) => {
      const client = createProjectApiClient();
      const accepted = await startExecutionRun(client, planId, input, client.createIdempotencyKey());
      return accepted.location ? client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location) : accepted;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.executionRunList(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

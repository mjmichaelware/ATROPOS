import { useMutation, useQueryClient } from "@tanstack/react-query";
import { describeOperationProgress, type OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { createProjectRelation, synthesizePlan, verifyPlan } from "./api";
import type { FreeformRecord, RelationInput } from "./schemas";

export function useCreateRelationMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RelationInput) => createProjectRelation(createProjectApiClient(), projectId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.graphRelations(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.planning(projectId) });
    },
  });
}

export function useSynthesizePlanMutation(projectId: string, onProgress?: (message: string) => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (allowOpenResearch: boolean) => {
      const client = createProjectApiClient();
      onProgress?.("Plan synthesis queued — waiting for a worker to pick it up.");
      const accepted = await synthesizePlan(client, projectId, allowOpenResearch, client.createIdempotencyKey());
      const terminal = accepted.location
        ? await client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location, {
            onProgress: (operation) => onProgress?.(describeOperationProgress("Plan synthesis", operation)),
          })
        : accepted;
      return terminal;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.planning(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.planList(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.readiness(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(projectId) });
    },
  });
}

export function useVerifyPlanMutation(projectId: string, planId: string | undefined, onProgress?: (message: string) => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      if (!planId) throw new Error("No plan selected");
      const client = createProjectApiClient();
      onProgress?.("Plan verification queued — waiting for a worker to pick it up.");
      const accepted = await verifyPlan(client, planId, client.createIdempotencyKey());
      const terminal = accepted.location
        ? await client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location, {
            onProgress: (operation) => onProgress?.(describeOperationProgress("Plan verification", operation)),
          })
        : accepted;
      return terminal;
    },
    onSuccess: () => {
      if (planId) void queryClient.invalidateQueries({ queryKey: queryKeys.graphPlan(planId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.planning(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.planList(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.readiness(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(projectId) });
    },
  });
}

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import {
  createOrUpdateProvider,
  createOrUpdateRenderer,
  createProjectRouteDecision,
  grantProjectPaidUnlock,
  recordProviderHealth,
  selectProjectRenderer,
  setRoutingPolicy,
} from "./api";
import type { PaidUnlockInput, ProviderHealthInput, ProviderInput, RendererInput, RendererSelectInput, RouteDecisionInput, RoutingPolicyInput } from "./schemas";

export function useSetRoutingPolicyMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, ifMatch }: { input: RoutingPolicyInput; ifMatch: string }) => setRoutingPolicy(createProjectApiClient(), projectId, input, ifMatch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.routingPolicy(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

export function useCreateOrUpdateProviderMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey, ifMatch }: { input: ProviderInput; idempotencyKey: string; ifMatch?: string }) =>
      createOrUpdateProvider(createProjectApiClient(), projectId, input, idempotencyKey, ifMatch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.providers(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

export function useRecordProviderHealthMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ providerId, input, idempotencyKey }: { providerId: string; input: ProviderHealthInput; idempotencyKey: string }) =>
      recordProviderHealth(createProjectApiClient(), providerId, input, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.providers(projectId) });
    },
  });
}

export function useCreateOrUpdateRendererMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey, ifMatch }: { input: RendererInput; idempotencyKey: string; ifMatch?: string }) =>
      createOrUpdateRenderer(createProjectApiClient(), projectId, input, idempotencyKey, ifMatch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.renderers(projectId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.handoffWorkspace(projectId) });
    },
  });
}

export function useSelectRendererMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: RendererSelectInput; idempotencyKey: string }) =>
      selectProjectRenderer(createProjectApiClient(), projectId, input, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.renderers(projectId) });
    },
  });
}

export function useGrantPaidUnlockMutation(projectId: string) {
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: PaidUnlockInput; idempotencyKey: string }) =>
      grantProjectPaidUnlock(createProjectApiClient(), projectId, input, idempotencyKey),
  });
}

export function useCreateRouteDecisionMutation(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: RouteDecisionInput; idempotencyKey: string }) =>
      createProjectRouteDecision(createProjectApiClient(), projectId, input, idempotencyKey),
    onSuccess: (result) => {
      void queryClient.setQueryData(queryKeys.routeDecisionDetail(result.body.id), result);
    },
  });
}

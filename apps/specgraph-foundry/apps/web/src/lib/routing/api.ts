import { SpecGraphApiClient } from "@/lib/api/client";
import type {
  PaidUnlock,
  PaidUnlockInput,
  Provider,
  ProviderHealthInput,
  ProviderInput,
  Renderer,
  RendererInput,
  RendererSelectInput,
  RouteDecision,
  RouteDecisionInput,
  RoutingPolicy,
  RoutingPolicyInput,
} from "./schemas";

export function getRoutingPolicy(client: SpecGraphApiClient, projectId: string) {
  return client.request<RoutingPolicy>({ path: `/v1/projects/${projectId}/routing-policy` });
}

export function setRoutingPolicy(client: SpecGraphApiClient, projectId: string, input: RoutingPolicyInput, ifMatch: string) {
  return client.request<RoutingPolicy>({ method: "POST", path: `/v1/projects/${projectId}/routing-policy`, body: input, ifMatch });
}

export function listProjectProviders(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: Provider[] }>({ path: `/v1/projects/${projectId}/providers` });
}

export function createOrUpdateProvider(client: SpecGraphApiClient, projectId: string, input: ProviderInput, idempotencyKey: string, ifMatch?: string) {
  return client.request<Provider>({ method: "POST", path: `/v1/projects/${projectId}/providers`, body: input, idempotencyKey, ifMatch });
}

export function recordProviderHealth(client: SpecGraphApiClient, providerId: string, input: ProviderHealthInput, idempotencyKey: string) {
  return client.request<Record<string, unknown>>({ method: "POST", path: `/v1/providers/${providerId}/health`, body: input, idempotencyKey });
}

export function listProjectRenderers(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: Renderer[] }>({ path: `/v1/projects/${projectId}/renderers` });
}

export function createOrUpdateRenderer(client: SpecGraphApiClient, projectId: string, input: RendererInput, idempotencyKey: string, ifMatch?: string) {
  return client.request<Renderer>({ method: "POST", path: `/v1/projects/${projectId}/renderers`, body: input, idempotencyKey, ifMatch });
}

export function selectProjectRenderer(client: SpecGraphApiClient, projectId: string, input: RendererSelectInput, idempotencyKey: string) {
  return client.request<{ renderer: Renderer }>({ method: "POST", path: `/v1/projects/${projectId}/renderers/select`, body: input, idempotencyKey });
}

export function grantProjectPaidUnlock(client: SpecGraphApiClient, projectId: string, input: PaidUnlockInput, idempotencyKey: string) {
  return client.request<PaidUnlock>({ method: "POST", path: `/v1/projects/${projectId}/paid-unlocks`, body: input, idempotencyKey });
}

export function createProjectRouteDecision(client: SpecGraphApiClient, projectId: string, input: RouteDecisionInput, idempotencyKey: string) {
  return client.request<RouteDecision>({ method: "POST", path: `/v1/projects/${projectId}/route-decisions`, body: input, idempotencyKey });
}

export function getRouteDecision(client: SpecGraphApiClient, decisionId: string) {
  return client.request<RouteDecision>({ path: `/v1/route-decisions/${decisionId}` });
}

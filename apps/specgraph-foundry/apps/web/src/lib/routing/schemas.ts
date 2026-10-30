import type { ApiResult } from "@/lib/api/client";

export type FreeformRecord = Record<string, unknown>;

export type RoutingPolicy = FreeformRecord & {
  allow_offline_degraded?: boolean;
  paid_emergency_enabled?: boolean;
  max_paid_decisions_per_unlock?: number;
};

export type Provider = FreeformRecord & {
  id: string;
  name?: string;
  provider_class?: string;
  cost_class?: string;
  territories?: string[];
  priority?: number;
  enabled?: boolean;
  status?: string;
  etag?: string;
};

export type Renderer = FreeformRecord & {
  id: string;
  name?: string;
  renderer_type?: string;
  territories?: string[];
  priority?: number;
  enabled?: boolean;
  etag?: string;
};

export type PaidUnlock = FreeformRecord & {
  id: string;
  project_id?: string;
  actor_id?: string;
  reason?: string;
  ttl_seconds?: number;
  max_decisions?: number;
  used_count?: number;
  provider_id?: string | null;
  created_at?: string;
  expires_at?: string;
};

export type RouteDecision = FreeformRecord & {
  id: string;
  project_id?: string;
  territory?: string;
  offline_capable?: boolean;
  status?: string;
  selected_provider_id?: string | null;
  selected_renderer_id?: string | null;
  reason_code?: string;
  cost_class?: string | null;
  risk_level?: string | null;
  created_at?: string;
};

export type RoutingPolicyInput = {
  allow_offline_degraded?: boolean;
  paid_emergency_enabled?: boolean;
  max_paid_decisions_per_unlock?: number;
};

export type ProviderInput = {
  name: string;
  provider_class: string;
  cost_class: string;
  territories?: string[];
  priority?: number;
  metadata?: FreeformRecord;
  enabled?: boolean;
};

export type ProviderHealthInput = {
  status: string;
  latency_ms?: number;
  error_message?: string;
  cooldown_seconds?: number;
};

export type RendererInput = {
  name: string;
  renderer_type: string;
  territories?: string[];
  priority?: number;
  metadata?: FreeformRecord;
  enabled?: boolean;
};

export type RendererSelectInput = {
  territory: string;
};

export type PaidUnlockInput = {
  actor_id: string;
  reason: string;
  ttl_seconds: number;
  max_decisions?: number;
  provider_id?: string;
};

export type RouteDecisionInput = {
  territory: string;
  offline_capable?: boolean;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;

import { SpecGraphApiClient } from "@/lib/api/client";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";
import { readPublicEnv } from "@/lib/config/client-env";
import type { ProjectCreateInput, ProjectListResponse, Project } from "./schemas";

export function createProjectApiClient(fetchImpl?: typeof fetch) {
  const env = readPublicEnv();
  return new SpecGraphApiClient({
    baseUrl: env.NEXT_PUBLIC_SPECGRAPH_API_URL,
    fetchImpl,
    getBearerToken: async () => {
      const { data } = await createSupabaseBrowserClient().auth.getSession();
      return data.session?.access_token;
    },
  });
}

export async function listProjects(client: SpecGraphApiClient, page?: { limit?: number; cursor?: string }) {
  return client.request<ProjectListResponse>({ path: "/v1/projects", page });
}

export async function createProject(client: SpecGraphApiClient, input: ProjectCreateInput) {
  return client.request<Project>({ method: "POST", path: "/v1/projects", body: input });
}

export async function getProject(client: SpecGraphApiClient, projectId: string) {
  return client.request<Project>({ path: `/v1/projects/${projectId}` });
}

export async function getWorkspace(client: SpecGraphApiClient, projectId: string) {
  return client.request<Record<string, unknown>>({ path: `/v1/projects/${projectId}/workspace` });
}

export async function getReadiness(client: SpecGraphApiClient, projectId: string) {
  return client.request<Record<string, unknown>>({ path: `/v1/projects/${projectId}/readiness` });
}

export async function getOperations(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: Array<Record<string, unknown>> }>({ path: `/v1/projects/${projectId}/operations`, page: { limit: 5 } });
}

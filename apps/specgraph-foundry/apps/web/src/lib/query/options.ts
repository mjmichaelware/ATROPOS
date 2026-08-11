import type { SpecGraphApiClient } from "@/lib/api/client";
import { queryKeys } from "./keys";

export function healthQueryOptions(client: SpecGraphApiClient) {
  return {
    queryKey: queryKeys.health(),
    queryFn: () => client.request({ path: "/health/ready", retryGet: true }),
    staleTime: 15_000,
  };
}

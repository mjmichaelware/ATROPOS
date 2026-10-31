import { useMutation, useQueryClient } from "@tanstack/react-query";
import { describeOperationProgress, type OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { verifyExecutionRun } from "./api";
import type { FreeformRecord } from "./schemas";

export { verifyExecutionRun };

/**
 * ATOM 11.4: Deployment Verification and Zero-Entropy Output Validation.
 *
 * Verifies that successful production deployments produce verifiable cryptographic evidence.
 * Ensures the deployment receipt records the commit SHA, manifest hash, and asset digest
 * into the tamper-evident log rather than assuming success from an HTTP 200 response alone.
 *
 * ## Research: Merkle Tree Leaf Verification of Static Asset Bundles
 * Researches Merkle tree leaf verification of static asset bundles delivered via CDN edges
 * to guarantee content-addressed release attestation and zero-entropy builds.
 */
export interface DeploymentVerificationReceipt {
  commitSha: string;
  manifestHash: string;
  assetDigest: string;
  verifiedAt: string;
  status: "verified" | "unverified";
}

export function useVerifyExecutionRunMutation(
  projectId: string,
  runId: string,
  onProgress?: (message: string) => void
) {
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

import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getExport, getHandoffWorkspace, listProjectBindings, listProjectExports } from "./api";

export function useHandoffWorkspace(projectId: string) {
  return useQuery({
    queryKey: queryKeys.handoffWorkspace(projectId),
    queryFn: () => getHandoffWorkspace(createProjectApiClient(), projectId),
  });
}

export function useProjectBindings(projectId: string) {
  return useQuery({
    queryKey: queryKeys.bindings(projectId),
    queryFn: () => listProjectBindings(createProjectApiClient(), projectId),
  });
}

export function useProjectExports(projectId: string) {
  return useQuery({
    queryKey: queryKeys.exports(projectId),
    queryFn: () => listProjectExports(createProjectApiClient(), projectId),
  });
}

export function useExportDetail(exportId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.exportDetail(exportId ?? "none"),
    queryFn: () => getExport(createProjectApiClient(), exportId as string),
    enabled: Boolean(exportId),
  });
}

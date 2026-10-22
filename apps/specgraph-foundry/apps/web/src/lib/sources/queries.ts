import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getDocument, getDocumentProvenance, getSourceWorkspace, listDocumentAtoms, listDocuments } from "./api";

export function useSourceWorkspace(projectId: string) {
  return useQuery({
    queryKey: queryKeys.sourceWorkspace(projectId),
    queryFn: () => getSourceWorkspace(createProjectApiClient(), projectId),
  });
}

export function useDocumentPage(projectId: string, cursor?: string, pageIndex = 0) {
  return useQuery({
    queryKey: queryKeys.sourceDocuments(projectId, pageIndex),
    queryFn: () => listDocuments(createProjectApiClient(), projectId, { limit: 12, cursor }),
  });
}

export function useDocumentInspector(documentId: string, atomCursor?: string, atomPage = 0) {
  return {
    document: useQuery({ queryKey: queryKeys.sourceDocument(documentId), queryFn: () => getDocument(createProjectApiClient(), documentId) }),
    provenance: useQuery({
      queryKey: queryKeys.sourceProvenance(documentId),
      queryFn: () => getDocumentProvenance(createProjectApiClient(), documentId),
    }),
    atoms: useQuery({
      queryKey: queryKeys.sourceAtoms(documentId, atomPage),
      queryFn: () => listDocumentAtoms(createProjectApiClient(), documentId, { limit: 20, cursor: atomCursor }),
    }),
  };
}

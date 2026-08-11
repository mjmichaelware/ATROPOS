export type PageRequest = {
  limit?: number;
  cursor?: string;
};

export function applyPagination(url: URL, page?: PageRequest) {
  if (page?.limit !== undefined) {
    url.searchParams.set("limit", String(page.limit));
  }
  if (page?.cursor) {
    url.searchParams.set("cursor", page.cursor);
  }
}

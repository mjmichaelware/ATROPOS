export type PaginationHeaders = {
  limit?: number;
  count?: number;
  hasMore?: boolean;
  nextCursor?: string;
};

export function parsePaginationHeaders(headers: Headers): PaginationHeaders {
  return {
    limit: numberHeader(headers, "x-page-limit"),
    count: numberHeader(headers, "x-page-count"),
    hasMore: booleanHeader(headers, "x-has-more"),
    nextCursor: headers.get("x-next-cursor") ?? undefined,
  };
}

export function parseRetryAfter(headers: Headers, capSeconds = 30): number | undefined {
  const value = numberHeader(headers, "retry-after");
  if (value === undefined) {
    return undefined;
  }
  return Math.max(1, Math.min(capSeconds, value));
}

function numberHeader(headers: Headers, name: string) {
  const value = headers.get(name);
  if (value === null) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function booleanHeader(headers: Headers, name: string) {
  const value = headers.get(name);
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  return undefined;
}

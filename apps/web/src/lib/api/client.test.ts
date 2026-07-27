import { describe, expect, it, vi } from "vitest";
import { SpecGraphApiClient } from "./client";
import { SpecGraphApiError, RequestTimeoutError } from "./errors";
import { errorEnvelope, jsonResponse } from "@/test/factories";

describe("SpecGraphApiClient", () => {
  it("parses success metadata, pagination, etag, and request id", async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse(
        { items: [] },
        {
          headers: {
            etag: '"abc"',
            "x-page-limit": "50",
            "x-page-count": "0",
            "x-has-more": "false",
          },
        },
      ),
    );
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl });
    const result = await client.request({ path: "/v1/projects", page: { limit: 50 } });
    expect(result.requestId).toBe("request-123");
    expect(result.etag).toBe('"abc"');
    expect(result.pagination).toMatchObject({ limit: 50, count: 0, hasMore: false });
    const firstCall = fetchImpl.mock.calls[0] as unknown as [URL, RequestInit];
    expect(String(firstCall[0])).toContain("limit=50");
  });

  it("normalizes nested stable errors without leaking body text", async () => {
    const client = new SpecGraphApiClient({
      baseUrl: "http://127.0.0.1:8787",
      fetchImpl: vi.fn(async () => jsonResponse(errorEnvelope("NOT_FOUND"), { status: 404 })),
    });
    await expect(client.request({ path: "/v1/projects/missing" })).rejects.toMatchObject({
      status: 404,
      code: "NOT_FOUND",
      requestId: "request-123",
    } satisfies Partial<SpecGraphApiError>);
  });

  it("sends idempotency and if-match headers only when supplied", async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ ok: true }));
    const client = new SpecGraphApiClient({
      baseUrl: "http://127.0.0.1:8787",
      fetchImpl,
      getBearerToken: () => "token",
    });
    await client.request({
      method: "POST",
      path: "/v1/projects",
      body: { slug: "x" },
      idempotencyKey: "idempotency-key-1234",
      ifMatch: '"etag"',
    });
    const firstCall = fetchImpl.mock.calls[0] as unknown as [URL, RequestInit];
    const headers = firstCall[1].headers as Headers;
    expect(headers.get("authorization")).toBe("Bearer token");
    expect(headers.get("idempotency-key")).toBe("idempotency-key-1234");
    expect(headers.get("if-match")).toBe('"etag"');
  });

  it("polls 202 operations until terminal state", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ operation: { state: "RUNNING" } }, { headers: { "retry-after": "1" } }))
      .mockResolvedValueOnce(jsonResponse({ operation: { state: "SUCCEEDED" } }));
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl });
    const result = await client.pollOperation<{ operation: { state: "SUCCEEDED" } }>("/v1/operations/1", {
      intervalMs: 1,
      timeoutMs: 2_000,
    });
    expect(result.body.operation.state).toBe("SUCCEEDED");
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it("turns client timeouts into typed timeout errors", async () => {
    const fetchImpl = vi.fn(
      (_url: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((resolve, reject) => {
          init?.signal?.addEventListener(
            "abort",
            () => reject(new DOMException("aborted", "AbortError")),
            { once: true },
          );
          setTimeout(() => resolve(jsonResponse({ ok: true })), 20);
        }),
    );
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl, requestTimeoutMs: 1 });
    await expect(client.request({ path: "/health/ready" })).rejects.toBeInstanceOf(RequestTimeoutError);
  });
});

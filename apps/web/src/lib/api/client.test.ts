import { describe, expect, it, vi } from "vitest";
import { SpecGraphApiClient } from "./client";
import { DependencyFailureError, SpecGraphApiError, RequestTimeoutError } from "./errors";
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

  it("normalizes a GET request's failure even when the built-in retry also fails (regression: raw 'Failed to fetch' reaching the UI)", async () => {
    // A real browser's fetch() throws a bare TypeError("Failed to fetch")
    // for a network-level failure - e.g. a mobile tab backgrounded
    // mid-request. request()'s single built-in GET retry previously called
    // attempt() a second time with no try/catch around it, so if that
    // retry also failed, the raw TypeError escaped unnormalized straight
    // to the caller instead of becoming the same DependencyFailureError
    // every other failure path produces.
    const fetchImpl = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl });
    const error = await client.request({ path: "/v1/projects" }).catch((thrown: unknown) => thrown);
    expect(error).toBeInstanceOf(DependencyFailureError);
    expect((error as Error).message).not.toContain("Failed to fetch");
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it("tolerates a bounded run of transient poll failures instead of failing the whole wait on one bad tick", async () => {
    // pollOperation can legitimately run for minutes while a worker picks
    // up the operation. A mobile browser backgrounding the tab for a
    // moment mid-wait can make a single poll tick's request fail outright
    // even though the operation itself is still fine server-side -
    // failing the entire wait (and discarding everything already waited
    // for) on one bad tick would be far more disruptive than briefly
    // retrying.
    let calls = 0;
    const fetchImpl = vi.fn(async () => {
      calls += 1;
      // Each poll tick makes up to 2 fetch() calls (request()'s own
      // built-in GET retry), so fail the first 2 ticks entirely (4 calls)
      // before succeeding on the 3rd tick.
      if (calls <= 4) {
        throw new TypeError("Failed to fetch");
      }
      return jsonResponse({ operation: { state: "SUCCEEDED" } });
    });
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl });
    const result = await client.pollOperation<{ operation: { state: "SUCCEEDED" } }>("/v1/operations/1", {
      intervalMs: 1,
      timeoutMs: 5_000,
    });
    expect(result.body.operation.state).toBe("SUCCEEDED");
  });

  it("does not retry a real API error response inside pollOperation", async () => {
    const fetchImpl = vi.fn(async () => jsonResponse(errorEnvelope("NOT_FOUND"), { status: 404 }));
    const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787", fetchImpl });
    await expect(
      client.pollOperation<{ operation: { state: "SUCCEEDED" } }>("/v1/operations/missing", { intervalMs: 1, timeoutMs: 5_000 }),
    ).rejects.toBeInstanceOf(SpecGraphApiError);
    // One request() call, which itself makes at most 2 fetch() calls for a
    // GET (the built-in single retry) - a 404 must not be retried by the
    // poll loop's own transient-failure tolerance.
    expect(fetchImpl.mock.calls.length).toBeLessThanOrEqual(2);
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

  it("calls the default fetch with a receiver, not detached (regression: browsers throw 'Illegal invocation' for bare fetch refs)", async () => {
    // Real browsers' native fetch has an internal brand check requiring
    // `this` to be the window/global object. Simulate that here: throw
    // unless invoked with globalThis as the receiver, exactly like a real
    // browser would for a fetch reference stored and called as a method.
    const brandedFetch = function (this: unknown) {
      if (this !== globalThis) {
        throw new TypeError("Failed to execute 'fetch' on 'Window': Illegal invocation");
      }
      return Promise.resolve(jsonResponse({ items: [] }));
    };
    vi.stubGlobal("fetch", brandedFetch);
    try {
      const client = new SpecGraphApiClient({ baseUrl: "http://127.0.0.1:8787" });
      await expect(client.request({ path: "/v1/projects" })).resolves.toMatchObject({ body: { items: [] } });
    } finally {
      vi.unstubAllGlobals();
    }
  });
});

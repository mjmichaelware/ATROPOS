import type { paths } from "./generated";
import { parsePaginationHeaders, parseRetryAfter, type PaginationHeaders } from "./headers";
import { TERMINAL_OPERATION_STATES, type OperationLike } from "./operations";
import { applyPagination, type PageRequest } from "./pagination";
import {
  MalformedResponseError,
  RequestAbortError,
  RequestTimeoutError,
  SpecGraphApiError,
  normalizeUnknownError,
} from "./errors";

type HttpMethod = "GET" | "HEAD" | "POST" | "PUT" | "PATCH" | "DELETE";
type BearerProvider = () => Promise<string | undefined> | string | undefined;

export type ApiClientOptions = {
  baseUrl: string;
  getBearerToken?: BearerProvider;
  fetchImpl?: typeof fetch;
  retryAfterCapSeconds?: number;
  requestTimeoutMs?: number;
};

export type RequestOptions = {
  method?: HttpMethod;
  path: string;
  body?: unknown;
  signal?: AbortSignal;
  page?: PageRequest;
  idempotencyKey?: string;
  ifMatch?: string;
  retryGet?: boolean;
};

export type ApiResult<T> = {
  body: T;
  status: number;
  requestId?: string;
  etag?: string;
  location?: string;
  retryAfter?: number;
  pagination: PaginationHeaders;
  idempotencyReplayed?: boolean;
};

export class SpecGraphApiClient {
  private readonly baseUrl: URL;
  private readonly fetchImpl: typeof fetch;
  private readonly retryAfterCapSeconds: number;
  private readonly requestTimeoutMs: number;

  constructor(private readonly options: ApiClientOptions) {
    this.baseUrl = new URL(options.baseUrl);
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.retryAfterCapSeconds = options.retryAfterCapSeconds ?? 30;
    this.requestTimeoutMs = options.requestTimeoutMs ?? 20_000;
  }

  async request<T = unknown>(options: RequestOptions): Promise<ApiResult<T>> {
    const method = options.method ?? "GET";
    const attempt = async () => this.requestOnce<T>({ ...options, method });
    try {
      const first = await attempt();
      return first;
    } catch (error) {
      const normalized = normalizeUnknownError(error);
      if (method !== "GET" && method !== "HEAD") {
        throw normalized;
      }
      if (options.retryGet === false || normalized instanceof SpecGraphApiError) {
        throw normalized;
      }
      return attempt();
    }
  }

  async pollOperation<T extends { operation: OperationLike }>(
    operationUrl: string,
    options: { signal?: AbortSignal; timeoutMs?: number; intervalMs?: number } = {},
  ): Promise<ApiResult<T>> {
    const started = Date.now();
    const timeoutMs = options.timeoutMs ?? 60_000;
    const intervalMs = Math.max(500, Math.min(options.intervalMs ?? 2_000, 30_000));
    while (Date.now() - started < timeoutMs) {
      const result = await this.request<T>({ path: operationUrl, signal: options.signal });
      if (TERMINAL_OPERATION_STATES.has(result.body.operation.state)) {
        return result;
      }
      await delay(result.retryAfter ? result.retryAfter * 1000 : intervalMs, options.signal);
    }
    throw new RequestTimeoutError("Operation polling timed out");
  }

  createIdempotencyKey() {
    return crypto.randomUUID();
  }

  private async requestOnce<T>(options: RequestOptions & { method: HttpMethod }): Promise<ApiResult<T>> {
    const url = new URL(options.path, this.baseUrl);
    applyPagination(url, options.page);
    const headers = new Headers({ accept: "application/json" });
    const token = await this.options.getBearerToken?.();
    if (token) {
      headers.set("authorization", `Bearer ${token}`);
    }
    if (options.idempotencyKey) {
      headers.set("idempotency-key", options.idempotencyKey);
    }
    if (options.ifMatch) {
      headers.set("if-match", options.ifMatch);
    }
    let body: string | undefined;
    if (options.body !== undefined) {
      headers.set("content-type", "application/json");
      body = JSON.stringify(options.body);
    }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.requestTimeoutMs);
    const signal = combineSignals(controller.signal, options.signal);
    try {
      const response = await this.fetchImpl(url, {
        method: options.method,
        headers,
        body,
        signal,
        cache: "no-store",
      });
      return await this.parseResponse<T>(response);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw options.signal?.aborted ? new RequestAbortError("Request was aborted") : new RequestTimeoutError("Request timed out");
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  private async parseResponse<T>(response: Response): Promise<ApiResult<T>> {
    const text = await response.text();
    const body = text ? safeJson(text) : {};
    const requestId = response.headers.get("x-request-id") ?? undefined;
    if (!response.ok) {
      if (isErrorEnvelope(body)) {
        throw new SpecGraphApiError(response.status, body.error.code, body.error.message, body.error.request_id, body.error.details);
      }
      throw new MalformedResponseError("API returned an invalid error response");
    }
    return {
      body: body as T,
      status: response.status,
      requestId,
      etag: response.headers.get("etag") ?? undefined,
      location: response.headers.get("location") ?? undefined,
      retryAfter: parseRetryAfter(response.headers, this.retryAfterCapSeconds),
      pagination: parsePaginationHeaders(response.headers),
      idempotencyReplayed: response.headers.get("idempotency-replayed") === "true",
    };
  }
}

export type SpecGraphPaths = paths;

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new MalformedResponseError("API returned invalid JSON");
  }
}

function isErrorEnvelope(value: unknown): value is { error: { code: string; message: string; request_id?: string; details: Record<string, unknown> } } {
  if (!value || typeof value !== "object" || !("error" in value)) {
    return false;
  }
  const error = (value as { error: unknown }).error;
  return Boolean(error && typeof error === "object" && "code" in error && "message" in error);
}

function combineSignals(primary: AbortSignal, secondary?: AbortSignal): AbortSignal {
  if (!secondary) {
    return primary;
  }
  const controller = new AbortController();
  const abort = () => controller.abort();
  primary.addEventListener("abort", abort, { once: true });
  secondary.addEventListener("abort", abort, { once: true });
  return controller.signal;
}

function delay(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(resolve, ms);
    signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(timeout);
        reject(new RequestAbortError("Request was aborted"));
      },
      { once: true },
    );
  });
}

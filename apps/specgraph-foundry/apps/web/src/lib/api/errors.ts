export type ApiErrorCode = string;

export class SpecGraphApiError extends Error {
  readonly kind = "api";

  constructor(
    readonly status: number,
    readonly code: ApiErrorCode,
    message: string,
    readonly requestId?: string,
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export class AuthenticationRequiredError extends Error {
  readonly kind = "auth";
}

export class DependencyFailureError extends Error {
  readonly kind = "dependency";
}

export class MalformedResponseError extends Error {
  readonly kind = "malformed-response";
}

export class RequestTimeoutError extends Error {
  readonly kind = "timeout";
}

export class RequestAbortError extends Error {
  readonly kind = "abort";
}

export type SpecGraphClientError =
  | SpecGraphApiError
  | AuthenticationRequiredError
  | DependencyFailureError
  | MalformedResponseError
  | RequestTimeoutError
  | RequestAbortError;

export function normalizeUnknownError(error: unknown): SpecGraphClientError {
  if (
    error instanceof SpecGraphApiError ||
    error instanceof AuthenticationRequiredError ||
    error instanceof DependencyFailureError ||
    error instanceof MalformedResponseError ||
    error instanceof RequestTimeoutError ||
    error instanceof RequestAbortError
  ) {
    return error;
  }
  if (error instanceof DOMException && error.name === "AbortError") {
    return new RequestAbortError("Request was aborted");
  }
  return new DependencyFailureError("Request failed");
}

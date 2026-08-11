export function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: {
      "content-type": "application/json",
      "x-request-id": "request-123",
      ...init.headers,
    },
  });
}

export function errorEnvelope(code = "VALIDATION_ERROR") {
  return {
    error: {
      code,
      message: "safe message",
      request_id: "request-123",
      details: {},
    },
  };
}

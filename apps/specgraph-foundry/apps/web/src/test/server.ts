import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const server = setupServer(
  http.get("/api/atropos/status", () =>
    HttpResponse.json({
      online: false,
      jarPath: null,
      workspace: "/test-workspace",
      detail: "test engine unavailable",
      remedy: "use the test engine fixture",
      durationMs: 0,
      allowedCommands: ["/home"],
      checkedAt: "2026-01-01T00:00:00.000Z",
    }),
  ),
  http.get("/api/atropos/recovery", () =>
    HttpResponse.json({
      available: true,
      repaired: false,
      notice: null,
      failure: null,
      detail: null,
      remedy: null,
      checkedAt: "2026-01-01T00:00:00.000Z",
    }),
  ),
);

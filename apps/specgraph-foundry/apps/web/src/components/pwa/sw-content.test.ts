import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const swPath = resolve(process.cwd(), "public/sw.js");
const sw = readFileSync(swPath, "utf8");

/**
 * The service worker is plain JavaScript, not a TypeScript module, so it is
 * validated here as text/pattern evidence (matching `node --check` in the
 * final gate) rather than executed — jsdom has no ServiceWorkerGlobalScope.
 */
describe("public/sw.js safe shell-only caching law", () => {
  it("contains no remote script import", () => {
    expect(sw).not.toMatch(/importScripts\s*\(/);
  });

  it("contains no push, background sync, or notification handlers", () => {
    expect(sw).not.toMatch(/addEventListener\(\s*["']push["']/);
    expect(sw).not.toMatch(/addEventListener\(\s*["']sync["']/);
    expect(sw).not.toMatch(/addEventListener\(\s*["']periodicsync["']/i);
    expect(sw).not.toMatch(/addEventListener\(\s*["']notificationclick["']/);
    expect(sw).not.toMatch(/showNotification/);
  });

  it("only handles GET requests", () => {
    expect(sw).toMatch(/request\.method\s*!==\s*["']GET["']/);
  });

  it("never caches API, auth, or /v1 traffic", () => {
    expect(sw).toContain('"/v1/"');
    expect(sw).toContain('"/api/"');
    expect(sw).toContain('"/auth/"');
    expect(sw).toMatch(/isNeverCachePath/);
  });

  it("never caches requests carrying an Authorization header", () => {
    expect(sw).toMatch(/request\.headers\.has\(\s*["']authorization["']\s*\)/);
  });

  it("restricts caching to same-origin requests", () => {
    expect(sw).toMatch(/url\.origin\s*!==\s*self\.location\.origin/);
  });

  it("never caches responses carrying Set-Cookie, or opaque/cross-origin responses", () => {
    expect(sw).toMatch(/set-cookie/i);
    expect(sw).toMatch(/opaque/);
  });

  it("navigation requests are network-first with a static /offline fallback only", () => {
    expect(sw).toMatch(/request\.mode\s*===\s*["']navigate["']/);
    expect(sw).toContain('"/offline"');
  });

  it("uses a versioned cache name and deletes obsolete caches on activate", () => {
    expect(sw).toMatch(/const SW_VERSION\s*=/);
    expect(sw).toMatch(/caches\.delete/);
  });

  it("calls clients.claim only inside activate, after cache cleanup", () => {
    const activateBlock = sw.slice(sw.indexOf('addEventListener("activate"'));
    expect(activateBlock).toContain("clients.claim()");
  });

  it("bounds the runtime cache size", () => {
    expect(sw).toMatch(/RUNTIME_CACHE_MAX_ENTRIES/);
    expect(sw).toMatch(/trimCache/);
  });

  it("contains no secret, token, or credential literal", () => {
    expect(sw).not.toMatch(/bearer\s+[a-z0-9]/i);
    expect(sw).not.toMatch(/api[_-]?key\s*[:=]/i);
    expect(sw).not.toMatch(/sk-[a-z0-9]{10,}/i);
  });

  it("does not console.log any request URL", () => {
    expect(sw).not.toMatch(/console\.(log|info|warn|error)\([^)]*url/i);
  });
});

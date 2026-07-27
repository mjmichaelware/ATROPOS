/*
 * SpecGraph Foundry shell-only service worker.
 *
 * Scope: caches only the static offline shell and immutable hashed
 * /_next/static assets. It never caches API/auth/domain traffic, POST/PUT/
 * PATCH/DELETE requests, Authorization-bearing requests, Set-Cookie
 * responses, signed URLs, export downloads, source uploads, or any
 * authenticated HTML. No push, no background sync, no notifications, no
 * offline mutation replay. No remote script is imported.
 */

const SW_VERSION = "specgraph-shell-v1";
const SHELL_CACHE = `${SW_VERSION}-shell`;
const RUNTIME_CACHE = `${SW_VERSION}-runtime`;
const CURRENT_CACHES = new Set([SHELL_CACHE, RUNTIME_CACHE]);
const OFFLINE_URL = "/offline";
const RUNTIME_CACHE_MAX_ENTRIES = 60;

const NEVER_CACHE_PATH_PREFIXES = ["/v1/", "/api/", "/auth/"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll([OFFLINE_URL]))
      .catch(() => {
        // A failed shell precache must not block install; the offline
        // fallback simply will not be available until a later successful run.
      }),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((name) => name.startsWith("specgraph-shell-") && !CURRENT_CACHES.has(name)).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  );
});

function isNeverCachePath(pathname) {
  return NEVER_CACHE_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

function isImmutableStaticAsset(url) {
  return url.pathname.startsWith("/_next/static/");
}

function isCacheableResponse(response) {
  if (!response || !response.ok) return false;
  if (response.type === "opaque" || response.type === "opaqueredirect") return false;
  if (response.type === "cors") return false; // same-origin only
  if (response.headers.has("set-cookie")) return false;
  return true;
}

async function trimCache(cacheName, maxEntries) {
  const cache = await caches.open(cacheName);
  const keys = await cache.keys();
  if (keys.length <= maxEntries) return;
  const excess = keys.length - maxEntries;
  for (let i = 0; i < excess; i += 1) {
    await cache.delete(keys[i]);
  }
}

async function networkFirstNavigation(request) {
  try {
    const response = await fetch(request);
    return response;
  } catch {
    const cache = await caches.open(SHELL_CACHE);
    const offline = await cache.match(OFFLINE_URL);
    if (offline) return offline;
    return new Response("Offline", { status: 503, statusText: "Offline" });
  }
}

async function cacheFirstStatic(request) {
  const cache = await caches.open(RUNTIME_CACHE);
  const cached = await cache.match(request);
  if (cached) return cached;
  try {
    const response = await fetch(request);
    if (isCacheableResponse(response)) {
      await cache.put(request, response.clone());
      trimCache(RUNTIME_CACHE, RUNTIME_CACHE_MAX_ENTRIES);
    }
    return response;
  } catch (error) {
    if (cached) return cached;
    throw error;
  }
}

self.addEventListener("fetch", (event) => {
  const { request } = event;

  // Only GET is ever handled; every other method passes straight through
  // to the network with no caching involvement whatsoever.
  if (request.method !== "GET") return;

  const url = new URL(request.url);

  // Same-origin only. Cross-origin (Supabase, fonts, anything else) is
  // never intercepted or cached by this worker.
  if (url.origin !== self.location.origin) return;

  // Never cache Authorization-bearing requests, regardless of path.
  if (request.headers.has("authorization")) return;

  if (isNeverCachePath(url.pathname)) return;

  if (request.mode === "navigate") {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (isImmutableStaticAsset(url)) {
    event.respondWith(cacheFirstStatic(request));
    return;
  }

  // Everything else (including the manifest, icons, and any other GET) is
  // left to the network with no service-worker caching involvement.
});

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

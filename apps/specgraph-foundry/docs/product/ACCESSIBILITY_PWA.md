# Accessibility Conformance and Safe Shell-Only PWA (Group 18)

Concise product/architecture record for Group 18. Extend this document in Group 19+ rather than re-deriving it.

## WCAG target and non-certification disclaimer

Implementation target is WCAG 2.2 AA, using the WAI-ARIA Authoring Practices Guide (APG) for widget behavior. **This document does not claim formal WCAG conformance.** Automated tooling (axe-core, in both a jsdom component harness and a real-browser Playwright run where available) finds a subset of possible issues — historically well under half of WCAG success criteria are mechanically checkable. A conformance claim requires a structured manual audit against every applicable success criterion, including real assistive-technology testing, which this group did not perform. What follows is real code-level repair work plus automated evidence, not a certification.

## Keyboard model

- Every action in the audited surfaces is reachable and operable without a pointer: buttons, tabs, dialogs, and form controls all use native or Radix-managed keyboard handling.
- The shared `Tabs` primitive (`src/components/ui/tabs.tsx`) now implements the WAI-ARIA APG Tabs pattern with **manual activation**: `ArrowLeft`/`ArrowRight` move focus among tabs (roving `tabindex`, wrapping at the ends) without activating them; `Home`/`End` jump to the first/last tab; `Enter`/`Space` (native button activation) selects the focused tab. Manual activation was chosen deliberately because activating a tab here can trigger real data fetching — arrow-browsing must never fire that as a side effect.
- Dialogs and sheets (`src/components/ui/dialog.tsx`, `sheet.tsx`, `mobile-navigation.tsx`) are all built on `@radix-ui/react-dialog`, which provides native-equivalent focus trapping, `Escape`-to-close, and focus restoration to the trigger element — verified directly in `mobile-navigation.test.tsx` rather than assumed.
- No single-character keyboard shortcut exists anywhere in the app, so there is nothing that could fire while a user is typing in a text input.

## Focus model

- `.sg-button`, `.sg-tab`, and nav links all carry Group 17's `:focus-visible` outline (`outline: 3px solid var(--sg-focus)`), which remains visible across dark, light, high-contrast, `forced-colors`, and reduced-transparency because `--sg-focus` is redefined per theme rather than hardcoded.
- Focused elements are not hidden behind sticky shell chrome: the header is sticky with a bounded height, and the one persistent overlay this group adds (`.sg-update-banner`) is only shown for the rare "an update is ready" state, not permanently.
- `viewport-fit=cover` was added to `layout.tsx`'s viewport export — without it, the `env(safe-area-inset-*)` tokens Group 17 introduced were silently inert on notched/rounded-corner devices; this is a real fix, not new scope.

## Screen-reader and live-region model

- `role="status"`/`aria-live="polite"` is used for real asynchronous state (operation results, form confirmations, connectivity transitions); `role="alert"` is reserved for errors that need immediate interruption. Both patterns already existed from Groups 11–16 and are reused, not duplicated.
- `PwaRegistration` (`src/components/pwa/pwa-registration.tsx`) announces exactly two kinds of state change — an online/offline transition and a waiting-update — and only when the state actually changes (`lastOnline.current !== online`), never on every render or every repeated offline event; `pwa-registration.test.tsx` asserts this deduplication directly by firing the same `offline` event twice and checking the announcement text is not re-triggered/duplicated.
- Next.js App Router's built-in route-change focus/announcement behavior was inspected before adding anything: no custom route announcer was added, avoiding a duplicate announcement on every navigation.

## Forms and errors

- Every form field in the audited surfaces uses `<Field>`/`<label htmlFor>` pairing (pre-existing convention, verified still intact).
- The sign-in password-visibility toggle (`sign-in-form.tsx`) previously had `aria-label="Toggle password visibility"` on a button whose visible text was "Show password"/"Hide password" — a WCAG 2.5.3 (Label in Name) violation, since the visible label text was not contained in the accessible name. Fixed by removing the mismatched `aria-label` (the dynamic visible text is already a sufficient, accurate accessible name) and adding `aria-pressed` so the toggle's state is exposed per WCAG 4.1.2.
- The mobile navigation trigger had the same class of issue (`aria-label="Open navigation"` on a button labeled "Menu") — fixed the same way. `Dialog.Content`'s redundant `aria-label="Mobile navigation"` was also removed since Radix already wires `aria-labelledby` to the visible `Dialog.Title` ("Navigation"); keeping both left two different, conflicting accessible-name sources.
- Autocomplete attributes (`autoComplete="email"`, `"current-password"`) were already present from earlier groups and are unchanged; password managers and paste are never blocked.

## Graph and dense-data accessibility

- The Research gap matrix (`src/components/research/gap-matrix.tsx`) previously used `role="grid"`/`role="row"` on a structure that does not implement grid keyboard navigation and does not satisfy the ARIA `grid`/`row` required-children relationship (a `<header>` sibling alongside cell content, and cells nested two levels deep without `gridcell` roles) — this was a real `aria-required-children` (critical) violation caught by the new axe test. Fixed by removing the `grid`/`row` roles entirely rather than fully building out grid semantics the widget doesn't behave as (no arrow-key cell navigation exists); it is now an honestly-labeled collection of independently focusable status buttons.
- `SourceTable` (`src/components/sources/source-table.tsx`) used `role="list"` with children that were not `role="listitem"` — another real `aria-required-children` violation, fixed by wrapping each `SourceCard` in an explicit `role="listitem"` container.
- The Execution node table and the Graph accessible list (Group 14/15/17 work) are unchanged in this group; their bounded-output and non-color status-distinction properties were verified, not rebuilt.

## Contrast and token evidence

`src/styles/contrast.test.ts` computes real WCAG relative-luminance contrast ratios (no eyeballing) for the literal hex values in `tokens.css`/`themes.css`, for dark, light, and high-contrast themes. This audit found one real AA failure: light-theme `--sg-text-muted` (`#69786f` on `#fffaf0`) measured 4.47:1, just under the 4.5:1 normal-text threshold. Retuned to `#586760` (5.73:1 on surface, 5.28:1 on canvas) — a darker shade in the same sage-green family, so the visual identity is preserved. All 25 token-pair tests pass against the corrected values.

## Automated audit coverage and limitations

- `src/test/axe.ts` wraps `axe-core` for component-level checks, disabling exactly two rules (`color-contrast`, `css-orientation-lock`) with a documented reason: jsdom has no real layout/paint engine and cannot evaluate either. No other rule is disabled. Real contrast evidence instead comes from `contrast.test.ts`.
- Component-level axe checks now run against representative rendered states: the authenticated project command center, the source workspace, the research workspace (including the just-fixed gap matrix), the graph workspace's accessible list view, the handoff workspace, execution run detail, the routing workspace's paid-unlock **confirmation dialog specifically** (not just its idle state), and the sign-in form.
- `e2e/accessibility-pwa.spec.ts` additionally defines an `@axe-core/playwright` scan across `/projects`, `/projects/new`, `/projects/project-1`, `/projects/project-1/sources`, `/auth/sign-in`, and `/offline` — a real-browser check, not a jsdom approximation — but see "Unverified" below for whether it actually executed in this environment.

## Manual screen-reader test checklist (not performed in this environment)

The following is the intended manual verification checklist for a future session with real device/screen-reader access — none of it has been executed:

- **NVDA + Firefox/Chrome (Windows)**: sign in, open a project, navigate Sources/Research/Graph/Handoff/Routing via the rail and via Tab, open and close the mobile nav sheet and the paid-unlock confirmation dialog, submit a form with a validation error, trigger and dismiss an offline/online announcement.
- **VoiceOver + Safari (macOS/iOS)**: same journey, including rotor-based landmark/heading navigation and the graph's accessible list as a VoiceOver rotor list.
- **TalkBack + Chrome (Android)**: same journey with touch-explore, focused on the mobile nav sheet, tab swipe gestures on the routing/handoff tab strips, and the offline page.

## Safe shell-only caching law

The service worker (`public/sw.js`) caches only two categories of resource: the static `/offline` shell page (precached on install) and immutable, hashed `/_next/static/*` assets (bounded runtime cache-first, capped at `RUNTIME_CACHE_MAX_ENTRIES` entries with FIFO trimming). Everything else is left to the network with zero service-worker involvement.

## Explicit private-data cache exclusions

The fetch handler returns early (no caching, no interception) for: any non-`GET` request; any request whose origin is not same-origin; any request carrying an `Authorization` header; and any request whose path starts with `/v1/`, `/api/`, or `/auth/`. The `isCacheableResponse` guard additionally refuses to cache any response carrying `Set-Cookie`, and any `opaque`/`opaqueredirect`/`cors`-type response (cross-origin). Concretely, this means: no API response, no authenticated HTML, no Supabase traffic, no signed download URL, no export artifact, no source upload, no project/research/plan/execution/routing payload is ever written to Cache Storage. `src/components/pwa/sw-content.test.ts` asserts these patterns are present in the literal service-worker source as a static/text check (jsdom has no `ServiceWorkerGlobalScope` to execute it against).

## Manifest and icons

`src/app/manifest.ts` uses `MetadataRoute.Manifest` with truthful `name`/`short_name`/`description`, `start_url: "/"`, `scope: "/"`, `display: "standalone"`, and the stable opaque Group 17 shell color (`#07100d`) for both `theme_color` and `background_color`. No `shortcuts`, `screenshots`, `share_target`, or `protocol_handlers` are declared — there are no real screenshots to reference and no authenticated project id would be safe to hardcode into a shortcut. Icons: `icon-192.png`, `icon-512.png` (purpose `any`), and `icon-maskable-512.png` (purpose `maskable`, content confined to a 72%-scale centered safe zone) — all three generated deterministically from the existing `public/icon.svg` design (same background/line/dot colors) using only the Python standard library (`struct`+`zlib` for raw PNG encoding), run as a one-off local command; no generator script was committed and no dependency was added.

## Service-worker lifecycle and update behavior

`components/pwa/pwa-registration.tsx` registers `/sw.js` with `updateViaCache: "none"` only when `process.env.NODE_ENV === "production"`, feature-detecting `"serviceWorker" in navigator` first. It never calls `skipWaiting()` or force-reloads on its own: when a new worker reaches the `installed` state while an existing controller is active, it is surfaced as a `role="status"` announcement plus a visible "Refresh to update" button; only an explicit click posts `{ type: "SKIP_WAITING" }` to the waiting worker, and the page only reloads after a real `controllerchange` event confirms the new worker has taken control. Registration failure is caught and silently non-fatal — no raw exception text is rendered or logged. All listeners are attached inside `useEffect` and the effect's cleanup guards against state updates after unmount via a `cancelled` flag.

## Offline-page behavior

`src/app/offline/page.tsx` is a plain server component (no `"use client"`, no JavaScript required for its core explanation): a heading, two paragraphs (network-unavailable explanation, explicit statement that project/source/research/plan/execution data is intentionally not stored offline), and two real `<Link>` anchors — "Retry" (re-requests `/offline` itself) and "Return to SpecGraph Foundry" (`/`). It is statically prerendered by Next.js (confirmed via `next build` output showing `○ /offline`), reuses the existing `.sg-card`/`.sg-shell` visual system, and is the sole navigation fallback the service worker ever serves.

## Installability conditions

A manifest with valid icons, `display: "standalone"`, and HTTPS (or localhost) are the standard browser-side installability conditions; this group satisfies the manifest/icon/service-worker side of that. No custom install-prompt UI was added — the task explicitly disallows a UA-sniffed fake install prompt, and no real cross-platform-safe `beforeinstallprompt`-equivalent capture was added in this pass, so there is no custom prompt at all, by design.

## Group 18 vs. Groups 19–20 boundary

Group 18 changed zero query/mutation logic, zero API contracts, zero domain calculations, and zero graph/authority/execution semantics — only navigation-territory presentation, ARIA correctness, focus/keyboard behavior, contrast tokens, and additive shell-only PWA files. Deployment automation, hosted acceptance, CI gating, and rollback procedures belong entirely to Groups 19–20 and are untouched here.

## Unverified

- **No formal WCAG 2.2 AA conformance is claimed.** Automated tooling plus targeted manual code review is not a conformance audit.
- **No manual screen-reader session was performed** with NVDA, VoiceOver, or TalkBack in this environment — the checklist above is a plan, not evidence.
- **Real-browser Playwright execution of `e2e/accessibility-pwa.spec.ts` did not complete in this environment.** Chromium is installed and available (confirmed at `/opt/pw-browsers`), but the required dev server could not start because `NEXT_PUBLIC_SPECGRAPH_API_URL`/`NEXT_PUBLIC_SUPABASE_URL` are not configured in this sandbox (`Error: Public web configuration is invalid`) — a pre-existing environment limitation unrelated to Group 18's code. `npm run e2e:list` (144 total specs, 29 new) is the verification that actually ran.
- **No hosted/deployed instance was visited.** No installability was tested on a real device or browser install surface. No physical-device screen-reader interaction occurred.

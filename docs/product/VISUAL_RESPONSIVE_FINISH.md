# Visual, Responsive, Motion, and Interaction Finish (Group 17)

Concise product/architecture record for Group 17. It documents what "persistently native" means in this codebase, the systems built to achieve it, and — honestly — what was verified locally versus what remains unverified. Extend this document in Group 18+ rather than re-deriving it; the HUD design language and competitive scan that originated it live in `docs/product/GRAPH_STUDIO_FOUNDATION.md`.

## Persistently native, defined

One component system that adapts continuously to viewport, container size, input modality, orientation, pixel density, theme, and motion preference — with no user-agent sniffing, no device-name branches, no isolated phone/tablet/desktop forks, and no route or navigation truth duplicated across surfaces. A layout that only works at 320/375/768/1024/1440 is a test result, not the architecture; the CSS in this group is written to remain coherent continuously between and beyond those points.

## Central navigation model

`apps/web/src/components/navigation/routes.ts` is the single source of route identity for the whole app: `projectRoute`, `projectSourcesRoute`, `projectDocumentRoute`, `projectResearchRoute`, `projectTaskRoute`, `projectGraphRoute`, `projectHandoffRoute`, `projectExecutionRoute`, and `projectRoutingRoute` are the only place a `/projects/${id}/...` string is assembled. `projectSections` describes every navigable section (id, label, route-context accent, project-aware builder, active-match rule). `use-nav-items.ts` computes one shared `{ global, project }` item list — with `href` and `active` state already resolved — consumed identically by `app-header.tsx`, `app-sidebar.tsx`, and `mobile-navigation.tsx` via the shared `<NavLinks>` renderer, so a route can never drift out of sync between the header, the rail, and the mobile sheet. `project-command-center.tsx`, `project-card.tsx`, `project-create-form.tsx`, `task-card.tsx`, `atom-card.tsx`, `source-card.tsx`, and `handoff/execution-run-list.tsx` were all converted from ad hoc template-literal paths to these builders; `navigation.test.ts` asserts route builders never hardcode a literal `/projects/${id}` outside the registry itself.

`route-accent.tsx` derives the active project section from the URL (no client-inferred eligibility, just pathname matching against the same registry) and tags `<html data-accent="...">`, which `themes.css` reads to shift the shell's ambient accent per section without ever touching semantic success/warning/danger tokens.

## Intrinsic, container-responsive architecture

Viewport `@media` queries are kept only as last-resort outer-shell safeguards (documented per-use in `globals.css`), gated behind `@supports (container-type: inline-size)` with a full non-container fallback. The shell nav (rail vs. mobile sheet) now responds to `@container sg-shell` — the shell's own measured width — rather than a bare viewport guess, so it degrades correctly inside a split-screen or embedded frame too. Card grids (`.sg-grid`, `.sg-project-grid`, `.sg-counts`) use `repeat(auto-fit, minmax(...))` instead of hardcoded column counts. The Projects command-center readiness/counts/latest trio is a bento composition (`.sg-bento`) where the primary card earns extra width only once its own container (not the viewport) has room, via a second, nested `@container` query. `.sg-graph-table` (used by execution node tables and the graph accessible list) keeps native `display: table` column alignment but adds `overflow-x: auto` directly on the table's own generated wrapper box, so a wide table scrolls within itself instead of forcing page-level horizontal overflow — no JS measurement needed. `.sg-page-heading` gained `flex-wrap: wrap` so an action-button row (now longer, since it's generated from the section registry) never overflows a narrow container.

## Fluid type, spacing, and size system

`styles/tokens.css` adds a bounded `clamp()` scale — `--sg-type-2xs` through `--sg-type-display`, `--sg-space-fluid-sm/md/lg`, `--sg-gutter`, `--sg-measure`/`--sg-measure-narrow` (ch-based reading width) — layered on top of the existing fixed `--sg-space-1..7`/`--sg-type-xs..xl` tokens, which are kept untouched for backward compatibility with every component written in Groups 11–16. No external font was added; the existing system font stack is unchanged. `--sg-touch-target` (44px) is now applied to every `.sg-button`, nav link, and `.sg-tab`, not just buttons.

## Semantic OKLCH theme system

`--sg-semantic-{source,research,planning,verified,blocked,unknown}` are defined in `oklch()` behind `@supports (color: oklch(...))`, falling back to the existing hex `--sg-graph-*` tokens on browsers without OKLCH support. Hover/pressed/subtle-background/border/glow variants are derived with `color-mix()` behind a matching `@supports` gate, with a flat fallback block for browsers without `color-mix()`. Dark remains the default; light (`themes.css`) is tuned independently — softer/warmer shadows, a hairline-tint glow instead of a bloom, a fully opaque graph panel, a distinct focus color — not an inverted dark palette. High-contrast defines its own flat semantic palette. `forced-colors: active` maps every semantic/graph token to `CanvasText`/`Canvas`/`Highlight` system colors and strips shadows/glow, with explicit `ButtonText`/`ButtonFace`/`Highlight` treatment for buttons. `prefers-reduced-transparency: reduce` zeroes `--sg-blur` and forces the header/mobile sheet/HUD panels to an opaque background instead of `backdrop-filter`.

## Material and depth

The existing HUD material system (`components/visual/{hud-panel,hud-frame,data-grid,signal-line,command-dock,morphing-panel,reveal,metric-orbit}.tsx`, already in production use across Sources/Research/Handoff since Groups 12–16) is preserved and reused, not replaced. `.sg-card`/`.sg-button` gained real elevation transitions (`box-shadow`/`border-color`/`transform` on hover, press, and `data-selected`), and `.sg-project-card` gains a lift-on-hover treatment gated to `(any-hover: hover) and (any-pointer: fine)` so it never becomes an essential, pointer-only affordance.

## Motion grammar

Tokenized in `tokens.css`: `--sg-duration-{instant,press,reveal,morph}` (alongside the existing `fast/normal/slow`), `--sg-ease-{standard,spring,decel}`, `--sg-distance-{xs,sm,md}`, `--sg-scale-{press,lift}`, `--sg-stagger-step/max`. `utilities.css` exposes `.sg-pressable` (PRESS category — applied to buttons, tabs, and nav links) and `.sg-selectable`/`[data-selected]` (SELECTION category, a precise non-color focus frame). `prefers-reduced-motion: reduce` collapses every duration/distance/scale token to near-zero *and* forces `animation-duration`/`transition-duration` to 1ms globally as a backstop — components never need their own reduced-motion branch, the tokens already collapsed. `components/ui/motion.ts` adds `motionTierForWorkload(itemCount)` — an explicit, workload-driven (never device-driven) adaptive-performance policy (`full`/`reduced`/`minimal`) and `useManagedAnimation`, a Web Animations API hook that cancels its animation on unmount or dependency change so no orphaned `Animation` object or DOM reference survives a re-render.

## View Transition progressive enhancement

`components/ui/view-transition.ts` exposes `supportsViewTransitions()` (feature detection only) and `runViewTransition`/`useViewTransition`, which route a state update through `document.startViewTransition` — the platform API only, never React's experimental `<ViewTransition>` — when the browser supports it and motion is not reduced, and apply the update immediately otherwise. The update is wrapped in `flushSync` inside the transition callback, which is required so the "after" DOM snapshot the browser captures reflects the real post-update state rather than racing an async/batched React render. This is wired into the shared `Tabs` component (`components/ui/tabs.tsx`), used by Handoff, Execution's parent workspace, Routing, and Sources — a tab switch now gets a real cross-fade continuity where the platform supports it, and an immediate, correct switch everywhere else (no browser support, reduced motion, or a thrown exception all fall back to the same immediate path).

## Interaction quality

Every `.sg-button` now has default, hover (`:hover:not([disabled])`), pressed (`:active`, collapses under `any-pointer: coarse` and `prefers-reduced-motion`), focus-visible (pre-existing outline, unchanged), disabled, and loading (`aria-busy`, pre-existing) states, plus a `min-width` matching the existing `min-height: 44px` touch target. Nav links (header/sidebar/mobile) gained the same touch-target sizing plus a real `[data-active]` (not just color) treatment — a background tint and border, so active state survives `forced-colors` and color-blind viewing. `.sg-hover-reveal` (new utility) only hides content behind hover when both `any-hover: hover` and `any-pointer: fine` are true, and always reveals it on `:focus-visible` — so no information or action is ever hover-only or pointer-only.

## Adaptive performance policy

Driven entirely by real workload size (`motionTierForWorkload`) and real browser capability media features (`prefers-reduced-motion`, `prefers-reduced-transparency`, `forced-colors`, `any-hover`, `any-pointer`) — never a user-agent string, device name, or hardware benchmark. The existing Group 14 large-graph safe-mode system (`src/lib/graph/zoom.ts`, fixture-driven 100/1,000/10,000-node budgets) is unchanged and untouched by this group; it already implements exactly this law for the graph canvas specifically.

## Surface-by-surface changes

- **Projects**: command-center action row and section links now come from the shared registry; readiness/counts/latest form a bento composition that earns extra width by its own container's space, not the viewport; the project-count grid is `auto-fit`.
- **Sources**: no route/behavior change beyond registry adoption in `source-card.tsx` (long-hash wrapping via the pre-existing `.sg-mono`/`.sg-hash` classes was already correct from Group 12 and is unchanged).
- **Research**: registry adoption in `atom-card.tsx`/`task-card.tsx`; shared `Tabs`/motion/press improvements apply transitively wherever Research uses them.
- **Graph/Planning**: untouched semantically — no React Flow, layout, ELK, or cycle-detection code was modified. Shared motion/press tokens apply transitively through `components/ui/*` and `globals.css` only.
- **Handoff**: registry adoption in `execution-run-list.tsx`; the shared `Tabs`/table/press/motion improvements apply to its Overview/Bindings/Exports/Runs tabs.
- **Execution**: `.sg-graph-table` (its node table) gained the local-scroll/overflow-wrap treatment described above.
- **Routing**: its five-tab `Tabs` instance (Policy/Providers/Renderers/Unlocks/Decisions) gains the tab-panel view-transition and press states; paid-unlock confirmation flow (Review → Confirm) is unchanged — still never automatic, still requires two deliberate actions.
- **Auth**: no changes were made in this group beyond what shared `components/ui`/`globals.css` changes apply transitively (buttons, focus rings). No auth/session behavior was touched.

## Representative test dimensions vs. continuous-layout law

`e2e/visual-responsive.spec.ts` exercises 320×568, 375×812, an 812×375 phone-landscape, 768×1024, 1024×768, 1366×768, 1440×900, and a 2560×1080 ultra-wide sample — but these are **verification samples**, not the architecture. The CSS backing them (container queries, `clamp()`, `auto-fit`/`minmax()`, `overflow-wrap`, logical properties) is written to hold at every width in between and beyond, not just these eight points.

## Verified local evidence

- `npx tsc --noEmit`: clean.
- `npx eslint . --max-warnings=0`: clean.
- `npx vitest run`: full suite passing (exact count in the final gate section of the commit's report), including new `navigation.test.ts`, `route-accent.test.tsx`, `motion.test.ts`, `view-transition.test.ts`, and `stubMatchMedia`-updated `handoff.test.tsx`/`research.test.tsx`/`routing.test.tsx`/`sources.test.tsx`.
- `npm run e2e:list`: all specs list without a parse error, no browser installed or run.
- `npm run build:termux`: production build succeeds and includes every existing route.

## Unverified

No browser screenshot was captured or compared in this environment. No physical device (phone, tablet, foldable) was used. No hosted/deployed instance was visited. This group did not perform an accessibility audit (that is Group 18's scope) — interaction-state and focus-visible work here is a visual/motion contribution toward that goal, not a conformance claim. No PWA/installability/service-worker/offline behavior was touched or claimed; that also belongs to Group 18. Container-query and `oklch()`/`color-mix()` browser support was verified only by reading the CSS spec and adding `@supports` fallbacks — not by testing in an actual unsupporting browser.

## Group 17 / Group 18 boundary

Group 17 is a visual, responsive, motion, and interaction finish over the application's existing, behaviorally-complete surfaces. It changed zero query/mutation logic, zero API contracts, zero domain calculations, and zero graph/authority/execution semantics. Group 18 (Accessibility and PWA) owns: WCAG conformance auditing (axe, manual screen-reader passes), the accessible-authentication requirements, and all installability/service-worker/offline/manifest work — none of which is claimed or attempted here.

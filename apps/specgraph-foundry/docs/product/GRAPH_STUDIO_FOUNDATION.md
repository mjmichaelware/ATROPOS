# Graph Studio Foundation (Group 14)

Bounded product specification for the Interactive Graph Foundation. This document records the competitive scan performed before implementation, the original design system it produced, and the explicit Group 14 / Group 15 boundary. It is the implementation reference for Group 14 — later groups should extend it rather than re-deriving it.

## Competitive graph UX findings

Evaluated from established public product behavior and official documentation/demo patterns (no proprietary code, assets, layouts, icons, or animation sequences were copied). Findings are deliberately narrow — one strength, one weakness, one SpecGraph opportunity, one anti-pattern per product — not a market report.

| Product | Strongest interaction | Strongest visual decision | Strongest navigation decision | Mobile strength | Accessibility strength | Major weakness | SpecGraph opportunity | Pattern not to copy |
|---|---|---|---|---|---|---|---|---|
| n8n | Drag-connect node wiring with live validity feedback | Muted neutral canvas that keeps node color meaningful | Sticky minimap + fit-to-view | Limited; canvas assumes a pointer | Keyboard node selection exists but node creation is mouse-first | Large workflows become a wall of identical rounded rectangles | Give nodes structural silhouettes, not just color, so type reads at a glance | Nearly all interaction is hover- or drag-gated |
| Langflow | Component palette search reduces node-hunting friction | Clear input/output handle typing on node edges | Breadcrumb-style flow switching | Weak — desktop-only canvas assumptions throughout | Screen-reader canvas support is minimal | Dense flows lose hierarchy; everything sits at one visual weight | Progressive disclosure by zoom tier prevents this flattening | Handle typing shown only as color dots |
| Dify | Inspector panel slides in without losing canvas context | Clean state badges on run nodes (success/error/running) | Left rail keeps app-level context stable while the canvas changes | Read-only mobile views are usable even if editing is not | Status badges pair color with text, which is the right instinct | Node detail requires many clicks to reach through nested panels | A single inspector transition (not nested panels) reaching full detail in one step | Deeply nested modal-over-modal detail drilling |
| Flowise | Fast, responsive drag reflow during connection-making | Distinct node header colors per integration category | One-click "add node here" affordance on hover of an edge | Poor — small touch targets throughout | Minimal — largely unaddressed | Canvas performance degrades noticeably past a few hundred nodes | Semantic zoom + bounded detail rendering solves this directly | Uncapped simultaneous animation on every node during layout |
| React Flow official AI Workflow Editor example | Clean typed node/edge data model as a first-class concept | Restrained, purposeful handle styling | Built-in fit-view and minimap wired correctly out of the box | N/A — reference example, not a product | N/A — reference example | Default visual theme is a generic dev-tool aesthetic if left unstyled | Confirms typed node/edge separation from renderer state is the right foundation to build on | Shipping the default unstyled theme as a finished product |
| Coda-style doc-to-plan / requirements-to-workflow tools (general pattern, no single product copied) | Inline provenance links from a generated step back to its source text | Calm, document-like node framing instead of generic boxes | Linear "next required step" affordance alongside the graph | Usually collapses to a list on narrow viewports, which is honest | List fallback is often the *only* accessible path, not an equal one | Provenance is frequently one hop removed (a tooltip, not a real link) | SpecGraph can make the source/document/atom node type genuinely reachable, not just labeled | Treating the accessible list as a lesser fallback instead of an equal view |

**Apple HIG guidance applied (hierarchy, materials, motion, direct manipulation, navigation, touch targets, accessibility, responsive/adaptive layout):** content precedes chrome; translucent material is reserved for transient/command surfaces, never for primary content; controls always have a resting, pressed, focused, and disabled state; touch targets meet the platform minimum; motion is interruptible and never blocks input; navigation is reversible with a clear way back; layout adapts to available space rather than being cropped or hidden. These principles are applied at a web-application level — no iOS chrome, iconography, or Liquid Glass shapes are imitated.

## Original SpecGraph design principles

1. **Immediate orientation** — project, graph mode, data freshness, scale, and available actions are legible in the first frame.
2. **Direct manipulation without semantic risk** — pan/zoom/select/move feel spatial and immediate; node movement is always visually-scoped and never silently rewrites relationship meaning.
3. **Spatial memory** — layout, selection, filters, and inspector context survive mode switches wherever the underlying record still exists.
4. **Progressive disclosure by zoom tier** — CONSTELLATION (silhouette + structural type/status, topology-first) → LABEL (primary label, type, status, selection-path emphasis) → INSPECTION (richer identity, provenance cue, inspector affordance).
5. **Content before chrome** — the canvas is primary; translucent material is reserved for command docks, filters, inspectors, and sheets.
6. **Meaningful beauty** — every visual property (shape, border treatment, silhouette, directional marker) encodes type, authority, status, hierarchy, selection, or availability. Nothing is decorative without a meaning.

## Interaction model

React Flow (`@xyflow/react`) is the renderer/interaction surface only. TanStack Query owns remote graph data (planning-workspace summary, paginated relations, plan detail). URL search params own graph mode (`authority`/`execution`), search text, active filters, selected node/edge id, and view (`canvas`/`list`). Component state owns transient UI (sheet open/close, layout-control expansion). React Flow's internal store owns viewport, drag, and renderer selection mechanics, which the graph-workspace layer treats as disposable and never trusts as semantic truth.

## Layout model

Deterministic layout only. `elkjs` runs inside a Web Worker (`src/workers/graph-layout.worker.ts`) driven by a typed request/response protocol with a monotonic generation ID; any response whose generation does not match the latest request is discarded. Two deterministic layout modes ship in Group 14: **Blueprint** (ELK `layered`, top-to-bottom, optimized for dependency reading) and **Compact** (ELK `layered` with reduced spacing/`mrtree` fallback, optimized for density). A **Freeform** mode allows manual visual repositioning, persisted only to a client-local layout-preference boundary (no server layout-persistence endpoint exists — this is stated honestly in the UI rather than invented). A **Focus** mode presents the selected node plus its currently loaded neighborhood. Radial/orbit layout is deferred — ELK's radial support was not verified safe/bounded within Group 14's scope, and the mandate explicitly forbids adding a second layout dependency merely for a radial appearance.

## Motion model

Motion is restrained and interruptible: selection settles with a short transform/opacity transition, layout changes interpolate from prior to new coordinates for small graphs and snap immediately for large graphs, fit-graph/fit-selection use a bounded camera transition, and mobile sheets slide from their physical edge. No infinite idle animation, no animated edge traversal without real backing state, no physics simulation. `prefers-reduced-motion` collapses all of the above to immediate/minimal transitions using existing CSS and the Web Animations API already available in the project — no motion library is added.

## Semantic zoom model

Zoom-driven detail tiers (CONSTELLATION/LABEL/INSPECTION) are computed from the current viewport zoom level combined with the active graph's node count, so a 10,000-node graph enters a coarser tier earlier than a 100-node graph at the same zoom. Tier thresholds are pure, tested functions independent of the renderer.

## Mobile model

Phone: full-canvas focus, floating command dock reachable one-handed, filters/layout controls in a bottom sheet, selected node/edge opens a full-height inspector sheet with a drag handle and explicit close action, 44px minimum targets, safe-area padding, minimap collapses when space is insufficient. Tablet: split graph/inspector. Desktop/Chromebook: navigation rail, persistent inspector, keyboard shortcuts, segmented view-mode control.

## Accessibility model

The canvas is never the only interface. Every graph view ships a keyboard-operable, screen-reader-named accessible list view over the same loaded/filtered subset, with bounded output (virtualized/paginated, never an unbounded DOM table). Reduced motion, reduced transparency, and `forced-colors` are supported without relying on glow or color alone — node/edge type distinctions use structural silhouette and edge-marker shape, not color alone.

## Performance adaptation

Deterministic seeded fixtures at 100 / 1,000 / 10,000 nodes drive three bounded interaction modes: 100 nodes renders full interactive detail; 1,000 nodes uses simplified rendering and viewport-aware detail suppression; 10,000 nodes enters an explicit safe mode (coarse tier forced, detailed node rendering capped, large-graph notice shown) rather than attempting full detailed simultaneous rendering. Fixtures are isolated in `src/lib/graph/fixtures.ts`, imported only by tests, never by production data paths.

## Group 14 vs. Group 15 boundary

Group 14 shipped the graph route, renderer/layout/inspector/search/accessible-list foundation, and generic node/edge rendering for whatever categories the real API returns — entirely read-only, with no mutations.

Group 15 ships on top of that foundation, without a second renderer: authority relation creation with real client-side validation and a bounded, deterministic, server-independent cycle *advisory* (never authoritative) over `REQUIRES` relations only; plan synthesis with an explicit, un-preselected open-research choice; plan history, explicit-plan-ID URL restore, and honest handling of a missing/inaccessible explicitly-selected plan; plan verification with real severity/code/message findings, filterable and focusable only onto currently loaded graph nodes; and execution-mode stage/dependency/binding inspection where canonical node readiness is read only from the server's `ready_nodes` list, never derived client-side.

Group 15 does **not** ship: relation editing or deletion, semantic undo/redo, graph-edit change-set previews, graph audit events, execution-cycle *creation* workflows (only advisory detection of a proposed `REQUIRES` cycle), or any client-side verification authority — the server independently validates every mutation, and a rejected mutation leaves the previously rendered graph completely unchanged. Group 16 (execution-run start/control, worker leases, receipts, providers, routing, paid unlocks, exports, handoff) is not implemented and has no controls on the graph route.

## Unverified browser/device requirements

Not claimed or measured in Group 14 or Group 15: 60fps proof on physical hardware, physical Android device behavior, hosted network timing, hosted execution of any mutation, automated axe scan execution, or a measured 2.5-second shell budget. Fixture-based proof (deterministic counts, bounded rendering, worker correctness) and mocked-API component/unit test proof are reported separately from any real-device, hosted-browser, or hosted-network performance/behavior claim, none of which exist yet.

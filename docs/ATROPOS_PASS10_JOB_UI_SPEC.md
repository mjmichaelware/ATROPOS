# ATROPOS Pass 10 — Agent Job UI Spec

Status: UI-only. This document specifies how `/agent run`, `/agent jobs`, and
`/agent job <id>` should *look* once a job runner exists behind them. It does
not wire, parse, or implement those commands — see
`src/main/kotlin/atropos/cli/ui/AgentJobRenderer.kt` for the renderer this
spec describes.

## Status vocabulary

Every job is always in exactly one of these states:

| Status      | Color | Meaning                                             |
|-------------|-------|------------------------------------------------------|
| `queued`    | amber | accepted, not started                                |
| `planning`  | amber | provider is drafting an approach                     |
| `patching`  | amber | provider is generating a diff                        |
| `applying`  | amber | ATROPOS is running `git apply --check` / `git apply` |
| `verifying` | amber | ATROPOS is running deterministic verification         |
| `repairing` | amber | ATROPOS is attempting an automatic repair pass        |
| `passed`    | green | verification passed, changes are in the working tree |
| `failed`    | red   | verification failed and no further repair was possible |
| `refused`   | red   | ATROPOS refused the patch (unsafe path, bad diff, etc.) |

Color mapping follows the existing dashboard legend (green = verified, amber
= pending/in-progress, red = missing/locked/error). There is no separate
"unknown" color for job status — every job always has one of the nine labels
above.

Rendered as a badge: `[queued]`, `[verifying]`, `[passed]`, etc. — lowercase,
square brackets, colored by the table above.

## `/agent run <task>`

Renders a single-job summary immediately after a job is accepted. Compact,
sectioned, no raw data dumps.

```
── AGENT RUN ──
job       job-20260702-0001
status    [queued]
task      Add a one-line comment to README noting ATROPOS owns repo edits
provider  pending
patch     none yet
next      /agent job job-20260702-0001  (check progress)
```

- `provider` shows `pending` (not a guess) until a provider has actually been
  selected for the job.
- `patch` shows `none yet` until a patch id exists — never a fabricated id.
- `next` is always a concrete next command, chosen from the job's current
  status (see "Next command" table below).
- An optional `note` row appears only when the job carries an operator-facing
  note (e.g. a refusal reason once status is `refused`).

## `/agent jobs`

Lists all known jobs, most-recently-updated implementation is a backend
concern — the renderer takes whatever order it's given. Layout adapts to
terminal width:

### 120 columns — full table

```
── AGENT JOBS ──
ID              STATUS       TASK                                                                PROVIDER      UPDATED
job-20260702-…  [verifying]  Add a one-line comment to README noting ATROPOS owns repo edits      github_mod…   17:28:47
job-20260702-…  [passed]     Refactor QuotaLedger cooldown window into a named constant           groq          14:06:22
job-20260702-…  [refused]    Patch attempt for provider fallback ordering in AgentProviderSele…    sambanova     12:00:09
job-20260702-…  [queued]     queued task, not started yet                                         --            17:30:00
4 jobs · /agent job <id> for detail
```

### 80 columns — compact table (no provider/updated columns)

```
── AGENT JOBS ──
ID           STATUS      TASK
job-202607…  [verifying] Add a one-line comment to README noting ATROPOS owns r…
job-202607…  [passed]    Refactor QuotaLedger cooldown window into a named cons…
job-202607…  [refused]   Patch attempt for provider fallback ordering in AgentP…
job-202607…  [queued]    queued task, not started yet
4 jobs · /agent job <id> for detail
```

### 40 columns — stacked list (no table, one job = two lines)

```
── AGENT JOBS ──
[verifying] job-20260702-0001
  Add a one-line comment to README noti…
[passed] job-20260702-0002
  Refactor QuotaLedger cooldown window …
[refused] job-20260702-0003
  Patch attempt for provider fallback o…
[queued] job-20260702-0004
  queued task, not started yet
4 jobs · /agent job <id> for detail
```

An empty job list renders as:

```
── AGENT JOBS ──
no jobs yet · /agent run <task>
```

Never a blank screen and never a fabricated empty-but-successful table.

## `/agent job <id>`

Full detail for one job, plus a timeline of status transitions if the
backend supplies one (timeline is optional — omit the section entirely if
there is no history to show, rather than inventing entries).

```
── AGENT JOB job-20260702-0001 ──
status    [verifying]
task      Add a one-line comment to README noting ATROPOS owns repo edits
provider  github_models
patch     patch-20260702-153012-github_models
changed   1 paths
started   17:28:11
updated   17:28:47

timeline
17:28:11    [queued]
17:28:14    [planning]
17:28:22    [patching]
17:28:35    [applying] git apply --check ok
17:28:47    [verifying]
next      /agent job job-20260702-0001  (check progress)
```

- `changed` shows a path count, never a full diff dump (matches the existing
  `/agent patch` and dashboard PATCH/APPLY card convention — bounded,
  readable, no raw JSON or raw diffs in normal output).
- Timeline notes (e.g. `git apply --check ok`) are short, single-line, and
  wrapped/ellipsized to terminal width like everything else.

## Next command by status

The `next` row is always derived from `status`, never left blank:

| Status                                             | Next command                                  |
|-----------------------------------------------------|------------------------------------------------|
| `queued` / `planning` / `patching` / `applying` / `verifying` / `repairing` | `/agent job <id>  (check progress)` |
| `passed`                                             | `git status --short  (review changes)`        |
| `failed`                                             | `/agent job <id>  (see failure detail)`       |
| `refused`                                            | `/agent job <id>  (see refusal reason)`       |

## Width behavior

All three views are safe at 40, 80, and 120 columns:

- No line ever exceeds the given width — every value is ellipsized
  (`…`) rather than truncated hard or left to overflow.
- At 40 columns, `/agent jobs` drops the table entirely in favor of a
  stacked two-line-per-job list, since a table with 4+ columns cannot fit
  legibly at that width.
- At 80 columns, the table drops the `PROVIDER` and `UPDATED` columns to
  keep `TASK` readable.
- At 120 columns, the full table (`ID`, `STATUS`, `TASK`, `PROVIDER`,
  `UPDATED`) is shown.
- Column padding is computed on visible cell width (not raw string length),
  so ANSI color codes around the status badge never desync column alignment.

## Truthfulness rules (carried over from the Pass 8.5 source doc)

- No fake status: a job only shows `passed` if verification actually
  passed. A job never shows a patch id before one exists.
- No raw JSON dumps in normal output.
- Sample data used to exercise this renderer (see
  `AgentJobRendererPreview` in `AgentJobRenderer.kt`) is clearly isolated,
  deterministic, and never reachable from a real command path.

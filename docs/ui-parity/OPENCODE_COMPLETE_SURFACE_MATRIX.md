# OpenCode Complete Surface Matrix

Generated: 2026-07-27T01:25:34.640399+00:00
Reference: `sst/opencode` @ `7534d23551f665e65080809975b4ca5c7d63807b` (2026-07-25, MIT)
ATROPOS HEAD: `b4c0f5b31b37735eccd9b6418ec95689b1b23a68`
Batch: **A (reference lock + inventory + baseline)**

Machine-readable companion: `OPENCODE_COMPLETE_SURFACE_MATRIX.json`

## Row counts by kind

| Kind | Rows |
|---|---:|
| theme-token-set | 37 |
| dialog | 27 |
| state-context | 22 |
| component | 21 |
| feature-plugin | 17 |
| other | 13 |
| route | 12 |
| prompt | 6 |
| client-package | 6 |
| ui-primitive | 5 |
| theme | 1 |

**Total: 167**

## Row counts by status

| Status | Rows |
|---|---:|
| DISCOVERED | 161 |
| BLOCKED_NO_TARGET_SURFACE | 6 |

## Lifecycle

Every row must pass, in order:

`DISCOVERED → MAPPED → IMPLEMENTED → WIRED → REACHABLE → STATE_CONNECTED →
RESPONSIVE → ACCESSIBLE → PERSISTENCE_VERIFIED → BEHAVIOR_TESTED →
VISUALLY_COMPARED → FAILURE_TESTED → INDEPENDENTLY_VERIFIED`

No row is complete on compilation, screenshots, or agent confidence alone.
All 161 applicable rows are currently at `DISCOVERED`. Zero rows are verified.

## Applicable rows (terminal client)

| Ref | Kind | Reference source path | Status |
|---|---|---|---|
| OC-0001 | other | `packages/tui/src/app.tsx` | DISCOVERED |
| OC-0002 | other | `packages/tui/src/attention.ts` | DISCOVERED |
| OC-0003 | other | `packages/tui/src/audio.d.ts` | DISCOVERED |
| OC-0004 | other | `packages/tui/src/audio.ts` | DISCOVERED |
| OC-0005 | other | `packages/tui/src/clipboard.ts` | DISCOVERED |
| OC-0006 | component | `packages/tui/src/component/bg-pulse-render.ts` | DISCOVERED |
| OC-0007 | component | `packages/tui/src/component/bg-pulse.tsx` | DISCOVERED |
| OC-0008 | component | `packages/tui/src/component/command-palette.tsx` | DISCOVERED |
| OC-0009 | dialog | `packages/tui/src/component/dialog-agent.tsx` | DISCOVERED |
| OC-0010 | dialog | `packages/tui/src/component/dialog-console-org.tsx` | DISCOVERED |
| OC-0011 | dialog | `packages/tui/src/component/dialog-debug.tsx` | DISCOVERED |
| OC-0012 | dialog | `packages/tui/src/component/dialog-mcp.tsx` | DISCOVERED |
| OC-0013 | dialog | `packages/tui/src/component/dialog-model.tsx` | DISCOVERED |
| OC-0014 | dialog | `packages/tui/src/component/dialog-move-session.tsx` | DISCOVERED |
| OC-0015 | dialog | `packages/tui/src/component/dialog-provider.tsx` | DISCOVERED |
| OC-0016 | dialog | `packages/tui/src/component/dialog-retry-action.tsx` | DISCOVERED |
| OC-0017 | dialog | `packages/tui/src/component/dialog-session-delete-failed.tsx` | DISCOVERED |
| OC-0018 | dialog | `packages/tui/src/component/dialog-session-list.tsx` | DISCOVERED |
| OC-0019 | dialog | `packages/tui/src/component/dialog-session-rename.tsx` | DISCOVERED |
| OC-0020 | dialog | `packages/tui/src/component/dialog-skill.tsx` | DISCOVERED |
| OC-0021 | dialog | `packages/tui/src/component/dialog-stash.tsx` | DISCOVERED |
| OC-0022 | dialog | `packages/tui/src/component/dialog-status.tsx` | DISCOVERED |
| OC-0023 | dialog | `packages/tui/src/component/dialog-tag.tsx` | DISCOVERED |
| OC-0024 | dialog | `packages/tui/src/component/dialog-theme-list.tsx` | DISCOVERED |
| OC-0025 | dialog | `packages/tui/src/component/dialog-variant.tsx` | DISCOVERED |
| OC-0026 | dialog | `packages/tui/src/component/dialog-workspace-create.tsx` | DISCOVERED |
| OC-0027 | dialog | `packages/tui/src/component/dialog-workspace-file-changes.tsx` | DISCOVERED |
| OC-0028 | dialog | `packages/tui/src/component/dialog-workspace-list.tsx` | DISCOVERED |
| OC-0029 | dialog | `packages/tui/src/component/dialog-workspace-unavailable.tsx` | DISCOVERED |
| OC-0030 | component | `packages/tui/src/component/error-component.tsx` | DISCOVERED |
| OC-0031 | component | `packages/tui/src/component/logo.tsx` | DISCOVERED |
| OC-0032 | component | `packages/tui/src/component/plugin-route-missing.tsx` | DISCOVERED |
| OC-0033 | component | `packages/tui/src/component/prompt/autocomplete.tsx` | DISCOVERED |
| OC-0034 | component | `packages/tui/src/component/prompt/cwd.ts` | DISCOVERED |
| OC-0035 | component | `packages/tui/src/component/prompt/frecency.tsx` | DISCOVERED |
| OC-0036 | component | `packages/tui/src/component/prompt/history.tsx` | DISCOVERED |
| OC-0037 | component | `packages/tui/src/component/prompt/index.tsx` | DISCOVERED |
| OC-0038 | component | `packages/tui/src/component/prompt/local-attachment.ts` | DISCOVERED |
| OC-0039 | component | `packages/tui/src/component/prompt/move.tsx` | DISCOVERED |
| OC-0040 | component | `packages/tui/src/component/prompt/stash.tsx` | DISCOVERED |
| OC-0041 | component | `packages/tui/src/component/prompt/workspace.tsx` | DISCOVERED |
| OC-0042 | component | `packages/tui/src/component/register-spinner.ts` | DISCOVERED |
| OC-0043 | component | `packages/tui/src/component/spinner.tsx` | DISCOVERED |
| OC-0044 | component | `packages/tui/src/component/startup-loading.tsx` | DISCOVERED |
| OC-0045 | component | `packages/tui/src/component/todo-item.tsx` | DISCOVERED |
| OC-0046 | component | `packages/tui/src/component/use-connected.tsx` | DISCOVERED |
| OC-0047 | component | `packages/tui/src/component/workspace-label.tsx` | DISCOVERED |
| OC-0048 | state-context | `packages/tui/src/context/args.tsx` | DISCOVERED |
| OC-0049 | state-context | `packages/tui/src/context/clipboard.tsx` | DISCOVERED |
| OC-0050 | state-context | `packages/tui/src/context/data.tsx` | DISCOVERED |
| OC-0051 | state-context | `packages/tui/src/context/directory.ts` | DISCOVERED |
| OC-0052 | state-context | `packages/tui/src/context/editor.ts` | DISCOVERED |
| OC-0053 | state-context | `packages/tui/src/context/epilogue.tsx` | DISCOVERED |
| OC-0054 | state-context | `packages/tui/src/context/event.ts` | DISCOVERED |
| OC-0055 | state-context | `packages/tui/src/context/exit.tsx` | DISCOVERED |
| OC-0056 | state-context | `packages/tui/src/context/helper.tsx` | DISCOVERED |
| OC-0057 | state-context | `packages/tui/src/context/kv.tsx` | DISCOVERED |
| OC-0058 | state-context | `packages/tui/src/context/local.tsx` | DISCOVERED |
| OC-0059 | state-context | `packages/tui/src/context/location.tsx` | DISCOVERED |
| OC-0060 | state-context | `packages/tui/src/context/path-format.tsx` | DISCOVERED |
| OC-0061 | state-context | `packages/tui/src/context/permission.tsx` | DISCOVERED |
| OC-0062 | state-context | `packages/tui/src/context/project.tsx` | DISCOVERED |
| OC-0063 | state-context | `packages/tui/src/context/prompt.tsx` | DISCOVERED |
| OC-0064 | state-context | `packages/tui/src/context/route.tsx` | DISCOVERED |
| OC-0065 | state-context | `packages/tui/src/context/runtime.tsx` | DISCOVERED |
| OC-0066 | state-context | `packages/tui/src/context/sdk.tsx` | DISCOVERED |
| OC-0067 | state-context | `packages/tui/src/context/sync.tsx` | DISCOVERED |
| OC-0068 | state-context | `packages/tui/src/context/theme.tsx` | DISCOVERED |
| OC-0069 | state-context | `packages/tui/src/context/thinking.ts` | DISCOVERED |
| OC-0070 | other | `packages/tui/src/editor-zed.ts` | DISCOVERED |
| OC-0071 | other | `packages/tui/src/editor.ts` | DISCOVERED |
| OC-0072 | feature-plugin | `packages/tui/src/feature-plugins/builtins.ts` | DISCOVERED |
| OC-0073 | feature-plugin | `packages/tui/src/feature-plugins/home/footer.tsx` | DISCOVERED |
| OC-0074 | feature-plugin | `packages/tui/src/feature-plugins/home/tips-view.tsx` | DISCOVERED |
| OC-0075 | feature-plugin | `packages/tui/src/feature-plugins/home/tips.tsx` | DISCOVERED |
| OC-0076 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/context.tsx` | DISCOVERED |
| OC-0077 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/files.tsx` | DISCOVERED |
| OC-0078 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/footer.tsx` | DISCOVERED |
| OC-0079 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/lsp.tsx` | DISCOVERED |
| OC-0080 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/mcp.tsx` | DISCOVERED |
| OC-0081 | feature-plugin | `packages/tui/src/feature-plugins/sidebar/todo.tsx` | DISCOVERED |
| OC-0082 | feature-plugin | `packages/tui/src/feature-plugins/system/diff-viewer-file-tree-utils.ts` | DISCOVERED |
| OC-0083 | feature-plugin | `packages/tui/src/feature-plugins/system/diff-viewer-file-tree.tsx` | DISCOVERED |
| OC-0084 | feature-plugin | `packages/tui/src/feature-plugins/system/diff-viewer-ui.tsx` | DISCOVERED |
| OC-0085 | feature-plugin | `packages/tui/src/feature-plugins/system/diff-viewer.tsx` | DISCOVERED |
| OC-0086 | feature-plugin | `packages/tui/src/feature-plugins/system/notifications.ts` | DISCOVERED |
| OC-0087 | feature-plugin | `packages/tui/src/feature-plugins/system/plugins.tsx` | DISCOVERED |
| OC-0088 | feature-plugin | `packages/tui/src/feature-plugins/system/which-key.tsx` | DISCOVERED |
| OC-0089 | other | `packages/tui/src/index.tsx` | DISCOVERED |
| OC-0090 | other | `packages/tui/src/keymap.tsx` | DISCOVERED |
| OC-0091 | other | `packages/tui/src/logo.ts` | DISCOVERED |
| OC-0092 | other | `packages/tui/src/parsers-config.ts` | DISCOVERED |
| OC-0093 | prompt | `packages/tui/src/prompt/display.ts` | DISCOVERED |
| OC-0094 | prompt | `packages/tui/src/prompt/frecency.tsx` | DISCOVERED |
| OC-0095 | prompt | `packages/tui/src/prompt/history.tsx` | DISCOVERED |
| OC-0096 | prompt | `packages/tui/src/prompt/part.ts` | DISCOVERED |
| OC-0097 | prompt | `packages/tui/src/prompt/stash.tsx` | DISCOVERED |
| OC-0098 | prompt | `packages/tui/src/prompt/traits.ts` | DISCOVERED |
| OC-0099 | route | `packages/tui/src/routes/home.tsx` | DISCOVERED |
| OC-0100 | route | `packages/tui/src/routes/home/session-destination.tsx` | DISCOVERED |
| OC-0101 | route | `packages/tui/src/routes/session/dialog-fork-from-timeline.tsx` | DISCOVERED |
| OC-0102 | route | `packages/tui/src/routes/session/dialog-message.tsx` | DISCOVERED |
| OC-0103 | route | `packages/tui/src/routes/session/dialog-subagent.tsx` | DISCOVERED |
| OC-0104 | route | `packages/tui/src/routes/session/dialog-timeline.tsx` | DISCOVERED |
| OC-0105 | route | `packages/tui/src/routes/session/footer.tsx` | DISCOVERED |
| OC-0106 | route | `packages/tui/src/routes/session/index.tsx` | DISCOVERED |
| OC-0107 | route | `packages/tui/src/routes/session/permission.tsx` | DISCOVERED |
| OC-0108 | route | `packages/tui/src/routes/session/question.tsx` | DISCOVERED |
| OC-0109 | route | `packages/tui/src/routes/session/sidebar.tsx` | DISCOVERED |
| OC-0110 | route | `packages/tui/src/routes/session/subagent-footer.tsx` | DISCOVERED |
| OC-0111 | other | `packages/tui/src/runtime.tsx` | DISCOVERED |
| OC-0112 | other | `packages/tui/src/terminal-win32.ts` | DISCOVERED |
| OC-0113 | theme | `packages/tui/src/theme/index.ts` | DISCOVERED |
| OC-0114 | ui-primitive | `packages/tui/src/ui/border.ts` | DISCOVERED |
| OC-0115 | dialog | `packages/tui/src/ui/dialog-alert.tsx` | DISCOVERED |
| OC-0116 | dialog | `packages/tui/src/ui/dialog-confirm.tsx` | DISCOVERED |
| OC-0117 | dialog | `packages/tui/src/ui/dialog-export-options.tsx` | DISCOVERED |
| OC-0118 | dialog | `packages/tui/src/ui/dialog-help.tsx` | DISCOVERED |
| OC-0119 | dialog | `packages/tui/src/ui/dialog-prompt.tsx` | DISCOVERED |
| OC-0120 | dialog | `packages/tui/src/ui/dialog-select.tsx` | DISCOVERED |
| OC-0121 | ui-primitive | `packages/tui/src/ui/dialog.tsx` | DISCOVERED |
| OC-0122 | ui-primitive | `packages/tui/src/ui/link.tsx` | DISCOVERED |
| OC-0123 | ui-primitive | `packages/tui/src/ui/spinner.ts` | DISCOVERED |
| OC-0124 | ui-primitive | `packages/tui/src/ui/toast.tsx` | DISCOVERED |
| OC-0125 | theme-token-set | `packages/ui/src/theme/themes/amoled.json` | DISCOVERED |
| OC-0126 | theme-token-set | `packages/ui/src/theme/themes/aura.json` | DISCOVERED |
| OC-0127 | theme-token-set | `packages/ui/src/theme/themes/ayu.json` | DISCOVERED |
| OC-0128 | theme-token-set | `packages/ui/src/theme/themes/carbonfox.json` | DISCOVERED |
| OC-0129 | theme-token-set | `packages/ui/src/theme/themes/catppuccin-frappe.json` | DISCOVERED |
| OC-0130 | theme-token-set | `packages/ui/src/theme/themes/catppuccin-macchiato.json` | DISCOVERED |
| OC-0131 | theme-token-set | `packages/ui/src/theme/themes/catppuccin.json` | DISCOVERED |
| OC-0132 | theme-token-set | `packages/ui/src/theme/themes/cobalt2.json` | DISCOVERED |
| OC-0133 | theme-token-set | `packages/ui/src/theme/themes/cursor.json` | DISCOVERED |
| OC-0134 | theme-token-set | `packages/ui/src/theme/themes/dracula.json` | DISCOVERED |
| OC-0135 | theme-token-set | `packages/ui/src/theme/themes/everforest.json` | DISCOVERED |
| OC-0136 | theme-token-set | `packages/ui/src/theme/themes/flexoki.json` | DISCOVERED |
| OC-0137 | theme-token-set | `packages/ui/src/theme/themes/github.json` | DISCOVERED |
| OC-0138 | theme-token-set | `packages/ui/src/theme/themes/gruvbox.json` | DISCOVERED |
| OC-0139 | theme-token-set | `packages/ui/src/theme/themes/kanagawa.json` | DISCOVERED |
| OC-0140 | theme-token-set | `packages/ui/src/theme/themes/lucent-orng.json` | DISCOVERED |
| OC-0141 | theme-token-set | `packages/ui/src/theme/themes/material.json` | DISCOVERED |
| OC-0142 | theme-token-set | `packages/ui/src/theme/themes/matrix.json` | DISCOVERED |
| OC-0143 | theme-token-set | `packages/ui/src/theme/themes/mercury.json` | DISCOVERED |
| OC-0144 | theme-token-set | `packages/ui/src/theme/themes/monokai.json` | DISCOVERED |
| OC-0145 | theme-token-set | `packages/ui/src/theme/themes/nightowl.json` | DISCOVERED |
| OC-0146 | theme-token-set | `packages/ui/src/theme/themes/nord.json` | DISCOVERED |
| OC-0147 | theme-token-set | `packages/ui/src/theme/themes/oc-2.json` | DISCOVERED |
| OC-0148 | theme-token-set | `packages/ui/src/theme/themes/one-dark.json` | DISCOVERED |
| OC-0149 | theme-token-set | `packages/ui/src/theme/themes/onedarkpro.json` | DISCOVERED |
| OC-0150 | theme-token-set | `packages/ui/src/theme/themes/opencode.json` | DISCOVERED |
| OC-0151 | theme-token-set | `packages/ui/src/theme/themes/orng.json` | DISCOVERED |
| OC-0152 | theme-token-set | `packages/ui/src/theme/themes/osaka-jade.json` | DISCOVERED |
| OC-0153 | theme-token-set | `packages/ui/src/theme/themes/palenight.json` | DISCOVERED |
| OC-0154 | theme-token-set | `packages/ui/src/theme/themes/rosepine.json` | DISCOVERED |
| OC-0155 | theme-token-set | `packages/ui/src/theme/themes/shadesofpurple.json` | DISCOVERED |
| OC-0156 | theme-token-set | `packages/ui/src/theme/themes/solarized.json` | DISCOVERED |
| OC-0157 | theme-token-set | `packages/ui/src/theme/themes/synthwave84.json` | DISCOVERED |
| OC-0158 | theme-token-set | `packages/ui/src/theme/themes/tokyonight.json` | DISCOVERED |
| OC-0159 | theme-token-set | `packages/ui/src/theme/themes/vercel.json` | DISCOVERED |
| OC-0160 | theme-token-set | `packages/ui/src/theme/themes/vesper.json` | DISCOVERED |
| OC-0161 | theme-token-set | `packages/ui/src/theme/themes/zenburn.json` | DISCOVERED |

## Blocked rows — no ATROPOS target surface exists

| Ref | Client | Reference path | Evidence | Note |
|---|---|---|---|---|
| OC-0162 | app | `packages/app` | 532 source files at pinned commit | desktop/web app: ATROPOS has no corresponding client |
| OC-0163 | desktop | `packages/desktop` | 85 source files at pinned commit | desktop shell: ATROPOS has no corresponding client |
| OC-0164 | console | `packages/console` | 235 source files at pinned commit | console web: ATROPOS has no corresponding client |
| OC-0165 | web | `packages/web` | 19 source files at pinned commit | docs/marketing web: ATROPOS has no corresponding client |
| OC-0166 | session-ui | `packages/session-ui` | 94 source files at pinned commit | session web ui: ATROPOS has no corresponding client |
| OC-0167 | server | `packages/server` | 29 source files at pinned commit | http server: ATROPOS has no corresponding client |

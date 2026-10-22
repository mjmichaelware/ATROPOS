/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ADD-W-007: Canonical Status-Vocabulary Retheme Engine & Accessible Roles
 * Section 1.3 mandate: Visual themes derived systematically from the canonical
 * status vocabulary enum with non-color glyph enforcement.
 */

export type CanonicalStatus =
  | 'idle'
  | 'planning'
  | 'waiting'
  | 'working'
  | 'review-required'
  | 'blocked'
  | 'completed'
  | 'failed'
  | 'cancelled';

export interface StatusThemeRole {
  status: CanonicalStatus;
  label: string;
  glyph: string;
  textColorClass: string;
  bgColorClass: string;
  borderColorClass: string;
  ariaRoleDescription: string;
}

export function resolveStatusThemeRole(status: CanonicalStatus): StatusThemeRole {
  switch (status) {
    case 'idle':
      return {
        status,
        label: 'Idle',
        glyph: '○',
        textColorClass: 'text-slate-400',
        bgColorClass: 'bg-slate-900',
        borderColorClass: 'border-slate-700',
        ariaRoleDescription: 'Status: Idle and awaiting operator instructions',
      };
    case 'planning':
      return {
        status,
        label: 'Planning',
        glyph: '◐',
        textColorClass: 'text-amber-400',
        bgColorClass: 'bg-amber-950/40',
        borderColorClass: 'border-amber-600',
        ariaRoleDescription: 'Status: Planning problem resolution trajectory',
      };
    case 'waiting':
      return {
        status,
        label: 'Waiting',
        glyph: '⏳',
        textColorClass: 'text-blue-400',
        bgColorClass: 'bg-blue-950/40',
        borderColorClass: 'border-blue-600',
        ariaRoleDescription: 'Status: Waiting on upstream dependency or approval',
      };
    case 'working':
      return {
        status,
        label: 'Working',
        glyph: '⚡',
        textColorClass: 'text-sky-400',
        bgColorClass: 'bg-sky-950/40',
        borderColorClass: 'border-sky-600',
        ariaRoleDescription: 'Status: Actively executing tool or transformation',
      };
    case 'review-required':
      return {
        status,
        label: 'Review Required',
        glyph: '◆',
        textColorClass: 'text-purple-400',
        bgColorClass: 'bg-purple-950/40',
        borderColorClass: 'border-purple-600',
        ariaRoleDescription: 'Status: Review required by operator authority',
      };
    case 'blocked':
      return {
        status,
        label: 'Blocked',
        glyph: '✖',
        textColorClass: 'text-rose-400',
        bgColorClass: 'bg-rose-950/40',
        borderColorClass: 'border-rose-600',
        ariaRoleDescription: 'Status: Blocked by verification failure or quota',
      };
    case 'completed':
      return {
        status,
        label: 'Completed',
        glyph: '✓',
        textColorClass: 'text-emerald-400',
        bgColorClass: 'bg-emerald-950/40',
        borderColorClass: 'border-emerald-600',
        ariaRoleDescription: 'Status: Task successfully completed and certified',
      };
    case 'failed':
      return {
        status,
        label: 'Failed',
        glyph: '✕',
        textColorClass: 'text-red-400',
        bgColorClass: 'bg-red-950/40',
        borderColorClass: 'border-red-600',
        ariaRoleDescription: 'Status: Execution failed with errors',
      };
    case 'cancelled':
      return {
        status,
        label: 'Cancelled',
        glyph: '⊘',
        textColorClass: 'text-zinc-400',
        bgColorClass: 'bg-zinc-900',
        borderColorClass: 'border-zinc-700',
        ariaRoleDescription: 'Status: Execution cancelled by operator',
      };
  }
}

export function applyThemeRoleToElement(element: HTMLElement, status: CanonicalStatus): void {
  const role = resolveStatusThemeRole(status);
  element.setAttribute('aria-roledescription', role.ariaRoleDescription);
  element.setAttribute('data-status', status);
}

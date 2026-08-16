'use client';

import {
  ThumbsUp,
  ThumbsDown,
  RotateCw,
  Pause,
  Play,
  X,
  ChevronRight,
  Shuffle,
  Split,
  Merge,
  Archive,
  Download,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/button';

export type ControlVerb =
  | 'approve'
  | 'reject'
  | 'retry'
  | 'pause'
  | 'resume'
  | 'cancel'
  | 'redirect'
  | 'prioritize'
  | 'split'
  | 'merge'
  | 'archive'
  | 'export'
  | 'inspect';

/** The one contract-layer verb vocabulary consumed by every surface. */
export const CANONICAL_CONTROL_VERBS = [
  'approve',
  'reject',
  'retry',
  'pause',
  'resume',
  'cancel',
  'redirect',
  'prioritize',
  'split',
  'merge',
  'archive',
  'export',
  'inspect',
] as const satisfies readonly ControlVerb[];

export function validateControlVerbSet(verbs: readonly string[]): boolean {
  return verbs.length <= CANONICAL_CONTROL_VERBS.length &&
    verbs.every((verb) => (CANONICAL_CONTROL_VERBS as readonly string[]).includes(verb));
}

interface ControlVerbsProps {
  available: ControlVerb[];
  onAction?: (verb: ControlVerb) => void;
  layout?: 'row' | 'column' | 'dropdown';
  size?: 'sm' | 'md' | 'lg';
  hideLabels?: boolean;
}

const VERB_CONFIG: Record<ControlVerb, { label: string; icon: any; color: string }> = {
  approve: { label: 'Approve', icon: ThumbsUp, color: 'text-green-600' },
  reject: { label: 'Reject', icon: ThumbsDown, color: 'text-red-600' },
  retry: { label: 'Retry', icon: RotateCw, color: 'text-blue-600' },
  pause: { label: 'Pause', icon: Pause, color: 'text-amber-600' },
  resume: { label: 'Resume', icon: Play, color: 'text-green-600' },
  cancel: { label: 'Cancel', icon: X, color: 'text-red-600' },
  redirect: { label: 'Redirect', icon: ChevronRight, color: 'text-purple-600' },
  prioritize: { label: 'Prioritize', icon: Zap, color: 'text-yellow-600' },
  split: { label: 'Split', icon: Split, color: 'text-blue-600' },
  merge: { label: 'Merge', icon: Merge, color: 'text-blue-600' },
  archive: { label: 'Archive', icon: Archive, color: 'text-neutral-600' },
  export: { label: 'Export', icon: Download, color: 'text-blue-600' },
  inspect: { label: 'Inspect', icon: Zap, color: 'text-blue-600' },
};

export function ControlVerbs({
  available,
  onAction,
  layout = 'row',
  size = 'md',
  hideLabels = false,
}: ControlVerbsProps) {
  const sizeClasses = {
    sm: 'text-xs gap-1 px-2 py-1',
    md: 'text-sm gap-2 px-3 py-2',
    lg: 'text-base gap-3 px-4 py-3',
  };

  const containerClass =
    layout === 'row'
      ? 'flex flex-wrap gap-2'
      : layout === 'column'
        ? 'flex flex-col gap-2'
        : 'relative'; // dropdown handled separately

  return (
    <div className={containerClass}>
      {available.map((verb) => {
        const config = VERB_CONFIG[verb];
        const Icon = config.icon;

        return (
          <button
            key={verb}
            onClick={() => onAction?.(verb)}
            className={`inline-flex items-center justify-center rounded-md border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800 transition-colors ${sizeClasses[size]} ${config.color}`}
            title={config.label}
            aria-label={config.label}
          >
            <Icon className="w-4 h-4" aria-hidden="true" />
            {!hideLabels && <span>{config.label}</span>}
          </button>
        );
      })}
    </div>
  );
}

interface ControlVerbsMenuProps {
  available: ControlVerb[];
  onAction?: (verb: ControlVerb) => void;
}

export function ControlVerbsMenu({ available, onAction }: ControlVerbsMenuProps) {
  // TODO: Implement dropdown menu for control verbs
  return <ControlVerbs available={available} onAction={onAction} layout="row" />;
}

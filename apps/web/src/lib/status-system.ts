/**
 * Canonical Status System
 * Section 3.3: Color is never the sole status channel.
 * Every status has icon, label, and semantic meaning.
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

export interface StatusDefinition {
  status: CanonicalStatus;
  label: string;
  icon: string; // icon name from lucide-react
  color: string; // CSS class or var
  bgColor: string; // background color for badge
  description: string;
  userProgress: string; // what this means to the user
  canRetry: boolean;
  canCancel: boolean;
  canApprove: boolean;
  canPause: boolean;
}

export const STATUS_DEFINITIONS: Record<CanonicalStatus, StatusDefinition> = {
  idle: {
    status: 'idle',
    label: 'Idle',
    icon: 'Circle',
    color: 'text-sg-neutral-500',
    bgColor: 'bg-sg-neutral-100 dark:bg-sg-neutral-800',
    description: 'Not currently active',
    userProgress: 'Ready to start, nothing happening',
    canRetry: false,
    canCancel: false,
    canApprove: false,
    canPause: false,
  },
  planning: {
    status: 'planning',
    label: 'Planning',
    icon: 'Lightbulb',
    color: 'text-sg-amber-600',
    bgColor: 'bg-sg-amber-100 dark:bg-sg-amber-900',
    description: 'Determining approach',
    userProgress: 'ATROPOS is planning the approach before starting work',
    canRetry: false,
    canCancel: true,
    canApprove: false,
    canPause: true,
  },
  waiting: {
    status: 'waiting',
    label: 'Waiting',
    icon: 'Clock',
    color: 'text-sg-blue-600',
    bgColor: 'bg-sg-blue-100 dark:bg-sg-blue-900',
    description: 'Blocked on external dependency or approval',
    userProgress: 'Waiting for your approval or external dependency to complete',
    canRetry: false,
    canCancel: true,
    canApprove: true,
    canPause: false,
  },
  working: {
    status: 'working',
    label: 'Working',
    icon: 'Zap',
    color: 'text-sg-red-600',
    bgColor: 'bg-sg-red-100 dark:bg-sg-red-900',
    description: 'Actively executing',
    userProgress: 'ATROPOS is actively working on this task',
    canRetry: false,
    canCancel: true,
    canApprove: false,
    canPause: true,
  },
  'review-required': {
    status: 'review-required',
    label: 'Review Required',
    icon: 'Eye',
    color: 'text-sg-purple-600',
    bgColor: 'bg-sg-purple-100 dark:bg-sg-purple-900',
    description: 'Awaiting human review before proceeding',
    userProgress: 'Your review and approval are needed to proceed',
    canRetry: false,
    canCancel: true,
    canApprove: true,
    canPause: false,
  },
  blocked: {
    status: 'blocked',
    label: 'Blocked',
    icon: 'AlertCircle',
    color: 'text-sg-red-600',
    bgColor: 'bg-sg-red-100 dark:bg-sg-red-900',
    description: 'Cannot proceed; requires intervention',
    userProgress: 'This task is blocked and needs your attention to resolve',
    canRetry: true,
    canCancel: true,
    canApprove: false,
    canPause: false,
  },
  completed: {
    status: 'completed',
    label: 'Completed',
    icon: 'CheckCircle2',
    color: 'text-sg-green-600',
    bgColor: 'bg-sg-green-100 dark:bg-sg-green-900',
    description: 'Successfully finished',
    userProgress: 'Task completed with evidence recorded',
    canRetry: false,
    canCancel: false,
    canApprove: false,
    canPause: false,
  },
  failed: {
    status: 'failed',
    label: 'Failed',
    icon: 'XCircle',
    color: 'text-sg-red-600',
    bgColor: 'bg-sg-red-100 dark:bg-sg-red-900',
    description: 'Did not complete successfully',
    userProgress: 'Task failed; review reason and decide to retry or cancel',
    canRetry: true,
    canCancel: true,
    canApprove: false,
    canPause: false,
  },
  cancelled: {
    status: 'cancelled',
    label: 'Cancelled',
    icon: 'XOctagon',
    color: 'text-sg-neutral-500',
    bgColor: 'bg-sg-neutral-100 dark:bg-sg-neutral-800',
    description: 'Manually stopped',
    userProgress: 'You or another agent cancelled this task',
    canRetry: true,
    canCancel: false,
    canApprove: false,
    canPause: false,
  },
};

export function getStatusDef(status: CanonicalStatus): StatusDefinition {
  return STATUS_DEFINITIONS[status];
}

export function getStatusIcon(status: CanonicalStatus): string {
  return getStatusDef(status).icon;
}

export function getStatusLabel(status: CanonicalStatus): string {
  return getStatusDef(status).label;
}

export function getStatusUserMessage(status: CanonicalStatus): string {
  return getStatusDef(status).userProgress;
}

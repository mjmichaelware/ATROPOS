'use client';

import { getStatusDef, CanonicalStatus } from '@/lib/status-system';
import {
  Circle,
  Lightbulb,
  Clock,
  Zap,
  Eye,
  AlertCircle,
  CheckCircle2,
  XCircle,
  XOctagon,
} from 'lucide-react';

const IconMap = {
  Circle,
  Lightbulb,
  Clock,
  Zap,
  Eye,
  AlertCircle,
  CheckCircle2,
  XCircle,
  XOctagon,
};

export type StatusTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

/**
 * Canonical form: the §3.3 status vocabulary, icon + label, colour-independent.
 */
interface CanonicalStatusBadgeProps {
  status: CanonicalStatus;
  size?: 'sm' | 'md' | 'lg';
  showIcon?: boolean;
  className?: string;
}

/**
 * Tone form, kept for SpecGraph surfaces whose subjects (a binding being
 * enabled, an export succeeding) are not project progress and so have no
 * canonical status to map onto.
 *
 * This contract was previously deleted rather than extended, which broke
 * every SpecGraph consumer. Both forms are supported so neither surface has
 * to lie about what its badge means.
 */
interface ToneStatusBadgeProps {
  tone?: StatusTone;
  label: string;
  className?: string;
}

export type StatusBadgeProps = CanonicalStatusBadgeProps | ToneStatusBadgeProps;

function isCanonical(props: StatusBadgeProps): props is CanonicalStatusBadgeProps {
  return 'status' in props;
}

export function StatusBadge(props: StatusBadgeProps) {
  if (!isCanonical(props)) {
    const { tone = 'neutral', label, className = '' } = props;
    const isLive = label.toUpperCase() === 'RUNNING';
    return (
      <span className={`sg-status sg-status-${tone} ${className}`} role="status">
        <span
          aria-hidden="true"
          className={`sg-status-mark ${isLive ? 'sg-status-mark-live' : ''}`}
        />
        <span>{label}</span>
      </span>
    );
  }

  const { status, size = 'md', showIcon = true, className = '' } = props;
  const def = getStatusDef(status);
  const IconComponent = IconMap[def.icon as keyof typeof IconMap];

  const sizeClasses = {
    sm: 'px-2 py-1 text-xs gap-1',
    md: 'px-3 py-2 text-sm gap-2',
    lg: 'px-4 py-3 text-base gap-2',
  };

  const sizeIconClasses = {
    sm: 'w-3 h-3',
    md: 'w-4 h-4',
    lg: 'w-5 h-5',
  };

  return (
    <div
      className={`inline-flex items-center rounded-full font-medium transition-all ${def.bgColor} ${def.color} ${sizeClasses[size]} ${className}`}
      data-status={status}
      role="status"
      aria-label={def.label}
      title={def.userProgress}
    >
      {showIcon && IconComponent && (
        <IconComponent className={sizeIconClasses[size]} aria-hidden="true" />
      )}
      <span>{def.label}</span>
    </div>
  );
}

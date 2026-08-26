import { Circle, AlertCircle, CheckCircle2, XCircle, HelpCircle, Lightbulb } from 'lucide-react';
import { normalizePlanStatus } from '@/lib/planning/status';

type PlanStatus = 'DRAFT' | 'BLOCKED' | 'INVALID' | 'VERIFIED' | 'UNKNOWN';

const PlanStatusIconMap = {
  DRAFT: Lightbulb,
  BLOCKED: AlertCircle,
  INVALID: XCircle,
  VERIFIED: CheckCircle2,
  UNKNOWN: HelpCircle,
} as const;

const PlanStatusColorMap: Record<PlanStatus, { color: string; bgColor: string }> = {
  DRAFT: { color: 'text-sg-amber-600', bgColor: 'bg-sg-amber-100 dark:bg-sg-amber-900' },
  BLOCKED: { color: 'text-sg-red-600', bgColor: 'bg-sg-red-100 dark:bg-sg-red-900' },
  INVALID: { color: 'text-sg-red-600', bgColor: 'bg-sg-red-100 dark:bg-sg-red-900' },
  VERIFIED: { color: 'text-sg-green-600', bgColor: 'bg-sg-green-100 dark:bg-sg-green-900' },
  UNKNOWN: { color: 'text-sg-neutral-500', bgColor: 'bg-sg-neutral-100 dark:bg-sg-neutral-800' },
};

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

export function PlanStatusBadge({
  status,
  size = 'md',
  showIcon = true,
  className = '',
}: {
  status: unknown;
  size?: 'sm' | 'md' | 'lg';
  showIcon?: boolean;
  className?: string;
}) {
  const normalized = normalizePlanStatus(status) as 'DRAFT' | 'BLOCKED' | 'INVALID' | 'VERIFIED' | 'UNKNOWN';
  const IconComponent = PlanStatusIconMap[normalized];
  const { color, bgColor } = PlanStatusColorMap[normalized];

  return (
    <div
      className={`inline-flex items-center rounded-full font-medium transition-all ${bgColor} ${color} ${sizeClasses[size]} ${className}`}
      data-status={normalized}
      role="status"
      aria-label={normalized}
      title={normalized}
    >
      {showIcon && IconComponent && (
        <IconComponent className={sizeIconClasses[size]} aria-hidden="true" />
      )}
      <span>{normalized}</span>
    </div>
  );
}

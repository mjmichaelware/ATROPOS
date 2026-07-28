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

interface StatusBadgeProps {
  status: CanonicalStatus;
  size?: 'sm' | 'md' | 'lg';
  showIcon?: boolean;
  className?: string;
}

export function StatusBadge({
  status,
  size = 'md',
  showIcon = true,
  className = '',
}: StatusBadgeProps) {
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

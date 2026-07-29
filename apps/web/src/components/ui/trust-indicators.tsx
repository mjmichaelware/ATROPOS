'use client';

import { Shield, CheckCircle2, AlertCircle, Info, HelpCircle } from 'lucide-react';

export interface TrustIndicators {
  /** Authority has been verified */
  authorityVerified?: boolean;
  /** Evidence has been verified */
  evidenceVerified?: boolean;
  /** Verification process completed */
  verificationComplete?: boolean;
  /** Compliant with all policies */
  policyCompliant?: boolean;
  /** Current checkpoint exists */
  checkpointCurrent?: boolean;
  /** Recovery data available */
  recoveryAvailable?: boolean;
  /** No silent failures detected */
  noSilentFailures?: boolean;
}

interface TrustIndicatorsProps {
  indicators: TrustIndicators;
  layout?: 'row' | 'column';
  size?: 'sm' | 'md';
  compact?: boolean;
}

export function TrustIndicators({
  indicators,
  layout = 'row',
  size = 'md',
  compact = false,
}: TrustIndicatorsProps) {
  const items = [
    {
      key: 'authorityVerified',
      label: 'Authority verified',
      icon: Shield,
    },
    {
      key: 'evidenceVerified',
      label: 'Evidence verified',
      icon: CheckCircle2,
    },
    {
      key: 'verificationComplete',
      label: 'Verification complete',
      icon: CheckCircle2,
    },
    {
      key: 'policyCompliant',
      label: 'Policy compliant',
      icon: Shield,
    },
    {
      key: 'checkpointCurrent',
      label: 'Checkpoint current',
      icon: Info,
    },
    {
      key: 'recoveryAvailable',
      label: 'Recovery available',
      icon: Info,
    },
    {
      key: 'noSilentFailures',
      label: 'No silent failures',
      icon: CheckCircle2,
    },
  ];

  // §4.2 lists three states, not two. An indicator nobody probed is unknown,
  // and unknown is displayed rather than dropped: a hidden indicator reads as
  // an absent requirement, which is how a fabricated `true` used to hide here.
  const activeItems = items.filter(
    (item) => indicators[item.key as keyof TrustIndicators] === true
  );

  const failedItems = items.filter(
    (item) => indicators[item.key as keyof TrustIndicators] === false
  );

  const unknownItems = items.filter(
    (item) => indicators[item.key as keyof TrustIndicators] === undefined
  );

  if (compact && activeItems.length === items.length) {
    return (
      <div className="flex items-center gap-1 text-green-600 text-xs font-medium">
        <CheckCircle2 className="w-3 h-3" />
        All systems verified
      </div>
    );
  }

  const containerClass =
    layout === 'row'
      ? 'flex flex-wrap gap-2'
      : 'flex flex-col gap-2';

  const sizeClass = size === 'sm' ? 'text-xs gap-1' : 'text-sm gap-2';

  return (
    <div className={containerClass}>
      {/* Active (green) indicators */}
      {activeItems.map((item) => {
        const Icon = item.icon;
        return (
          <div
            key={item.key}
            className={`inline-flex items-center ${sizeClass} px-2 py-1 rounded-full bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400`}
            title={item.label}
          >
            <Icon className="w-3 h-3" aria-hidden="true" />
            {!compact && <span>{item.label}</span>}
          </div>
        );
      })}

      {/* Failed (red) indicators */}
      {failedItems.map((item) => (
        <div
          key={item.key}
          className={`inline-flex items-center ${sizeClass} px-2 py-1 rounded-full bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400`}
          title={`${item.label} - FAILED`}
          role="alert"
        >
          <AlertCircle className="w-3 h-3" aria-hidden="true" />
          {!compact && <span>{item.label}</span>}
        </div>
      ))}

      {/* Unknown (neutral) indicators: not probed, so nothing is claimed. */}
      {unknownItems.map((item) => (
        <div
          key={item.key}
          className={`inline-flex items-center ${sizeClass} px-2 py-1 rounded-full bg-sg-neutral-100 dark:bg-sg-neutral-800 text-sg-neutral-600 dark:text-sg-neutral-400`}
          title={`${item.label} - not verified`}
        >
          <HelpCircle className="w-3 h-3" aria-hidden="true" />
          {!compact && <span>{item.label}: unknown</span>}
        </div>
      ))}
    </div>
  );
}

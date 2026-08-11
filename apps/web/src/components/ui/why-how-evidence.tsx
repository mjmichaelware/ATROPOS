'use client';

import { useState } from 'react';
import { HelpCircle, Route, Link2 } from 'lucide-react';
import type { Evidence, SixAnswers } from '@/lib/api-atropos/types';
import { EvidenceLinking } from '@/components/ui/evidence-linking';

/**
 * §5.3 explainability controls: Why? / How? / Evidence.
 *
 * The single rule this component exists to enforce is that a missing answer
 * stays missing. Each control opens to what the engine actually recorded, or
 * to a statement that the engine recorded nothing — never to a reconstructed
 * explanation. A plausible answer is indistinguishable from a true one at a
 * glance, which makes it worse than an admitted gap.
 */
export interface WhyHowEvidenceProps {
  /** Whatever the engine recorded for this subject. */
  answers?: SixAnswers;
  evidence?: Evidence[];
  /**
   * Pipeline, participating agents and verification steps.
   *
   * No current producer emits this: the ATROPOS API carries no pipeline field
   * on work items, runs or goals. It is accepted here so that the control is
   * already wired when the engine begins reporting it, and reads as "not
   * provided" until then.
   */
  how?: string;
  subject?: string;
  className?: string;
}

type Panel = 'why' | 'how' | 'evidence';

export function WhyHowEvidence({
  answers,
  evidence,
  how,
  subject = 'this item',
  className = '',
}: WhyHowEvidenceProps) {
  const [open, setOpen] = useState<Panel | null>(null);

  const toggle = (panel: Panel) => setOpen((current) => (current === panel ? null : panel));

  const why = answers?.reasoning?.trim();
  const pipeline = how?.trim();
  const hasEvidence = Boolean(evidence && evidence.length > 0);

  const controls: Array<{ id: Panel; label: string; icon: typeof HelpCircle }> = [
    { id: 'why', label: 'Why?', icon: HelpCircle },
    { id: 'how', label: 'How?', icon: Route },
    { id: 'evidence', label: 'Evidence', icon: Link2 },
  ];

  return (
    <div className={`space-y-2 ${className}`}>
      <div className="flex flex-wrap gap-2">
        {controls.map((control) => {
          const Icon = control.icon;
          const isOpen = open === control.id;
          return (
            <button
              key={control.id}
              type="button"
              onClick={() => toggle(control.id)}
              aria-expanded={isOpen}
              className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                isOpen
                  ? 'border-sg-red-400 bg-sg-red-50 text-sg-red-700 dark:border-sg-red-600 dark:bg-sg-red-900/20 dark:text-sg-red-300'
                  : 'border-sg-neutral-300 text-sg-neutral-600 hover:border-sg-red-400 dark:border-sg-neutral-700 dark:text-sg-neutral-400'
              }`}
            >
              <Icon className="h-3 w-3" aria-hidden="true" />
              {control.label}
            </button>
          );
        })}
      </div>

      {open === 'why' && (
        <Panel title={`Why is ATROPOS doing ${subject}?`}>
          {why ? (
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{why}</p>
          ) : (
            <NotProvided what="No rationale was recorded for this item." />
          )}
        </Panel>
      )}

      {open === 'how' && (
        <Panel title="How is it being done?">
          {pipeline ? (
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{pipeline}</p>
          ) : (
            <NotProvided what="The engine does not yet report a pipeline, participating agents or verification steps for this item." />
          )}
        </Panel>
      )}

      {open === 'evidence' && (
        <Panel title="Can I inspect the evidence?">
          {hasEvidence ? (
            <EvidenceLinking evidence={evidence!} />
          ) : (
            <NotProvided what="No evidence is linked to this item." />
          )}
        </Panel>
      )}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-sg-neutral-200 bg-sg-neutral-50 p-3 dark:border-sg-neutral-800 dark:bg-sg-neutral-900">
      <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-sg-neutral-600 dark:text-sg-neutral-400">
        {title}
      </p>
      {children}
    </div>
  );
}

/**
 * The honest empty state. Deliberately worded as an absence of a record rather
 * than an absence of a reason, so it never reads as "there was no reason".
 */
function NotProvided({ what }: { what: string }) {
  return (
    <p className="text-sm italic text-sg-neutral-500 dark:text-sg-neutral-500">
      {what} Nothing is inferred here.
    </p>
  );
}

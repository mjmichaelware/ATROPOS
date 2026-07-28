'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { TrustIndicators } from '@/components/ui/trust-indicators';
import { Network } from 'lucide-react';

export default function ModelsPage() {
  const modelsAnswers: SixAnswer = {
    objective: 'Present available providers and routing decisions.',
    currentOperation: 'No providers configured. System ready for setup.',
    reasoning: 'Models surface is presentation of routing reality, not a second policy engine. Shows every available provider and current selection reasoning.',
    progress: { percent: 0, stage: 'Setup' },
    nextAction: 'Configure available providers and set routing preferences.',
    evidence: { link: '/settings', label: 'Configure providers' },
  };

  const providerAnswers: SixAnswer = {
    objective: 'Display every available provider, routing decision, quota, and cost.',
    currentOperation: 'Idle - No providers available.',
    reasoning: 'Provider dashboard shows availability, current routing, fallback chain, latency, quota, cost, tokens, retries, failures, and health.',
    progress: { percent: 0, stage: 'Idle' },
    nextAction: 'Set up your first provider.',
  };

  return (
    <div className="space-y-8 p-8 max-w-4xl mx-auto">
      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          Models & Providers
        </h1>
        <SixAnswersPanel answers={modelsAnswers} compact={false} />
      </section>

      {/* Provider Dashboard */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Provider Status
        </h2>
        <SixAnswersPanel answers={providerAnswers} compact={false} expandable={true} />

        <div className="text-center py-12 border border-dashed border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-sg-neutral-50 dark:bg-sg-neutral-900">
          <Network className="w-16 h-16 text-sg-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
            No providers configured
          </h3>
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400 mb-4">
            Configure providers in settings to enable model routing and autonomous work.
          </p>
        </div>
      </section>
    </div>
  );
}

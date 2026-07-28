'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { AllInspectors } from '@/components/dev-tools/inspectors';
import { AlertCircle } from 'lucide-react';

export default function DevToolsPage() {
  const devToolsAnswers: SixAnswer = {
    objective: 'Provide deep visibility into ATROPOS runtime, compilation, and verification systems.',
    currentOperation: 'Awaiting project activation or manual inspection.',
    reasoning:
      'Developer Tools expose advanced systems without polluting primary navigation. For system inspection and debugging only.',
    progress: { percent: 100, stage: 'Ready' },
    nextAction: 'Select a project to inspect its runtime state, or review system-level metrics.',
    evidence: {
      link: '#',
      label: 'View developer documentation',
    },
  };

  return (
    <div className="space-y-8 p-8 max-w-6xl mx-auto">
      {/* Warning */}
      <div className="bg-sg-amber-50 dark:bg-sg-amber-900/20 border border-sg-amber-200 dark:border-sg-amber-800 rounded-lg p-4 flex gap-3">
        <AlertCircle className="w-5 h-5 text-sg-amber-600 flex-shrink-0 mt-0.5" />
        <div className="text-sm text-sg-amber-900 dark:text-sg-amber-100">
          <p className="font-semibold mb-1">Developer Tools</p>
          <p>
            These inspectors expose internal runtime state, compilation outputs, and advanced
            verification data. Use for debugging, auditing, and advanced configuration only.
          </p>
        </div>
      </div>

      {/* Page Context */}
      <section className="space-y-3">
        <h1 className="text-3xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
          Developer Tools
        </h1>
        <SixAnswersPanel answers={devToolsAnswers} compact={false} />
      </section>

      {/* Inspectors */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          System Inspectors
        </h2>
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
          Click any inspector to view detailed system information. Data updates in real-time when a
          project is active.
        </p>
        <AllInspectors />
      </section>

      {/* Documentation Links */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Resources
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <a
            href="#"
            className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
          >
            <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              Runtime API Documentation
            </h3>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              Learn about ATROPOS runtime internals, DAG execution, and checkpoint semantics
            </p>
          </a>
          <a
            href="#"
            className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
          >
            <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              SpecGraph Integration
            </h3>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              Understand how SpecGraph compiler and DLOI systems interact with ATROPOS
            </p>
          </a>
          <a
            href="#"
            className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
          >
            <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              Policy and Authority
            </h3>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              Deep dive into ATROPOS policy engine, safety boundaries, and source authority
            </p>
          </a>
          <a
            href="#"
            className="block p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:border-sg-red-400 dark:hover:border-sg-red-600 transition-colors"
          >
            <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-1">
              Recovery and Checkpoints
            </h3>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              Understand checkpoint format, recovery protocols, and state verification
            </p>
          </a>
        </div>
      </section>
    </div>
  );
}

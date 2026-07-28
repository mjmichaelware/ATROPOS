'use client';

import { Metadata } from 'next';
import { Plus, Zap, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { TrustIndicators } from '@/components/ui/trust-indicators';
import { StatusBadge } from '@/components/ui/status-badge';

// Metadata can't be used in client component, so we'll skip it for now
// export const metadata: Metadata = {
//   title: 'Home - ATROPOS',
//   description: 'ATROPOS operative cockpit',
// };

export default function Home() {
  // TODO: Replace with real data from API
  const systemStatus: SixAnswer = {
    objective: 'Provide a unified operating environment for autonomous work directed by humans.',
    currentOperation: 'Ready for projects. No active workflows.',
    reasoning: 'System initialized and waiting for project creation or task assignment.',
    progress: {
      percent: 0,
      stage: 'Idle',
    },
    nextAction: 'Create your first project or open an existing one to begin work.',
    evidence: {
      link: '/history',
      label: 'View system initialization log',
    },
  };

  const trustIndicators = {
    authorityVerified: true,
    evidenceVerified: true,
    verificationComplete: true,
    policyCompliant: true,
    checkpointCurrent: true,
    recoveryAvailable: false,
    noSilentFailures: true,
  };

  return (
    <div className="space-y-8 p-8">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-4xl font-bold text-sg-red-600">ATROPOS</h1>
        <p className="text-lg text-sg-neutral-600 dark:text-sg-neutral-400">
          Persistent autonomous software operating environment
        </p>
      </div>

      {/* System Status - Six Continuous Answers */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          System Status
        </h2>
        <SixAnswersPanel answers={systemStatus} expandable={false} />
      </section>

      {/* Trust Indicators */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          System Health
        </h2>
        <TrustIndicators indicators={trustIndicators} layout="row" compact={false} />
      </section>

      {/* Quick Actions */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Quick Actions
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <button className="flex items-center gap-3 p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors">
            <Plus className="w-5 h-5 text-sg-red-600" />
            <div className="text-left">
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Create Project
              </p>
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                Start new autonomous work
              </p>
            </div>
          </button>

          <button className="flex items-center gap-3 p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors">
            <Zap className="w-5 h-5 text-sg-amber-600" />
            <div className="text-left">
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Resume Work
              </p>
              <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                Continue from last checkpoint
              </p>
            </div>
          </button>
        </div>
      </section>

      {/* Recent Projects - placeholder */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Recent Projects
        </h2>
        <div className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 text-center">
          <CheckCircle2 className="w-12 h-12 text-sg-neutral-400 mx-auto mb-3" />
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
            No projects yet. Create one to get started.
          </p>
        </div>
      </section>

      {/* Running Jobs - placeholder */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Running Jobs
        </h2>
        <div className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 text-center">
          <Zap className="w-12 h-12 text-sg-neutral-400 mx-auto mb-3" />
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
            No active jobs
          </p>
        </div>
      </section>

      {/* Approvals & Blockers - placeholder */}
      <section className="space-y-3">
        <h2 className="text-xl font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Approvals & Blockers
        </h2>
        <div className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-6 text-center">
          <AlertTriangle className="w-12 h-12 text-sg-neutral-400 mx-auto mb-3" />
          <p className="text-sg-neutral-600 dark:text-sg-neutral-400">
            Nothing requires attention
          </p>
        </div>
      </section>
    </div>
  );
}

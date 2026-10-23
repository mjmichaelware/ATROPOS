/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { AutonomousWorkflowComposer } from '@/components/automation/autonomous-workflow-composer';
import { Zap } from 'lucide-react';

export default function AutomationPage() {
  const automationAnswers: SixAnswer = {
    objective: 'Orchestrate recurring autonomous tasks, background verification loops, and self-healing repair swarms.',
    currentOperation: 'Autonomous Cron pipelines active. Continuous Merkle sentinel and AST assertions engaged.',
    reasoning: 'Workflows operate on formal state machines with cryptographic checkpoint verification before advancing.',
    progress: { percent: 100, stage: 'Continuous Sentinel' },
    nextAction: 'Monitor execution heartbeats or trigger an ad-hoc verification pass.',
  };

  return (
    <div className="space-y-8 p-4 md:p-8 max-w-7xl mx-auto font-sans">
      {/* Page Context */}
      <section className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400">
            <Zap className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl md:text-3xl font-bold text-white tracking-tight">
              Autonomous Automation & Schedulers
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Deterministic cron execution, Merkle checkpoint automations, and self-healing AST repair pipelines.
            </p>
          </div>
        </div>

        <SixAnswersPanel answers={automationAnswers} compact={false} />
      </section>

      {/* Autonomous Composer */}
      <AutonomousWorkflowComposer />
    </div>
  );
}

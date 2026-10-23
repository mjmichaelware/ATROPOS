/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { ModelArenaComparator } from '@/components/models/model-arena-comparator';
import { Cpu, Server, Activity } from 'lucide-react';

export default function ModelsPage() {
  const modelsAnswers: SixAnswer = {
    objective: 'Present configured model providers, routing topologies, and real-time benchmark telemetry.',
    currentOperation: 'Dynamic multi-provider routing active across frontier, standard, and local air-gapped backends.',
    reasoning: 'Routing reality reflects strict latency budgets and mathematical token burn constraints. Zero opaque vendor lock-in.',
    progress: { percent: 100, stage: 'Active Routing' },
    nextAction: 'Run benchmark duels or register custom local inference endpoints.',
  };

  return (
    <div className="space-y-8 p-4 md:p-8 max-w-7xl mx-auto font-sans">
      {/* Page Header */}
      <section className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-cyan-600/10 border border-cyan-500/20 text-cyan-400">
            <Cpu className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl md:text-3xl font-bold text-white tracking-tight">
              Sovereign Models & Provider Arena
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Multi-backend latency benchmarking, side-by-side deterministic duel comparisons, and dynamic routing policies.
            </p>
          </div>
        </div>

        <SixAnswersPanel answers={modelsAnswers} compact={false} />
      </section>

      {/* Arena Component */}
      <ModelArenaComparator />
    </div>
  );
}

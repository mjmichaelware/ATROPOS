/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React from 'react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { SovereignHistoryTimeline } from '@/components/history/sovereign-history-timeline';

export default function HistoryPage() {
  const historyAnswers: SixAnswer = {
    objective: 'Display every sovereign event within projects as an immutable, cryptographically attested audit log.',
    currentOperation: 'Streaming real-time Merkle attestation journal and verification checkpoints.',
    reasoning: 'Every state transition is signed with SHA-256 evidence hashes and cannot be silently mutated.',
    progress: { percent: 100, stage: 'Continuous Verification' },
    nextAction: 'Filter timeline by verification gate, AST mutation, or Merkle seal.'
  };

  return (
    <div className="space-y-6 p-4 md:p-8 max-w-6xl mx-auto text-slate-100 font-sans">
      <section className="space-y-3">
        <h1 className="text-2xl md:text-3xl font-bold text-slate-100">
          Cryptographic History & Merkle Journal
        </h1>
        <SixAnswersPanel answers={historyAnswers} compact={false} expandable={true} />
      </section>

      <section>
        <SovereignHistoryTimeline />
      </section>
    </div>
  );
}

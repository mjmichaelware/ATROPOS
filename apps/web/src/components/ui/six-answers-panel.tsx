'use client';

import { ChevronRightIcon, ChevronDownIcon } from 'lucide-react';
import { useState } from 'react';
import type { SixAnswers } from '@/lib/api-atropos/types';
import { EvidenceLinking } from '@/components/ui/evidence-linking';

/**
 * The six continuous answers of Source Document 4 §0.1.
 *
 * This panel previously declared its own shape whose `evidence` was a single
 * `{link,label}`, while the API returned `Evidence[]`. The two drifted and
 * every project page failed to compile against one or the other. §10.3 makes
 * evidence a first-class browsable trail rather than one link, so the API
 * shape is the single source of truth and this panel renders it.
 */
export type SixAnswer = SixAnswers;

interface SixAnswersPanelProps {
  answers: SixAnswer;
  compact?: boolean;
  expandable?: boolean;
}

export function SixAnswersPanel({
  answers,
  compact = false,
  expandable = true,
}: SixAnswersPanelProps) {
  const [expanded, setExpanded] = useState(!compact);

  if (compact && !expanded) {
    return (
      <button
        onClick={() => setExpanded(true)}
        className="flex items-center gap-2 text-sm text-sg-neutral-600 hover:text-sg-red-600 transition-colors"
      >
        <ChevronRightIcon className="w-4 h-4" />
        Show context ({Object.values(answers).filter(Boolean).length} items)
      </button>
    );
  }

  return (
    <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4 space-y-3">
      {/* Header */}
      {expandable && compact && (
        <button
          onClick={() => setExpanded(false)}
          className="flex items-center gap-2 text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-2"
        >
          <ChevronDownIcon className="w-4 h-4" />
          Context
        </button>
      )}

      {/* 1. Objective */}
      {answers.objective && (
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            What am I trying to accomplish?
          </label>
          <p className="text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
            {answers.objective}
          </p>
        </div>
      )}

      {/* 2. Current Operation */}
      {answers.currentOperation && (
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            What is ATROPOS doing?
          </label>
          <p className="text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
            {answers.currentOperation}
          </p>
        </div>
      )}

      {/* 3. Reasoning */}
      {answers.reasoning && (
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            Why is it doing that?
          </label>
          <p className="text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
            {answers.reasoning}
          </p>
        </div>
      )}

      {/* 4. Progress */}
      {answers.progress && (
        <div className="space-y-2">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            How far along is it?
          </label>
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
                {answers.progress.stage || 'Execution'}
              </span>
              <span className="text-sm font-semibold text-sg-red-600">
                {answers.progress.percent}%
              </span>
            </div>
            <div className="w-full bg-sg-neutral-200 dark:bg-sg-neutral-800 rounded-full h-2 overflow-hidden">
              <div
                className="bg-gradient-to-r from-sg-red-500 to-sg-red-600 h-full transition-all duration-300"
                style={{ width: `${answers.progress.percent}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {/* 5. Next Action */}
      {answers.nextAction && (
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            What should I do next?
          </label>
          <p className="text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
            {answers.nextAction}
          </p>
        </div>
      )}

      {/* 6. Evidence */}
      <div className="space-y-1">
        <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
          Can I inspect the evidence?
        </label>
        {answers.evidence && answers.evidence.length > 0 ? (
          // §10.3: the full trail, not a single link.
          <EvidenceLinking
            evidence={answers.evidence.map((item) => ({
              id: item.id,
              type: item.type,
              title: item.title,
              timestamp: item.timestamp,
              link: item.link,
            }))}
          />
        ) : (
          // §0.9 / no-fake-data: absence of evidence is stated, never implied
          // by an empty region the operator has to interpret.
          <p className="text-sm text-sg-neutral-500 italic">
            No evidence recorded yet.
          </p>
        )}
      </div>
    </div>
  );
}

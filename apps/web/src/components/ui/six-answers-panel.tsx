'use client';

import { ChevronRightIcon, ChevronDownIcon } from 'lucide-react';
import { useState } from 'react';

export interface SixAnswer {
  /** What am I trying to accomplish? */
  objective?: string;
  /** What is ATROPOS doing? */
  currentOperation?: string;
  /** Why is it doing that? */
  reasoning?: string;
  /** How far along is it? */
  progress?: {
    percent: number;
    stage?: string;
  };
  /** What should I do next? */
  nextAction?: string;
  /** Can I inspect the evidence? */
  evidence?: {
    link?: string;
    label?: string;
  };
}

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
      {answers.evidence && (
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
            Can I inspect the evidence?
          </label>
          {answers.evidence.link ? (
            <a
              href={answers.evidence.link}
              className="text-sm text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline transition-colors"
            >
              {answers.evidence.label || 'View evidence'}
            </a>
          ) : (
            <p className="text-sm text-sg-neutral-500 italic">No evidence available</p>
          )}
        </div>
      )}
    </div>
  );
}

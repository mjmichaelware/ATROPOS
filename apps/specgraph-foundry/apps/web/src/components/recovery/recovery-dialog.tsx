'use client';

import { AlertCircle, CheckCircle2, Clock, RotateCcw } from 'lucide-react';
import { useEffect, useState } from 'react';

export interface RecoveredCheckpoint {
  id: string;
  timestamp: string;
  projectName: string;
  projectId: string;
  stage: string;
  progress: number;
  agents: string[];
  workflows: number;
  queued: number;
}

interface RecoveryDialogProps {
  checkpoint?: RecoveredCheckpoint;
  onRecover?: () => void;
  onIgnore?: () => void;
  open?: boolean;
}

export function RecoveryDialog({
  checkpoint,
  onRecover,
  onIgnore,
  open = true,
}: RecoveryDialogProps) {
  if (!checkpoint || !open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50">
      <div className="bg-white dark:bg-sg-neutral-900 rounded-lg shadow-2xl max-w-2xl w-full mx-4 p-8 space-y-6">
        {/* Header */}
        <div className="flex items-start gap-3">
          <RotateCcw className="w-6 h-6 text-sg-amber-600 flex-shrink-0 mt-1" />
          <div>
            <h2 className="text-2xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
              Recover Previous Work?
            </h2>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
              ATROPOS found an unfinished work session. Review what will be restored.
            </p>
          </div>
        </div>

        {/* Checkpoint Details */}
        <div className="bg-sg-neutral-50 dark:bg-sg-neutral-800 rounded-lg p-4 space-y-3">
          <div>
            <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              Project
            </h3>
            <p className="text-base text-sg-neutral-700 dark:text-sg-neutral-300">
              {checkpoint.projectName}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <h4 className="text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase">
                Stage
              </h4>
              <p className="text-base text-sg-neutral-900 dark:text-sg-neutral-50">
                {checkpoint.stage}
              </p>
            </div>
            <div>
              <h4 className="text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase">
                Progress
              </h4>
              <p className="text-base text-sg-neutral-900 dark:text-sg-neutral-50">
                {checkpoint.progress}%
              </p>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase mb-2">
              What Will Be Restored
            </h4>
            <ul className="space-y-1 text-sm">
              {checkpoint.agents.length > 0 && (
                <li className="text-sg-neutral-700 dark:text-sg-neutral-300">
                  ✓ {checkpoint.agents.length} agent assignment(s)
                </li>
              )}
              {checkpoint.workflows > 0 && (
                <li className="text-sg-neutral-700 dark:text-sg-neutral-300">
                  ✓ {checkpoint.workflows} workflow(s) in progress
                </li>
              )}
              {checkpoint.queued > 0 && (
                <li className="text-sg-neutral-700 dark:text-sg-neutral-300">
                  ✓ {checkpoint.queued} queued task(s)
                </li>
              )}
              <li className="text-sg-neutral-700 dark:text-sg-neutral-300">
                ✓ Workspace layout and state
              </li>
              <li className="text-sg-neutral-700 dark:text-sg-neutral-300">
                ✓ All project memory and history
              </li>
            </ul>
          </div>

          <div className="flex items-center gap-2 text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
            <Clock className="w-4 h-4" />
            Checkpoint saved: {checkpoint.timestamp}
          </div>
        </div>

        {/* Safety Notice */}
        <div className="bg-sg-blue-50 dark:bg-sg-blue-900/30 border border-sg-blue-200 dark:border-sg-blue-800 rounded-lg p-4 flex gap-3">
          <AlertCircle className="w-5 h-5 text-sg-blue-600 flex-shrink-0 mt-0.5" />
          <div className="text-sm text-sg-blue-900 dark:text-sg-blue-100">
            <p className="font-semibold mb-1">Recovery is safe</p>
            <p>
              All recovered work has been verified. No agent work will be duplicated. Approvals
              remain in their previous state.
            </p>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-3 justify-end">
          <button
            onClick={onIgnore}
            className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800 transition-colors font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
          >
            Start Fresh
          </button>
          <button
            onClick={onRecover}
            className="px-6 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold flex items-center gap-2"
          >
            <RotateCcw className="w-4 h-4" />
            Recover & Resume
          </button>
        </div>
      </div>
    </div>
  );
}

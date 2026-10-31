/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React from 'react';
import { AlertTriangle, ArrowUpRight, ArrowDownLeft, RefreshCw } from 'lucide-react';

/**
 * ATOM VC2: COMBINED PULL AND PUSH CONFIRMATION PROMPT
 * Path: apps/web/src/components/ide/GitSyncConfirmationDialog.tsx
 *
 * Prompts the operator for explicit confirmation before executing combined pull and push
 * operations. Warns developers if the local branch has diverged significantly from remote tracking branches.
 */
export interface GitSyncConfirmationDialogProps {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  incomingCount: number;
  outgoingCount: number;
  currentBranch: string;
  hasDivergence?: boolean;
}

export const GitSyncConfirmationDialog: React.FC<GitSyncConfirmationDialogProps> = ({
  isOpen,
  onConfirm,
  onCancel,
  incomingCount,
  outgoingCount,
  currentBranch,
  hasDivergence = false,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-lg shadow-2xl p-5 text-slate-100 animate-in fade-in zoom-in-95 duration-150">
        <div className="flex items-start gap-3">
          <div className="p-2 rounded-full bg-cyan-950/80 border border-cyan-800/50 text-cyan-400 shrink-0">
            <RefreshCw className="w-5 h-5 animate-spin-reverse" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-100">
              Synchronize Branch "{currentBranch}"?
            </h3>
            <p className="text-xs text-slate-400 mt-1">
              This action will pull remote changes and push your local commits to the tracking repository.
            </p>
          </div>
        </div>

        {/* Sync Status Counters */}
        <div className="grid grid-cols-2 gap-2 my-4 bg-slate-950/80 border border-slate-800/80 rounded-md p-3 text-xs">
          <div className="flex items-center gap-2 text-emerald-400">
            <ArrowUpRight className="w-4 h-4" />
            <span>{outgoingCount} commits to push</span>
          </div>
          <div className="flex items-center gap-2 text-cyan-400">
            <ArrowDownLeft className="w-4 h-4" />
            <span>{incomingCount} commits to pull</span>
          </div>
        </div>

        {/* Divergence Warning */}
        {hasDivergence && (
          <div className="flex items-start gap-2 p-2.5 mb-4 bg-amber-950/40 border border-amber-800/50 rounded text-amber-300 text-xs">
            <AlertTriangle className="w-4 h-4 shrink-0 text-amber-400 mt-0.5" />
            <span>
              Both local and remote branches have new commits. Syncing will generate a merge commit or require rebasing.
            </span>
          </div>
        )}

        {/* Action Controls */}
        <div className="flex items-center justify-end gap-2 mt-4 pt-3 border-t border-slate-800">
          <button
            onClick={onCancel}
            className="px-3 py-1.5 rounded text-xs text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className="px-3.5 py-1.5 rounded text-xs font-semibold bg-cyan-500 hover:bg-cyan-400 text-slate-950 transition-colors"
          >
            Confirm Sync
          </button>
        </div>
      </div>
    </div>
  );
};

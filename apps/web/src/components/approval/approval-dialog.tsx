'use client';

import { useRef, useState } from 'react';
import { Eye, Lightbulb, AlertCircle } from 'lucide-react';

export interface ApprovalRequest {
  id: string;
  taskId: string;
  taskName: string;
  reason: string;
  evidenceLink?: string;
  suggestedAction?: string;
  estimatedImpact?: string;
  policyRef?: string;
}

interface ApprovalDialogProps {
  request: ApprovalRequest;
  onApprove: (comment: string, evidenceLink?: string) => void;
  onReject: (reason: string) => void;
  onClose: () => void;
  open?: boolean;
}

export function ApprovalDialog({
  request,
  onApprove,
  onReject,
  onClose,
  open = true,
}: ApprovalDialogProps) {
  const [mode, setMode] = useState<'view' | 'approve' | 'reject'>('view');
  const [comment, setComment] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [evidenceLink, setEvidenceLink] = useState(request.evidenceLink || '');

  if (!open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-sg-neutral-900 rounded-lg shadow-2xl max-w-2xl w-full max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="px-6 py-4 border-b border-sg-neutral-200 dark:border-sg-neutral-800">
          <h2 className="text-2xl font-bold text-sg-neutral-900 dark:text-sg-neutral-50">
            Approval Required
          </h2>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
            Review and decide: {request.taskName}
          </p>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
          {/* Task Info */}
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              What needs approval?
            </h3>
            <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">{request.reason}</p>
          </div>

          {/* Suggested Action */}
          {request.suggestedAction && (
            <div className="bg-sg-blue-50 dark:bg-sg-blue-900/30 border border-sg-blue-200 dark:border-sg-blue-800 rounded-lg p-3 flex gap-3">
              <Lightbulb className="w-5 h-5 text-sg-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-sg-blue-900 dark:text-sg-blue-100 uppercase">
                  Suggested Action
                </p>
                <p className="text-sm text-sg-blue-900 dark:text-sg-blue-100 mt-1">
                  {request.suggestedAction}
                </p>
              </div>
            </div>
          )}

          {/* Impact */}
          {request.estimatedImpact && (
            <div>
              <h4 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Estimated Impact
              </h4>
              <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300">
                {request.estimatedImpact}
              </p>
            </div>
          )}

          {/* Evidence */}
          {request.evidenceLink && (
            <a
              href={request.evidenceLink}
              className="inline-flex items-center gap-2 text-sm text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline"
            >
              <Eye className="w-4 h-4" />
              View supporting evidence
            </a>
          )}

          {/* Policy Reference */}
          {request.policyRef && (
            <div className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 bg-sg-neutral-50 dark:bg-sg-neutral-800 rounded p-2">
              Policy: <code className="text-xs">{request.policyRef}</code>
            </div>
          )}

          {/* Decision Modes */}
          {mode === 'view' && (
            <div className="pt-4 space-y-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
              <p className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Your decision?
              </p>
            </div>
          )}

          {mode === 'approve' && (
            <div className="space-y-3 pt-4 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
              <label className="block">
                <p className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-2">
                  Comment (optional)
                </p>
                <textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Add approval comment..."
                  className="w-full px-3 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-50"
                  rows={3}
                />
              </label>
              <label className="block">
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mb-2">
                  Evidence link
                </p>
                <input
                  type="url"
                  value={evidenceLink}
                  onChange={(e) => setEvidenceLink(e.target.value)}
                  placeholder="https://..."
                  className="w-full px-3 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-50"
                />
              </label>
            </div>
          )}

          {mode === 'reject' && (
            <div className="space-y-3 pt-4 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
              <label className="block">
                <p className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-2">
                  Reason for rejection
                </p>
                <textarea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="Explain your decision..."
                  className="w-full px-3 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg bg-white dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-50"
                  rows={3}
                />
              </label>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="px-6 py-4 border-t border-sg-neutral-200 dark:border-sg-neutral-800 flex gap-3 justify-end">
          {mode === 'view' && (
            <>
              <button
                onClick={onClose}
                className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800 transition-colors font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
              >
                Close
              </button>
              <button
                onClick={() => setMode('reject')}
                className="px-4 py-2 border border-sg-red-300 dark:border-sg-red-700 text-sg-red-900 dark:text-sg-red-100 rounded-lg hover:bg-sg-red-50 dark:hover:bg-sg-red-900/20 transition-colors font-semibold"
              >
                Reject
              </button>
              <button
                onClick={() => setMode('approve')}
                className="px-4 py-2 bg-sg-green-600 text-white rounded-lg hover:bg-sg-green-700 transition-colors font-semibold"
              >
                Approve
              </button>
            </>
          )}

          {mode === 'approve' && (
            <>
              <button
                onClick={() => setMode('view')}
                className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800 transition-colors font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
              >
                Back
              </button>
              <button
                onClick={() => {
                  onApprove(comment, evidenceLink);
                  onClose();
                }}
                className="px-4 py-2 bg-sg-green-600 text-white rounded-lg hover:bg-sg-green-700 transition-colors font-semibold"
              >
                Confirm Approval
              </button>
            </>
          )}

          {mode === 'reject' && (
            <>
              <button
                onClick={() => setMode('view')}
                className="px-4 py-2 border border-sg-neutral-300 dark:border-sg-neutral-700 rounded-lg hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800 transition-colors font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
              >
                Back
              </button>
              <button
                onClick={() => {
                  onReject(rejectionReason);
                  onClose();
                }}
                className="px-4 py-2 bg-sg-red-600 text-white rounded-lg hover:bg-sg-red-700 transition-colors font-semibold"
              >
                Confirm Rejection
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

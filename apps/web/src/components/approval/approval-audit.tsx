'use client';

import { CheckCircle2, XCircle, Clock, User, MessageSquare } from 'lucide-react';
import { useState } from 'react';

export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'expired' | 'superseded';

export interface ApprovalRecord {
  id: string;
  taskId: string;
  status: ApprovalStatus;
  requestedAt: number;
  respondedAt?: number;
  respondedBy?: string;
  approverEmail?: string;
  reason?: string;
  comment?: string;
  evidenceLink?: string;
  policyRef?: string;
  supercededBy?: string;
}

interface ApprovalAuditProps {
  records: ApprovalRecord[];
  compact?: boolean;
  showExpanded?: boolean;
}

const STATUS_CONFIG: Record<ApprovalStatus, { icon: any; label: string; color: string }> = {
  pending: {
    icon: Clock,
    label: 'Pending',
    color: 'text-sg-amber-600 bg-sg-amber-50 dark:bg-sg-amber-900/30',
  },
  approved: {
    icon: CheckCircle2,
    label: 'Approved',
    color: 'text-sg-green-600 bg-sg-green-50 dark:bg-sg-green-900/30',
  },
  rejected: {
    icon: XCircle,
    label: 'Rejected',
    color: 'text-sg-red-600 bg-sg-red-50 dark:bg-sg-red-900/30',
  },
  expired: {
    icon: Clock,
    label: 'Expired',
    color: 'text-sg-neutral-600 bg-sg-neutral-50 dark:bg-sg-neutral-900/30',
  },
  superseded: {
    icon: MessageSquare,
    label: 'Superseded',
    color: 'text-sg-purple-600 bg-sg-purple-50 dark:bg-sg-purple-900/30',
  },
};

interface ApprovalItemProps {
  record: ApprovalRecord;
  showExpanded?: boolean;
}

function ApprovalItem({ record, showExpanded = false }: ApprovalItemProps) {
  const [expanded, setExpanded] = useState(showExpanded);
  const config = STATUS_CONFIG[record.status];
  const Icon = config.icon;

  return (
    <div className={`border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg overflow-hidden ${config.color}`}>
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full text-left p-4 hover:bg-black/5 dark:hover:bg-white/5 transition-colors flex items-center justify-between"
      >
        <div className="flex items-center gap-3">
          <Icon className="w-5 h-5 flex-shrink-0" />
          <div>
            <h4 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              {config.label}
            </h4>
            <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 mt-0.5">
              {new Date(record.requestedAt).toLocaleString()}
            </p>
          </div>
        </div>
        <span className="text-sg-neutral-600 dark:text-sg-neutral-400">
          {expanded ? '▼' : '▶'}
        </span>
      </button>

      {expanded && (
        <div className="px-4 py-3 border-t border-current/20 space-y-2 text-sm">
          {record.respondedAt && (
            <div>
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                Responded by: {record.approverEmail || 'Unknown'}
              </p>
              <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                {new Date(record.respondedAt).toLocaleString()}
              </p>
            </div>
          )}

          {record.comment && (
            <div>
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Comment</p>
              <p className="text-sg-neutral-700 dark:text-sg-neutral-300">{record.comment}</p>
            </div>
          )}

          {record.reason && (
            <div>
              <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">Reason</p>
              <p className="text-sg-neutral-700 dark:text-sg-neutral-300">{record.reason}</p>
            </div>
          )}

          {record.evidenceLink && (
            <a
              href={record.evidenceLink}
              className="text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline text-xs"
            >
              View evidence
            </a>
          )}

          {record.policyRef && (
            <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
              Policy: <code>{record.policyRef}</code>
            </p>
          )}

          {record.supercededBy && (
            <p className="text-xs text-sg-purple-600 dark:text-sg-purple-400">
              Superseded by approval: <code>{record.supercededBy}</code>
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export function ApprovalAudit({ records, compact = false, showExpanded = false }: ApprovalAuditProps) {
  if (compact && records.length === 0) return null;

  const pending = records.filter((r) => r.status === 'pending');
  const completed = records.filter((r) => r.status !== 'pending');

  return (
    <div className="space-y-4">
      {pending.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            Pending Approvals ({pending.length})
          </h4>
          <div className="space-y-2">
            {pending.map((record) => (
              <ApprovalItem key={record.id} record={record} showExpanded={showExpanded} />
            ))}
          </div>
        </div>
      )}

      {completed.length > 0 && (
        <div className="space-y-2">
          <details className="cursor-pointer">
            <summary className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 hover:text-sg-red-600 transition-colors">
              Approval History ({completed.length})
            </summary>
            <div className="space-y-2 mt-2">
              {completed.map((record) => (
                <ApprovalItem key={record.id} record={record} showExpanded={showExpanded} />
              ))}
            </div>
          </details>
        </div>
      )}

      {records.length === 0 && (
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 text-center py-4">
          No approvals required
        </p>
      )}
    </div>
  );
}

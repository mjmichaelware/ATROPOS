'use client';

import { FileText, Package, CheckCircle2, XCircle, Clock, Eye, Link as LinkIcon } from 'lucide-react';
import { useState } from 'react';

export type EvidenceType = 'artifact' | 'verification' | 'approval' | 'execution' | 'reference';

export interface EvidenceItem {
  id: string;
  type: EvidenceType;
  title: string;
  description?: string;
  timestamp?: string;
  source?: string;
  link?: string;
  metadata?: Record<string, any>;
  verified?: boolean;
  impact?: 'critical' | 'major' | 'minor';
}

interface EvidenceBrowserProps {
  items: EvidenceItem[];
  onItemClick?: (item: EvidenceItem) => void;
  compact?: boolean;
  maxItems?: number;
}

const EVIDENCE_ICONS = {
  artifact: FileText,
  verification: CheckCircle2,
  approval: Eye,
  execution: Clock,
  reference: LinkIcon,
};

const EVIDENCE_COLORS = {
  artifact: 'text-sg-blue-600 bg-sg-blue-50 dark:bg-sg-blue-900',
  verification: 'text-sg-green-600 bg-sg-green-50 dark:bg-sg-green-900',
  approval: 'text-sg-purple-600 bg-sg-purple-50 dark:bg-sg-purple-900',
  execution: 'text-sg-amber-600 bg-sg-amber-50 dark:bg-sg-amber-900',
  reference: 'text-sg-neutral-600 bg-sg-neutral-50 dark:bg-sg-neutral-900',
};

export function EvidenceBrowser({
  items,
  onItemClick,
  compact = false,
  maxItems = 10,
}: EvidenceBrowserProps) {
  const [expanded, setExpanded] = useState(!compact);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const displayItems = items.slice(0, maxItems);
  const hasMore = items.length > maxItems;

  if (compact && !expanded) {
    return (
      <button
        onClick={() => setExpanded(true)}
        className="flex items-center gap-2 text-sm text-sg-neutral-600 hover:text-sg-red-600 transition-colors"
      >
        <Eye className="w-4 h-4" />
        View evidence ({items.length} items)
      </button>
    );
  }

  return (
    <div className="space-y-3">
      {/* Header */}
      {compact && (
        <button
          onClick={() => setExpanded(false)}
          className="flex items-center gap-2 text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50"
        >
          <Eye className="w-4 h-4" />
          Evidence ({items.length} items)
        </button>
      )}

      {/* Evidence list */}
      {displayItems.length > 0 ? (
        <div className="space-y-2">
          {displayItems.map((item) => {
            const Icon = EVIDENCE_ICONS[item.type];
            const colorClass = EVIDENCE_COLORS[item.type];
            const isSelected = selectedId === item.id;

            return (
              <button
                key={item.id}
                onClick={() => {
                  setSelectedId(item.id);
                  onItemClick?.(item);
                }}
                className={`w-full text-left p-3 rounded-md border transition-colors ${
                  isSelected
                    ? 'border-sg-red-600 bg-sg-red-50 dark:bg-sg-red-900'
                    : 'border-sg-neutral-200 dark:border-sg-neutral-800 hover:border-sg-red-300 dark:hover:border-sg-red-700'
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className={`mt-1 ${colorClass} p-1 rounded`}>
                    <Icon className="w-4 h-4" aria-hidden="true" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 truncate">
                      {item.title}
                    </h4>
                    {item.description && (
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 line-clamp-2">
                        {item.description}
                      </p>
                    )}
                    <div className="flex items-center gap-2 mt-1 text-xs text-sg-neutral-500 dark:text-sg-neutral-400">
                      {item.timestamp && <span>{item.timestamp}</span>}
                      {item.verified && (
                        <span className="text-green-600">
                          ✓ Verified
                        </span>
                      )}
                      {item.impact && (
                        <span
                          className={`px-1 py-0.5 rounded ${
                            item.impact === 'critical'
                              ? 'bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300'
                              : item.impact === 'major'
                                ? 'bg-amber-100 dark:bg-amber-900 text-amber-700 dark:text-amber-300'
                                : 'bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300'
                          }`}
                        >
                          {item.impact}
                        </span>
                      )}
                    </div>
                  </div>
                  {item.link && (
                    <a
                      href={item.link}
                      className="text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 flex-shrink-0"
                      onClick={(e) => e.stopPropagation()}
                      title="Open evidence"
                    >
                      <LinkIcon className="w-4 h-4" />
                    </a>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      ) : (
        <div className="text-center py-6">
          <Package className="w-8 h-8 text-sg-neutral-400 mx-auto mb-2" />
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            No evidence recorded
          </p>
        </div>
      )}

      {/* "Show more" */}
      {hasMore && (
        <button className="text-sm text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 font-semibold">
          Show {items.length - maxItems} more
        </button>
      )}
    </div>
  );
}

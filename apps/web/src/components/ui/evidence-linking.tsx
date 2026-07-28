import { Link2, FileText, Hash, Clock, User, MapPin } from 'lucide-react';

export interface Evidence {
  id: string;
  type: 'artifact' | 'decision' | 'approval' | 'error' | 'checkpoint';
  title: string;
  timestamp: string;
  actor?: string;
  link?: string;
  summary?: string;
  tags?: string[];
}

export interface EvidenceLinkingProps {
  evidence: Evidence[];
  className?: string;
  maxItems?: number;
  linkToPage?: (evidence: Evidence) => void;
}

/**
 * Evidence linking component for ATROPOS HOE.
 * Shows provenance and traceability across the system.
 * Every decision, failure, and artifact is linked with evidence.
 */
export function EvidenceLinking({
  evidence,
  className = '',
  maxItems = 5,
  linkToPage,
}: EvidenceLinkingProps) {
  if (!evidence || evidence.length === 0) {
    return null;
  }

  const displayed = evidence.slice(0, maxItems);
  const hasMore = evidence.length > maxItems;

  const typeIcon = (type: Evidence['type']) => {
    switch (type) {
      case 'artifact':
        return <FileText className="w-4 h-4" />;
      case 'decision':
        return <MapPin className="w-4 h-4" />;
      case 'approval':
        return <Hash className="w-4 h-4" />;
      case 'error':
        return <Link2 className="w-4 h-4" />;
      case 'checkpoint':
        return <Clock className="w-4 h-4" />;
      default:
        return <FileText className="w-4 h-4" />;
    }
  };

  const typeColor = (type: Evidence['type']) => {
    switch (type) {
      case 'artifact':
        return 'text-sg-blue-600';
      case 'decision':
        return 'text-sg-purple-600';
      case 'approval':
        return 'text-sg-amber-600';
      case 'error':
        return 'text-sg-red-600';
      case 'checkpoint':
        return 'text-sg-green-600';
      default:
        return 'text-sg-neutral-600';
    }
  };

  const typeLabel = (type: Evidence['type']) => {
    switch (type) {
      case 'artifact':
        return 'Artifact';
      case 'decision':
        return 'Decision';
      case 'approval':
        return 'Approval';
      case 'error':
        return 'Error';
      case 'checkpoint':
        return 'Checkpoint';
      default:
        return 'Evidence';
    }
  };

  return (
    <div className={`space-y-3 ${className}`}>
      <div className="flex items-center gap-2 text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
        <Link2 className="w-4 h-4" />
        Evidence Trail
      </div>

      <div className="space-y-2">
        {displayed.map((item, idx) => (
          <button
            key={item.id}
            onClick={() => linkToPage?.(item)}
            className="w-full text-left p-3 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors"
          >
            <div className="flex items-start gap-3">
              <div className={`flex-shrink-0 ${typeColor(item.type)}`}>
                {typeIcon(item.type)}
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <h4 className="text-sm font-medium text-sg-neutral-900 dark:text-sg-neutral-50 truncate">
                    {item.title}
                  </h4>
                  <span className="text-xs text-sg-neutral-500 flex-shrink-0">
                    {typeLabel(item.type)}
                  </span>
                </div>

                {item.summary && (
                  <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 line-clamp-2 mt-1">
                    {item.summary}
                  </p>
                )}

                <div className="flex items-center gap-4 mt-2 text-xs text-sg-neutral-500">
                  <div className="flex items-center gap-1">
                    <Clock className="w-3 h-3" />
                    {new Date(item.timestamp).toLocaleTimeString()}
                  </div>
                  {item.actor && (
                    <div className="flex items-center gap-1">
                      <User className="w-3 h-3" />
                      {item.actor}
                    </div>
                  )}
                </div>

                {item.tags && item.tags.length > 0 && (
                  <div className="flex gap-1 mt-2 flex-wrap">
                    {item.tags.map((tag) => (
                      <span
                        key={tag}
                        className="text-xs px-2 py-0.5 bg-sg-neutral-100 dark:bg-sg-neutral-800 text-sg-neutral-600 dark:text-sg-neutral-400 rounded"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {item.link && (
                <Link2 className="w-4 h-4 text-sg-neutral-400 flex-shrink-0" />
              )}
            </div>
          </button>
        ))}
      </div>

      {hasMore && (
        <button className="text-sm text-sg-blue-600 dark:text-sg-blue-400 hover:underline">
          View all {evidence.length} items
        </button>
      )}
    </div>
  );
}

import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';

export interface ProgressiveDisclosureProps {
  title: string;
  level?: 'simple' | 'detailed' | 'expert';
  /** Optional: a section may instead supply the per-level content below. */
  children?: React.ReactNode;
  simpleContent?: React.ReactNode;
  detailedContent?: React.ReactNode;
  expertContent?: React.ReactNode;
  defaultExpanded?: boolean;
  onToggle?: (expanded: boolean) => void;
  className?: string;
}

/**
 * Progressive disclosure component for HOE pattern.
 * Shows simplified content by default, deeper details on demand.
 *
 * Levels:
 * - simple: Most important info only (default)
 * - detailed: Additional context and metrics
 * - expert: Full technical details and controls
 */
export function ProgressiveDisclosure({
  title,
  level = 'simple',
  children,
  simpleContent,
  detailedContent,
  expertContent,
  defaultExpanded = false,
  onToggle,
  className = '',
}: ProgressiveDisclosureProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  const handleToggle = () => {
    const newState = !expanded;
    setExpanded(newState);
    onToggle?.(newState);
  };

  const content = (() => {
    if (level === 'simple' && simpleContent) return simpleContent;
    if ((level === 'detailed' || level === 'simple') && detailedContent) return detailedContent;
    if (expertContent) return expertContent;
    return children;
  })();

  return (
    <div className={`space-y-2 ${className}`}>
      <button
        onClick={handleToggle}
        className="flex items-center gap-2 text-left hover:text-sg-red-600 transition-colors"
        aria-expanded={expanded}
        aria-label={`${expanded ? 'Collapse' : 'Expand'} ${title}`}
      >
        {expanded ? (
          <ChevronDown className="w-4 h-4" />
        ) : (
          <ChevronRight className="w-4 h-4" />
        )}
        <span className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">{title}</span>
        {level !== 'simple' && (
          <span className="ml-auto text-xs text-sg-neutral-500 px-2 py-1 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded">
            {level}
          </span>
        )}
      </button>

      {expanded && <div className="pl-4 space-y-2">{content}</div>}
    </div>
  );
}

/**
 * Wrapper for managing multiple disclosure sections at once.
 */
export interface DisclosureGroupProps {
  level?: 'simple' | 'detailed' | 'expert';
  sections: Array<{
    title: string;
    simpleContent?: React.ReactNode;
    detailedContent?: React.ReactNode;
    expertContent?: React.ReactNode;
  }>;
  className?: string;
}

export function DisclosureGroup({ level = 'simple', sections, className = '' }: DisclosureGroupProps) {
  return (
    <div className={`space-y-4 ${className}`}>
      {sections.map((section, idx) => (
        <ProgressiveDisclosure
          key={idx}
          title={section.title}
          level={level}
          simpleContent={section.simpleContent}
          detailedContent={section.detailedContent}
          expertContent={section.expertContent}
        />
      ))}
    </div>
  );
}

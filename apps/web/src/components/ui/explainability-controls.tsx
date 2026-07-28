'use client';

import { HelpCircle, Zap, Eye } from 'lucide-react';
import { useState } from 'react';

export interface ExplainabilityContent {
  why?: string;
  how?: string;
  evidence?: string;
}

interface ExplainabilityControlsProps {
  content: ExplainabilityContent;
  size?: 'sm' | 'md';
  inline?: boolean;
}

export function ExplainabilityControls({
  content,
  size = 'md',
  inline = false,
}: ExplainabilityControlsProps) {
  const [openSection, setOpenSection] = useState<'why' | 'how' | 'evidence' | null>(null);

  const sizeClasses = {
    sm: 'text-xs gap-1',
    md: 'text-sm gap-2',
  };

  const sections = [
    { id: 'why', label: 'Why?', icon: HelpCircle, content: content.why },
    { id: 'how', label: 'How?', icon: Zap, content: content.how },
    { id: 'evidence', label: 'Evidence', icon: Eye, content: content.evidence },
  ] as const;

  if (inline) {
    return (
      <div className={`flex flex-wrap items-center ${sizeClasses[size]}`}>
        {sections
          .filter((s) => s.content)
          .map((section) => (
            <button
              key={section.id}
              onClick={() =>
                setOpenSection(openSection === section.id ? null : section.id)
              }
              className="text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline transition-colors"
              title={section.label}
            >
              {section.label}
            </button>
          ))}
      </div>
    );
  }

  return (
    <div className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg overflow-hidden">
      {sections.map((section) => {
        if (!section.content) return null;

        const Icon = section.icon;
        const isOpen = openSection === section.id;

        return (
          <div key={section.id} className="border-b border-sg-neutral-200 dark:border-sg-neutral-800 last:border-b-0">
            <button
              onClick={() => setOpenSection(isOpen ? null : section.id)}
              className="w-full flex items-center gap-2 p-3 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors text-left"
            >
              <Icon className="w-4 h-4 text-sg-red-600 flex-shrink-0" aria-hidden="true" />
              <span className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                {section.label}
              </span>
              <span className={`ml-auto text-sg-neutral-400 transition-transform ${isOpen ? 'rotate-180' : ''}`}>
                ▼
              </span>
            </button>

            {isOpen && (
              <div className="px-3 py-2 bg-sg-neutral-50 dark:bg-sg-neutral-900 border-t border-sg-neutral-200 dark:border-sg-neutral-800">
                <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300 whitespace-pre-wrap">
                  {section.content}
                </p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

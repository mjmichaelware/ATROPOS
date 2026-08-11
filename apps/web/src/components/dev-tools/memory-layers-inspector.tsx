'use client';

import { useState } from 'react';
import { ChevronDown, Database, Lock, Brain, FileStack, Target, BookOpen, CheckCircle } from 'lucide-react';

interface MemoryLayer {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  size?: number;
  items?: number;
  readonly: boolean;
  content?: string[];
}

const memoryLayers: MemoryLayer[] = [
  {
    id: 'temporary',
    name: 'Temporary Memory',
    description: 'Session-level working memory cleared on restart',
    icon: <Database className="w-5 h-5" />,
    readonly: false,
    items: 0,
    content: ['Session metadata', 'Current operation state', 'Pending approvals'],
  },
  {
    id: 'conversation',
    name: 'Conversation Memory',
    description: 'Chat history and context within current session',
    icon: <Brain className="w-5 h-5" />,
    readonly: false,
    items: 0,
    content: ['Message history', 'User preferences', 'Context window'],
  },
  {
    id: 'project',
    name: 'Project Memory',
    description: 'Project-scoped state, files, conversations, and work history',
    icon: <FileStack className="w-5 h-5" />,
    readonly: false,
    items: 0,
    content: ['Project metadata', 'Work history', 'File artifacts', 'Conversations'],
  },
  {
    id: 'workspace',
    name: 'Workspace Memory',
    description: 'User workspace settings, preferences, and configuration',
    icon: <Target className="w-5 h-5" />,
    readonly: false,
    items: 0,
    content: ['Workspace settings', 'User preferences', 'Theme configuration', 'API keys'],
  },
  {
    id: 'knowledge',
    name: 'Knowledge Layer',
    description: 'Learned patterns, domain knowledge, and training data',
    icon: <BookOpen className="w-5 h-5" />,
    readonly: true,
    items: 0,
    content: ['Learned patterns', 'Domain models', 'Best practices'],
  },
  {
    id: 'authority',
    name: 'Authority & Policy',
    description: 'Policy constraints, permissions, and authority rules',
    icon: <Lock className="w-5 h-5" />,
    readonly: true,
    items: 0,
    content: ['Access policies', 'Safety constraints', 'Authority rules', 'Verification gates'],
  },
  {
    id: 'learning',
    name: 'Learning Observations',
    description: 'Observations from execution for future improvement',
    icon: <Brain className="w-5 h-5" />,
    readonly: false,
    items: 0,
    content: ['Performance metrics', 'Failure patterns', 'Optimization opportunities'],
  },
  {
    id: 'evidence',
    name: 'Evidence Layer',
    description: 'Verified facts, artifacts, and verified evidence',
    icon: <CheckCircle className="w-5 h-5" />,
    readonly: true,
    items: 0,
    content: ['Verified artifacts', 'Checksums', 'Authority signatures', 'Verification records'],
  },
];

interface ExpandedLayers {
  [key: string]: boolean;
}

export function MemoryLayersInspector() {
  const [expanded, setExpanded] = useState<ExpandedLayers>({});

  const toggleLayer = (layerId: string) => {
    setExpanded((prev) => ({
      ...prev,
      [layerId]: !prev[layerId],
    }));
  };

  return (
    <div className="space-y-2">
      <div className="px-4 py-3 border-b border-sg-neutral-200 dark:border-sg-neutral-800">
        <h3 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
          Memory Layers
        </h3>
        <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400 mt-1">
          Eight persistent and temporary memory layers power ATROPOS reasoning and decision-making
        </p>
      </div>

      <div className="space-y-1">
        {memoryLayers.map((layer) => (
          <div
            key={layer.id}
            className="border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg overflow-hidden"
          >
            <button
              onClick={() => toggleLayer(layer.id)}
              className="w-full flex items-center gap-3 px-4 py-3 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900/50 transition-colors text-left"
            >
              <ChevronDown
                className={`w-4 h-4 text-sg-neutral-600 dark:text-sg-neutral-400 transition-transform ${
                  expanded[layer.id] ? 'rotate-180' : ''
                }`}
              />
              <div className="flex-shrink-0 w-5 h-5 text-sg-red-600">{layer.icon}</div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
                  {layer.name}
                </p>
                <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                  {layer.description}
                </p>
              </div>
              {layer.readonly && (
                <span className="text-xs px-2 py-1 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded text-sg-neutral-600 dark:text-sg-neutral-400 flex-shrink-0">
                  Read-only
                </span>
              )}
              {layer.items !== undefined && (
                <span className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 flex-shrink-0">
                  {layer.items} items
                </span>
              )}
            </button>

            {expanded[layer.id] && layer.content && (
              <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900/30 border-t border-sg-neutral-200 dark:border-sg-neutral-800 px-4 py-3 space-y-2">
                <div className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 space-y-1">
                  {layer.content.map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2 pl-9">
                      <span className="w-1 h-1 rounded-full bg-sg-neutral-400"></span>
                      <span>{item}</span>
                    </div>
                  ))}
                </div>

                <div className="pt-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800 pl-9">
                  <details className="text-xs">
                    <summary className="cursor-pointer font-medium text-sg-neutral-700 dark:text-sg-neutral-300">
                      Technical Details
                    </summary>
                    <pre className="mt-2 p-2 bg-black/50 dark:bg-white/5 rounded text-xs overflow-x-auto text-sg-neutral-400">
{`Layer: ${layer.id}
Type: ${layer.readonly ? 'Read-only' : 'Mutable'}
Persistence: ${
  layer.id === 'temporary'
    ? 'Session-scoped'
    : layer.id === 'conversation'
      ? 'Conversation-scoped'
      : layer.id === 'project'
        ? 'Project-scoped'
        : layer.id === 'workspace'
          ? 'User-scoped'
          : 'Global'
}
Size: ${layer.size ? `${(layer.size / 1024).toFixed(2)} KB` : 'N/A'}`}
                    </pre>
                  </details>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="mt-4 p-4 bg-sg-blue-50 dark:bg-sg-blue-900/20 border border-sg-blue-200 dark:border-sg-blue-800 rounded-lg">
        <p className="text-sm text-sg-blue-900 dark:text-sg-blue-100">
          <span className="font-semibold">Memory Architecture:</span> ATROPOS maintains eight independent memory layers
          for resilience and clarity. Temporary and conversation layers reset, while project, workspace, and authority
          layers persist. Evidence layer is cryptographically verified and immutable.
        </p>
      </div>
    </div>
  );
}

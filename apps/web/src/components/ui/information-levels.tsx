'use client';

import { useState } from 'react';
import { ChevronDown } from 'lucide-react';

export type InformationLevel = 1 | 2 | 3 | 4;

interface InformationLevelDescriptor {
  level: InformationLevel;
  name: string;
  description: string;
  details: string[];
}

const LEVELS: Record<InformationLevel, InformationLevelDescriptor> = {
  1: {
    level: 1,
    name: 'Simple',
    description: 'Only information necessary to complete work',
    details: [
      'Current task/objective',
      'Progress indicator',
      'Next action',
      'Approval/blockers',
    ],
  },
  2: {
    level: 2,
    name: 'Professional',
    description: 'Adds execution status, project metrics, workflow details',
    details: [
      'All Level 1 info',
      'Execution status',
      'Project metrics',
      'Workflow stages',
      'Resource usage',
    ],
  },
  3: {
    level: 3,
    name: 'Engineering',
    description: 'Adds architecture, agents, routing, verification, dependencies',
    details: [
      'All Level 2 info',
      'Architecture visualization',
      'Agent responsibilities',
      'Provider routing decisions',
      'Verification gates',
      'Dependency graphs',
    ],
  },
  4: {
    level: 4,
    name: 'Internal',
    description: 'Complete runtime state via Developer Tools',
    details: [
      'All Level 3 info',
      'Compiler outputs',
      'Runtime state',
      'Source authority',
      'Execution graphs',
      'Policy engine state',
      'Checkpoint recovery data',
    ],
  },
};

interface InformationLevelsProps {
  currentLevel: InformationLevel;
  onLevelChange?: (level: InformationLevel) => void;
  compact?: boolean;
}

export function InformationLevels({
  currentLevel,
  onLevelChange,
  compact = false,
}: InformationLevelsProps) {
  const [showDetails, setShowDetails] = useState(false);
  const current = LEVELS[currentLevel];

  if (compact) {
    return (
      <div className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
        Information Level: <span className="font-semibold">{current.name}</span>
      </div>
    );
  }

  return (
    <div className="bg-sg-neutral-50 dark:bg-sg-neutral-900 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg p-4">
      <div className="space-y-3">
        {/* Level indicator */}
        <div>
          <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-50 mb-2">
            Information Depth
          </h3>
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 mb-3">
            {current.description}
          </p>

          {/* Level buttons */}
          <div className="flex gap-2">
            {Object.values(LEVELS).map((level) => (
              <button
                key={level.level}
                onClick={() => onLevelChange?.(level.level)}
                className={`px-3 py-2 text-xs font-semibold rounded-md transition-colors ${
                  currentLevel === level.level
                    ? 'bg-sg-red-600 text-white'
                    : 'bg-sg-neutral-200 dark:bg-sg-neutral-800 text-sg-neutral-900 dark:text-sg-neutral-100 hover:bg-sg-neutral-300 dark:hover:bg-sg-neutral-700'
                }`}
                aria-pressed={currentLevel === level.level}
              >
                {level.level}. {level.name}
              </button>
            ))}
          </div>
        </div>

        {/* Details */}
        <div>
          <button
            onClick={() => setShowDetails(!showDetails)}
            className="flex items-center gap-2 text-xs font-semibold text-sg-neutral-700 dark:text-sg-neutral-300 hover:text-sg-red-600 transition-colors"
          >
            <ChevronDown
              className={`w-3 h-3 transition-transform ${showDetails ? 'rotate-180' : ''}`}
            />
            What's shown at this level?
          </button>

          {showDetails && (
            <div className="mt-2 pt-2 border-t border-sg-neutral-200 dark:border-sg-neutral-700">
              <ul className="text-xs space-y-1 text-sg-neutral-700 dark:text-sg-neutral-300">
                {current.details.map((detail, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="text-sg-red-600">▪</span>
                    {detail}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

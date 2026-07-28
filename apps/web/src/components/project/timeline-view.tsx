'use client';

import { Clock, Zap, CheckCircle, AlertCircle, Eye } from 'lucide-react';

interface TimelineEvent {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  type: 'task' | 'approval' | 'error' | 'completion' | 'state-change';
  details?: string;
  evidence?: {
    type: string;
    link?: string;
    label?: string;
  };
}

interface TimelineViewProps {
  events?: TimelineEvent[];
  loading?: boolean;
}

const eventIcons = {
  task: Zap,
  approval: Eye,
  error: AlertCircle,
  completion: CheckCircle,
  'state-change': Clock,
};

const eventColors = {
  task: 'bg-sg-blue-100 dark:bg-sg-blue-900/20 text-sg-blue-600 dark:text-sg-blue-400',
  approval: 'bg-sg-purple-100 dark:bg-sg-purple-900/20 text-sg-purple-600 dark:text-sg-purple-400',
  error: 'bg-sg-red-100 dark:bg-sg-red-900/20 text-sg-red-600 dark:text-sg-red-400',
  completion: 'bg-sg-green-100 dark:bg-sg-green-900/20 text-sg-green-600 dark:text-sg-green-400',
  'state-change':
    'bg-sg-amber-100 dark:bg-sg-amber-900/20 text-sg-amber-600 dark:text-sg-amber-400',
};

export function TimelineView({ events = [], loading = false }: TimelineViewProps) {
  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading timeline...</p>
      </div>
    );
  }

  if (events.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-96 text-center space-y-3">
        <Clock className="w-12 h-12 text-sg-neutral-400" />
        <div>
          <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
            No events recorded
          </p>
          <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
            Events will appear here as work progresses
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {events.map((event, idx) => {
        const IconComponent = eventIcons[event.type];
        const colorClass = eventColors[event.type];
        const isLast = idx === events.length - 1;

        return (
          <div key={event.id} className="relative">
            {/* Timeline line */}
            {!isLast && (
              <div className="absolute left-6 top-16 bottom-0 w-0.5 bg-sg-neutral-200 dark:bg-sg-neutral-800" />
            )}

            {/* Event */}
            <div className="flex gap-4">
              {/* Icon circle */}
              <div className={`flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center ${colorClass} relative z-10`}>
                <IconComponent className="w-5 h-5" />
              </div>

              {/* Content */}
              <div className="flex-1 pt-2 pb-4">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h4 className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                      {event.action}
                    </h4>
                    <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
                      By {event.actor}
                    </p>
                  </div>
                  <time className="text-xs text-sg-neutral-500 flex-shrink-0">
                    {new Date(event.timestamp).toLocaleString()}
                  </time>
                </div>

                {event.details && (
                  <p className="text-sm text-sg-neutral-700 dark:text-sg-neutral-300 mt-2">
                    {event.details}
                  </p>
                )}

                {event.evidence && (
                  <button className="text-xs mt-2 text-sg-red-600 hover:text-sg-red-700 dark:hover:text-sg-red-500 underline transition-colors">
                    {event.evidence.label || 'View evidence'}
                  </button>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

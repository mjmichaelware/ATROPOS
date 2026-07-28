'use client';

import { Activity, Zap, Clock, CheckCircle, AlertCircle } from 'lucide-react';

interface ExecutionTask {
  id: string;
  title: string;
  status: 'queued' | 'running' | 'completed' | 'failed';
  progress: number;
  startedAt?: string;
  estimatedDuration?: number;
  agent?: string;
  message?: string;
}

interface ExecutionMonitorProps {
  tasks?: ExecutionTask[];
  loading?: boolean;
}

const statusIcons = {
  queued: Clock,
  running: Activity,
  completed: CheckCircle,
  failed: AlertCircle,
};

const statusColors = {
  queued: 'bg-sg-neutral-100 dark:bg-sg-neutral-800 text-sg-neutral-600 dark:text-sg-neutral-400',
  running: 'bg-sg-amber-100 dark:bg-sg-amber-900/20 text-sg-amber-600 dark:text-sg-amber-400',
  completed: 'bg-sg-green-100 dark:bg-sg-green-900/20 text-sg-green-600 dark:text-sg-green-400',
  failed: 'bg-sg-red-100 dark:bg-sg-red-900/20 text-sg-red-600 dark:text-sg-red-400',
};

export function ExecutionMonitor({ tasks = [], loading = false }: ExecutionMonitorProps) {
  const runningCount = tasks.filter((t) => t.status === 'running').length;
  const completedCount = tasks.filter((t) => t.status === 'completed').length;
  const failedCount = tasks.filter((t) => t.status === 'failed').length;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <p className="text-sg-neutral-600 dark:text-sg-neutral-400">Loading execution monitor...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Stats */}
      <div className="grid grid-cols-4 gap-2">
        <div className="p-3 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded-lg">
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Running</p>
          <p className="text-lg font-bold text-sg-amber-600">{runningCount}</p>
        </div>
        <div className="p-3 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded-lg">
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Queued</p>
          <p className="text-lg font-bold text-sg-neutral-600">
            {tasks.filter((t) => t.status === 'queued').length}
          </p>
        </div>
        <div className="p-3 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded-lg">
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Completed</p>
          <p className="text-lg font-bold text-sg-green-600">{completedCount}</p>
        </div>
        <div className="p-3 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded-lg">
          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">Failed</p>
          <p className="text-lg font-bold text-sg-red-600">{failedCount}</p>
        </div>
      </div>

      {/* Task List */}
      {tasks.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-96 text-center space-y-3">
          <Zap className="w-12 h-12 text-sg-neutral-400" />
          <div>
            <p className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
              No tasks executing
            </p>
            <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">
              Create work items to begin execution
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          {tasks.map((task) => {
            const IconComponent = statusIcons[task.status];
            const colorClass = statusColors[task.status];

            return (
              <div
                key={task.id}
                className="p-4 border border-sg-neutral-200 dark:border-sg-neutral-800 rounded-lg"
              >
                <div className="flex items-start gap-3">
                  <div className={`flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center ${colorClass}`}>
                    <IconComponent className="w-5 h-5" />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h4 className="font-medium text-sg-neutral-900 dark:text-sg-neutral-50">
                          {task.title}
                        </h4>
                        {task.agent && (
                          <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                            {task.agent}
                          </p>
                        )}
                      </div>
                      <span className={`text-xs px-2 py-1 rounded font-medium ${colorClass}`}>
                        {task.status}
                      </span>
                    </div>

                    {/* Progress Bar */}
                    {task.status !== 'queued' && (
                      <div className="mt-2">
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400">
                            Progress
                          </span>
                          <span className="text-xs font-semibold text-sg-neutral-900 dark:text-sg-neutral-50">
                            {task.progress}%
                          </span>
                        </div>
                        <div className="w-full bg-sg-neutral-200 dark:bg-sg-neutral-700 rounded-full h-2 overflow-hidden">
                          <div
                            className={`h-full transition-all ${
                              task.status === 'completed'
                                ? 'bg-sg-green-500'
                                : task.status === 'failed'
                                  ? 'bg-sg-red-500'
                                  : 'bg-sg-amber-500'
                            }`}
                            style={{ width: `${task.progress}%` }}
                          />
                        </div>
                      </div>
                    )}

                    {task.message && (
                      <p className="text-xs text-sg-neutral-600 dark:text-sg-neutral-400 mt-2">
                        {task.message}
                      </p>
                    )}

                    {task.estimatedDuration && task.status === 'running' && (
                      <p className="text-xs text-sg-neutral-500 mt-1">
                        ETA: {Math.ceil(task.estimatedDuration * (1 - task.progress / 100))}s
                      </p>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

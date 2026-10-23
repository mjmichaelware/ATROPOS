/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';
import {
  Zap,
  Play,
  Pause,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Plus,
  RefreshCw,
  GitBranch,
  ShieldCheck,
  Terminal,
  Settings,
  Trash2,
  X,
  ChevronRight,
  Layers
} from 'lucide-react';

export interface WorkflowStage {
  id: string;
  name: string;
  action: string;
  status: 'idle' | 'running' | 'success' | 'failed';
  durationMs?: number;
}

export interface ScheduledWorkflow {
  id: string;
  name: string;
  description: string;
  triggerType: 'cron' | 'webhook' | 'event';
  cronExpression?: string;
  status: 'active' | 'paused' | 'running';
  lastRunAt?: string;
  lastRunStatus?: 'success' | 'failed';
  stages: WorkflowStage[];
}

const INITIAL_WORKFLOWS: ScheduledWorkflow[] = [
  {
    id: 'wf-ci-sentinel',
    name: 'Continuous AST & Merkle Sentinel',
    description: 'Autonomous pass verifying AST integrity and signing Merkle checkpoint roots every 30 minutes.',
    triggerType: 'cron',
    cronExpression: '*/30 * * * *',
    status: 'active',
    lastRunAt: '12 minutes ago',
    lastRunStatus: 'success',
    stages: [
      { id: 's1', name: 'AST Syntax Validation', action: 'Verify Kotlin and TS syntax integrity', status: 'success', durationMs: 420 },
      { id: 's2', name: 'Contract Assertions', action: 'Run backend-atom & test selector contracts', status: 'success', durationMs: 1250 },
      { id: 's3', name: 'Merkle Checkpoint Seal', action: 'Compute SHA-256 state tree audit root', status: 'success', durationMs: 180 },
    ],
  },
  {
    id: 'wf-specgraph-sync',
    name: 'SpecGraph Foundry Cloud Sync',
    description: 'Bidirectional state synchronization between Atropos local engine and Vercel cloud foundry.',
    triggerType: 'cron',
    cronExpression: '0 * * * *',
    status: 'active',
    lastRunAt: '45 minutes ago',
    lastRunStatus: 'success',
    stages: [
      { id: 's1', name: 'Lattice Export', action: 'Compile DAG nodes to SpecGraph JSON schema', status: 'success', durationMs: 650 },
      { id: 's2', name: 'Vercel Deployment Webhook', action: 'Dispatch signed event to foundry endpoint', status: 'success', durationMs: 890 },
    ],
  },
  {
    id: 'wf-drift-repair',
    name: 'Self-Healing Autonomous Reconciler',
    description: 'Listens for test regressions or compiler drift and initiates autonomous repair swarms.',
    triggerType: 'event',
    status: 'paused',
    lastRunAt: '3 hours ago',
    lastRunStatus: 'success',
    stages: [
      { id: 's1', name: 'Failure Ingestion', action: 'Parse build failure stack trace', status: 'idle' },
      { id: 's2', name: 'Autonomous Patch Generator', action: 'Deploy AST self-healing agent lane', status: 'idle' },
      { id: 's3', name: 'Gated Approval Verification', action: 'Await operator cryptographic sign-off', status: 'idle' },
    ],
  },
];

export function AutonomousWorkflowComposer() {
  const [workflows, setWorkflows] = useState<ScheduledWorkflow[]>(INITIAL_WORKFLOWS);
  const [runningWorkflowId, setRunningWorkflowId] = useState<string | null>(null);
  const [executionLog, setExecutionLog] = useState<string[]>([]);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  // Form State
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newCron, setNewCron] = useState('0 */2 * * *');
  const [newTrigger, setNewTrigger] = useState<'cron' | 'event'>('cron');

  const handleTriggerNow = (workflowId: string) => {
    if (runningWorkflowId) return;
    setRunningWorkflowId(workflowId);
    setExecutionLog([
      `[${new Date().toLocaleTimeString()}] Initiating manual workflow run for ${workflowId}...`,
      `[${new Date().toLocaleTimeString()}] Locking workspace Merkle tree and allocating worker lanes...`,
    ]);

    // Reset stages to running
    setWorkflows((prev) =>
      prev.map((wf) => {
        if (wf.id === workflowId) {
          return {
            ...wf,
            status: 'running',
            stages: wf.stages.map((st, idx) => ({
              ...st,
              status: idx === 0 ? 'running' : 'idle',
            })),
          };
        }
        return wf;
      })
    );

    // Simulate multi-stage progression
    setTimeout(() => {
      setExecutionLog((prev) => [
        ...prev,
        `[${new Date().toLocaleTimeString()}] Stage 1 complete: 0 syntax errors detected.`,
        `[${new Date().toLocaleTimeString()}] Executing Stage 2 contracts...`,
      ]);
      setWorkflows((prev) =>
        prev.map((wf) => {
          if (wf.id === workflowId) {
            return {
              ...wf,
              stages: wf.stages.map((st, idx) => ({
                ...st,
                status: idx === 0 ? 'success' : idx === 1 ? 'running' : 'idle',
              })),
            };
          }
          return wf;
        })
      );
    }, 1200);

    setTimeout(() => {
      setExecutionLog((prev) => [
        ...prev,
        `[${new Date().toLocaleTimeString()}] Stage 2 complete: All contracts passed.`,
        `[${new Date().toLocaleTimeString()}] Sealing Merkle cryptographic checkpoint root...`,
      ]);
      setWorkflows((prev) =>
        prev.map((wf) => {
          if (wf.id === workflowId) {
            return {
              ...wf,
              stages: wf.stages.map((st, idx) => ({
                ...st,
                status: idx <= 1 ? 'success' : idx === 2 ? 'running' : 'idle',
              })),
            };
          }
          return wf;
        })
      );
    }, 2400);

    setTimeout(() => {
      setExecutionLog((prev) => [
        ...prev,
        `[${new Date().toLocaleTimeString()}] Workflow execution succeeded. Checkpoint sealed.`,
      ]);
      setWorkflows((prev) =>
        prev.map((wf) => {
          if (wf.id === workflowId) {
            return {
              ...wf,
              status: 'active',
              lastRunAt: 'Just now',
              lastRunStatus: 'success',
              stages: wf.stages.map((st) => ({
                ...st,
                status: 'success',
              })),
            };
          }
          return wf;
        })
      );
      setRunningWorkflowId(null);
    }, 3600);
  };

  const handleTogglePause = (workflowId: string) => {
    setWorkflows((prev) =>
      prev.map((wf) => {
        if (wf.id === workflowId) {
          const nextStatus = wf.status === 'active' ? 'paused' : 'active';
          return { ...wf, status: nextStatus };
        }
        return wf;
      })
    );
  };

  const handleDeleteWorkflow = (workflowId: string) => {
    setWorkflows((prev) => prev.filter((wf) => wf.id !== workflowId));
  };

  const handleCreateWorkflow = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;

    const created: ScheduledWorkflow = {
      id: `wf-${Date.now()}`,
      name: newName.trim(),
      description: newDesc.trim() || 'Custom operator-defined automated pipeline',
      triggerType: newTrigger,
      cronExpression: newTrigger === 'cron' ? newCron.trim() : undefined,
      status: 'active',
      lastRunAt: 'Never',
      stages: [
        { id: 's1', name: 'Pipeline Initialization', action: 'Spawn autonomous execution container', status: 'idle' },
        { id: 's2', name: 'Contract Verification', action: 'Validate invariant integrity', status: 'idle' },
        { id: 's3', name: 'Checkpoint Attestation', action: 'Sign audit log hash', status: 'idle' },
      ],
    };

    setWorkflows((prev) => [created, ...prev]);
    setNewName('');
    setNewDesc('');
    setIsCreateModalOpen(false);
  };

  return (
    <div className="space-y-8 font-sans">
      {/* Top Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Zap className="w-5 h-5 text-amber-400" />
            Autonomous Workflows & Event Pipelines
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Deterministic schedules, Merkle checkpoint automations, and self-healing repair swarms.
          </p>
        </div>
        <button
          onClick={() => setIsCreateModalOpen(true)}
          className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white rounded-xl text-xs font-semibold shadow-lg transition-all active:scale-95 self-start md:self-auto"
        >
          <Plus className="w-4 h-4" />
          Schedule New Workflow
        </button>
      </div>

      {/* Workflow Cards */}
      <div className="grid grid-cols-1 gap-5">
        {workflows.map((wf) => (
          <div
            key={wf.id}
            className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4 hover:border-slate-700 transition-colors"
          >
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div className="flex items-start gap-3">
                <div
                  className={`p-2.5 rounded-xl border ${
                    wf.status === 'running'
                      ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-400 animate-pulse'
                      : wf.status === 'active'
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                      : 'bg-slate-800 border-slate-700 text-slate-400'
                  }`}
                >
                  <Zap className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2.5">
                    <h3 className="text-sm font-bold text-white">{wf.name}</h3>
                    <span
                      className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                        wf.status === 'running'
                          ? 'bg-cyan-500/20 text-cyan-400'
                          : wf.status === 'active'
                          ? 'bg-emerald-500/20 text-emerald-400'
                          : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      {wf.status}
                    </span>
                    {wf.cronExpression && (
                      <span className="font-mono text-[10px] text-slate-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
                        {wf.cronExpression}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400 mt-1">{wf.description}</p>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-2 self-end md:self-auto">
                <button
                  onClick={() => handleTriggerNow(wf.id)}
                  disabled={runningWorkflowId !== null}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-600/20 hover:bg-cyan-600/30 text-cyan-400 text-xs font-semibold transition-colors disabled:opacity-50"
                >
                  <Play className="w-3.5 h-3.5" />
                  Trigger Pass
                </button>
                <button
                  onClick={() => handleTogglePause(wf.id)}
                  className="p-1.5 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                  title={wf.status === 'active' ? 'Pause Workflow' : 'Resume Workflow'}
                >
                  {wf.status === 'active' ? (
                    <Pause className="w-3.5 h-3.5" />
                  ) : (
                    <Play className="w-3.5 h-3.5" />
                  )}
                </button>
                <button
                  onClick={() => handleDeleteWorkflow(wf.id)}
                  className="p-1.5 rounded-xl border border-slate-800 bg-slate-900 hover:bg-red-500/20 text-slate-400 hover:text-red-400 transition-colors"
                  title="Delete Workflow"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Pipeline Stage Visualizer */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-3 border-t border-slate-800/80">
              {wf.stages.map((stage, idx) => (
                <div
                  key={stage.id}
                  className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-1.5"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-bold text-slate-300 flex items-center gap-1.5">
                      <span className="w-4 h-4 rounded-full bg-slate-800 text-[9px] flex items-center justify-center font-mono">
                        {idx + 1}
                      </span>
                      {stage.name}
                    </span>
                    {stage.status === 'success' && (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                    )}
                    {stage.status === 'running' && (
                      <RefreshCw className="w-3.5 h-3.5 text-cyan-400 animate-spin" />
                    )}
                    {stage.status === 'idle' && (
                      <Clock className="w-3.5 h-3.5 text-slate-500" />
                    )}
                  </div>
                  <p className="text-[10px] text-slate-400 truncate">{stage.action}</p>
                  {stage.durationMs && (
                    <span className="text-[9px] font-mono text-slate-500 block">
                      {stage.durationMs}ms
                    </span>
                  )}
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
              <span>Last Execution: <strong className="text-slate-400">{wf.lastRunAt}</strong></span>
              <span>Outcome: <strong className="text-emerald-400">Deterministic Pass</strong></span>
            </div>
          </div>
        ))}
      </div>

      {/* Execution Terminal Output */}
      {executionLog.length > 0 && (
        <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 font-mono text-xs shadow-2xl">
          <div className="flex items-center gap-2 text-slate-400 pb-2 mb-2 border-b border-slate-800 font-sans">
            <Terminal className="w-4 h-4 text-cyan-400" />
            <span className="font-semibold text-white">Live Workflow Runner Log</span>
          </div>
          <div className="space-y-1 text-slate-300 max-h-40 overflow-y-auto">
            {executionLog.map((line, idx) => (
              <div key={idx} className="leading-relaxed">
                <span className="text-cyan-400">&gt;</span> {line}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Schedule Workflow Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Plus className="w-5 h-5 text-cyan-400" />
                Schedule Autonomous Workflow
              </h3>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateWorkflow} className="space-y-4 text-xs">
              <div>
                <label className="block text-slate-300 font-semibold mb-1">
                  Workflow Name *
                </label>
                <input
                  type="text"
                  required
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. Nightly AST Verification & Packaging"
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="block text-slate-300 font-semibold mb-1">
                  Description
                </label>
                <textarea
                  rows={2}
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="Operational purpose and failure recovery strategy..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="block text-slate-300 font-semibold mb-1">
                  Trigger Mechanism
                </label>
                <select
                  value={newTrigger}
                  onChange={(e) => setNewTrigger(e.target.value as 'cron' | 'event')}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-cyan-500"
                >
                  <option value="cron">Periodic Cron Schedule</option>
                  <option value="event">Event-Driven (Git Push / Audit Drift)</option>
                </select>
              </div>

              {newTrigger === 'cron' && (
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">
                    Cron Expression (UTC)
                  </label>
                  <input
                    type="text"
                    value={newCron}
                    onChange={(e) => setNewCron(e.target.value)}
                    placeholder="*/15 * * * *"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono focus:outline-none focus:border-cyan-500"
                  />
                </div>
              )}

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-slate-700 text-slate-300 hover:bg-slate-800 font-medium transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold shadow-md transition-colors"
                >
                  Create Schedule
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

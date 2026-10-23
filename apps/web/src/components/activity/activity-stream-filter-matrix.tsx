/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useMemo, useEffect, FC } from 'react';
import {
  Activity,
  Filter,
  Search,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Clock,
  Download,
  Shield,
  Layers,
  ArrowRight,
  Terminal,
  Zap,
  RotateCcw,
  Sliders
} from 'lucide-react';

export type ActivityEventKind =
  | 'plan_phase'
  | 'neural_turn'
  | 'tool_call'
  | 'diff_mutation'
  | 'contract_check'
  | 'quarantine_event'
  | 'release_tag';

export type ActivityEventStatus = 'SUCCESS' | 'PENDING' | 'QUARANTINED' | 'FAILED';

export interface StreamActivityItem {
  id: string;
  kind: ActivityEventKind;
  status: ActivityEventStatus;
  title: string;
  source: string;
  target?: string;
  latencyMs?: number;
  tokensUsed?: number;
  timestamp: string;
  digest: string;
  metadata?: Record<string, string | number>;
}

const INITIAL_EVENTS: StreamActivityItem[] = [
  {
    id: 'act-001',
    kind: 'diff_mutation',
    status: 'SUCCESS',
    title: 'Atomic AST Patch Committed',
    source: 'autonomous-orchestrator',
    target: 'src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt',
    latencyMs: 142,
    timestamp: '2026-10-22T21:04:41.000Z',
    digest: '5ff6ff29a1b0c98f',
    metadata: { insertions: 2635, deletions: 1 }
  },
  {
    id: 'act-002',
    kind: 'contract_check',
    status: 'SUCCESS',
    title: 'Backend Atom Contract Verified (OK)',
    source: 'scripts/backend-atom-contract-test.sh',
    latencyMs: 980,
    timestamp: '2026-10-22T21:04:01.000Z',
    digest: 'b5a2c9b97b0a8523',
    metadata: { testCases: 486, exitCode: 0 }
  },
  {
    id: 'act-003',
    kind: 'tool_call',
    status: 'SUCCESS',
    title: 'MCP Host Tool Call: git_log_history',
    source: 'atropos-mcp-engine',
    target: 'GitDaemon',
    latencyMs: 45,
    timestamp: '2026-10-22T21:03:40.000Z',
    digest: '7d5a918a93e8e24c',
    metadata: { params: 'limit=15' }
  },
  {
    id: 'act-004',
    kind: 'neural_turn',
    status: 'SUCCESS',
    title: 'Syntactic Hex Analyzer Reasoning Completed',
    source: 'neural-cascade-gateway',
    latencyMs: 1840,
    tokensUsed: 3420,
    timestamp: '2026-10-22T21:02:18.000Z',
    digest: 'ef2d127de37b942b',
    metadata: { promptTokens: 2150, completionTokens: 1270 }
  },
  {
    id: 'act-005',
    kind: 'quarantine_event',
    status: 'QUARANTINED',
    title: 'AST Out-of-Bounds Mutation Intercepted',
    source: 'sovereign-policy-sentinel',
    target: '/etc/resolv.conf',
    latencyMs: 12,
    timestamp: '2026-10-22T21:00:15.000Z',
    digest: '4b227777d4dd1fc6',
    metadata: { ruleId: 'rule-fs-block-etc', reason: 'System partition write denied' }
  },
  {
    id: 'act-006',
    kind: 'plan_phase',
    status: 'SUCCESS',
    title: 'SpecGraph Phase 4 Convergence Ratified',
    source: 'autonomous-planner',
    target: 'SpecGraph Foundry',
    latencyMs: 310,
    timestamp: '2026-10-22T20:56:53.000Z',
    digest: 'e3b0c44298fc1c14',
    metadata: { phase: 4, dagNodes: 48 }
  }
];

export const ActivityStreamFilterMatrix: FC = () => {
  const [events, setEvents] = useState<StreamActivityItem[]>(INITIAL_EVENTS);
  const [searchFilter, setSearchFilter] = useState('');
  const [selectedKind, setSelectedKind] = useState<string>('all');
  const [selectedStatus, setSelectedStatus] = useState<string>('all');
  const [merkleLogRoot, setMerkleLogRoot] = useState<string>('');
  const [isLiveStream, setIsLiveStream] = useState<boolean>(true);

  // Filtered Events
  const filteredEvents = useMemo(() => {
    return events.filter((ev) => {
      const matchesSearch =
        searchFilter.trim() === '' ||
        ev.id.toLowerCase().includes(searchFilter.toLowerCase()) ||
        ev.title.toLowerCase().includes(searchFilter.toLowerCase()) ||
        ev.source.toLowerCase().includes(searchFilter.toLowerCase()) ||
        (ev.target && ev.target.toLowerCase().includes(searchFilter.toLowerCase())) ||
        ev.digest.toLowerCase().includes(searchFilter.toLowerCase());

      const matchesKind = selectedKind === 'all' || ev.kind === selectedKind;
      const matchesStatus = selectedStatus === 'all' || ev.status === selectedStatus;

      return matchesSearch && matchesKind && matchesStatus;
    });
  }, [events, searchFilter, selectedKind, selectedStatus]);

  // Statistics
  const totalTokens = useMemo(
    () => filteredEvents.reduce((acc, ev) => acc + (ev.tokensUsed || 0), 0),
    [filteredEvents]
  );

  const avgLatency = useMemo(() => {
    const latencies = filteredEvents.map((ev) => ev.latencyMs || 0).filter((l) => l > 0);
    if (latencies.length === 0) return 0;
    return Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length);
  }, [filteredEvents]);

  // Compute Merkle Root for filtered events
  useEffect(() => {
    let cancelled = false;
    const compute = async () => {
      const canonical = filteredEvents.map((e) => `${e.id}:${e.digest}:${e.status}`).join('|');
      try {
        const enc = new TextEncoder();
        const buf = await crypto.subtle.digest('SHA-256', enc.encode(canonical));
        const arr = Array.from(new Uint8Array(buf));
        const hex = arr.map((b) => b.toString(16).padStart(2, '0')).join('');
        if (!cancelled) setMerkleLogRoot(`0x${hex}`);
      } catch {
        if (!cancelled) setMerkleLogRoot('0x-offline');
      }
    };
    void compute();
    return () => {
      cancelled = true;
    };
  }, [filteredEvents]);

  // Export Merkle Proof Log
  const handleExportMerkleLog = () => {
    const bundle = {
      specVersion: '1.4.0',
      exportedAt: new Date().toISOString(),
      merkleLogRoot,
      totalCount: filteredEvents.length,
      events: filteredEvents
    };
    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `activity-merkle-log-${merkleLogRoot.slice(2, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6 rounded-xl border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white/70 dark:bg-sg-neutral-900/60 p-6 backdrop-blur-md shadow-sm">
      {/* Header & Telemetry Summary */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-indigo-500" />
            <h2 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
              Activity Stream Filter Matrix & Merkle Audit
            </h2>
          </div>
          <p className="mt-1 text-xs text-sg-neutral-500 dark:text-sg-neutral-400">
            Real-time telemetry event stream covering AST diffs, contract passes, neural inferences, and security quarantine events.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950 px-3 py-1.5 font-mono text-xs">
            <Shield className="h-3.5 w-3.5 text-indigo-400" />
            <span className="text-sg-neutral-500">Log Root:</span>
            <span className="text-indigo-600 dark:text-indigo-400 font-semibold" title={merkleLogRoot}>
              {merkleLogRoot ? `${merkleLogRoot.slice(0, 10)}...${merkleLogRoot.slice(-6)}` : 'Computing...'}
            </span>
          </div>

          <button
            type="button"
            onClick={handleExportMerkleLog}
            className="flex items-center gap-1.5 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-sg-neutral-100 dark:bg-sg-neutral-800 px-3 py-1.5 text-xs font-medium text-sg-neutral-800 dark:text-sg-neutral-200 hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700 transition"
          >
            <Download className="h-3.5 w-3.5" />
            Export Merkle Log
          </button>
        </div>
      </div>

      {/* Statistical Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-3">
          <div className="text-[11px] font-medium text-sg-neutral-500">Visible Events</div>
          <div className="mt-1 text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-100 font-mono">
            {filteredEvents.length} / {events.length}
          </div>
        </div>

        <div className="rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-3">
          <div className="text-[11px] font-medium text-sg-neutral-500">Avg Latency</div>
          <div className="mt-1 text-lg font-semibold text-emerald-600 dark:text-emerald-400 font-mono">
            {avgLatency} ms
          </div>
        </div>

        <div className="rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-3">
          <div className="text-[11px] font-medium text-sg-neutral-500">Tokens Consumed</div>
          <div className="mt-1 text-lg font-semibold text-indigo-600 dark:text-indigo-400 font-mono">
            {totalTokens.toLocaleString()}
          </div>
        </div>

        <div className="rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-3">
          <div className="text-[11px] font-medium text-sg-neutral-500">Stream Status</div>
          <div className="mt-1 flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">
              SYNCHRONIZED
            </span>
          </div>
        </div>
      </div>

      {/* Filter and Query Controls */}
      <div className="flex flex-col sm:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <Search className="h-4 w-4 absolute left-3 top-2.5 text-sg-neutral-400" />
          <input
            type="text"
            placeholder="Search activity by title, target path, ID, or SHA digest..."
            value={searchFilter}
            onChange={(e) => setSearchFilter(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
          />
        </div>

        {/* Kind Filter */}
        <select
          value={selectedKind}
          onChange={(e) => setSelectedKind(e.target.value)}
          className="w-full sm:w-44 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-medium text-sg-neutral-800 dark:text-sg-neutral-200"
        >
          <option value="all">All Categories</option>
          <option value="diff_mutation">diff_mutation</option>
          <option value="contract_check">contract_check</option>
          <option value="neural_turn">neural_turn</option>
          <option value="tool_call">tool_call</option>
          <option value="quarantine_event">quarantine_event</option>
          <option value="plan_phase">plan_phase</option>
        </select>

        {/* Status Filter */}
        <select
          value={selectedStatus}
          onChange={(e) => setSelectedStatus(e.target.value)}
          className="w-full sm:w-36 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-semibold text-sg-neutral-800 dark:text-sg-neutral-200"
        >
          <option value="all">All Statuses</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="QUARANTINED">QUARANTINED</option>
          <option value="PENDING">PENDING</option>
          <option value="FAILED">FAILED</option>
        </select>
      </div>

      {/* Activity Timeline List */}
      <div className="space-y-2.5 max-h-[500px] overflow-y-auto pr-1">
        {filteredEvents.map((ev) => (
          <div
            key={ev.id}
            className="p-3.5 rounded-xl border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900/40 hover:border-sg-neutral-300 dark:hover:border-sg-neutral-700 transition text-xs space-y-2"
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                {ev.status === 'SUCCESS' && (
                  <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                )}
                {ev.status === 'QUARANTINED' && (
                  <AlertTriangle className="h-4 w-4 text-amber-500 shrink-0" />
                )}
                {ev.status === 'FAILED' && <XCircle className="h-4 w-4 text-rose-500 shrink-0" />}
                {ev.status === 'PENDING' && <Clock className="h-4 w-4 text-sky-500 shrink-0" />}

                <span className="font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
                  {ev.title}
                </span>

                <span className="rounded bg-sg-neutral-100 dark:bg-sg-neutral-800 px-1.5 py-0.5 text-[10px] font-mono uppercase text-sg-neutral-600 dark:text-sg-neutral-400">
                  {ev.kind.replace('_', ' ')}
                </span>
              </div>

              <div className="flex items-center gap-2 font-mono text-[11px] text-sg-neutral-400">
                {ev.latencyMs && <span>{ev.latencyMs}ms</span>}
                {ev.tokensUsed && <span>· {ev.tokensUsed} tokens</span>}
                <span>· {new Date(ev.timestamp).toLocaleTimeString()}</span>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px] font-mono text-sg-neutral-500 dark:text-sg-neutral-400">
              <div className="flex items-center gap-1.5 truncate">
                <span className="text-sg-neutral-400">Src:</span>
                <span className="text-indigo-600 dark:text-indigo-400">{ev.source}</span>
                {ev.target && (
                  <>
                    <ArrowRight className="h-3 w-3 text-sg-neutral-400" />
                    <span className="text-sg-neutral-700 dark:text-sg-neutral-300 truncate">
                      {ev.target}
                    </span>
                  </>
                )}
              </div>

              <div className="flex items-center gap-1 text-[10px] text-sg-neutral-400 shrink-0">
                <span>DIGEST:</span>
                <code className="bg-sg-neutral-100 dark:bg-sg-neutral-800 px-1 rounded">
                  {ev.digest}
                </code>
              </div>
            </div>

            {ev.metadata && Object.keys(ev.metadata).length > 0 && (
              <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-sg-neutral-100 dark:border-sg-neutral-800/60 text-[10px] font-mono text-sg-neutral-500">
                {Object.entries(ev.metadata).map(([k, v]) => (
                  <span
                    key={k}
                    className="bg-sg-neutral-50 dark:bg-sg-neutral-950 px-1.5 py-0.5 rounded border border-sg-neutral-200 dark:border-sg-neutral-800"
                  >
                    {k}: <strong className="text-sg-neutral-700 dark:text-sg-neutral-300">{v}</strong>
                  </span>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

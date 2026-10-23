/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';
import {
  Clock,
  Search,
  Filter,
  CheckCircle2,
  Shield,
  Layers,
  Terminal,
  FileCode,
  Sparkles,
  ChevronRight,
  ExternalLink,
  Copy,
  Check
} from 'lucide-react';

export interface AuditEventItem {
  id: string;
  seq: number;
  timestamp: string;
  type: 'AST_MUTATION' | 'MERKLE_SEAL' | 'TOOL_EXECUTION' | 'VERIFICATION_GATE' | 'AGENT_SPAWN';
  actor: string;
  action: string;
  merkleRoot: string;
  evidenceHash: string;
  status: 'passed' | 'reverted' | 'gated';
  payload: Record<string, unknown>;
}

const SAMPLE_HISTORY_EVENTS: AuditEventItem[] = [
  {
    id: 'evt-001',
    seq: 104,
    timestamp: '2026-10-23T01:30:15Z',
    type: 'VERIFICATION_GATE',
    actor: 'Verification Sentry',
    action: 'Verified 486 hosted test selectors with zero regressions',
    merkleRoot: '7e2b109c84e1a0b3f81e1948ba26d4c5',
    evidenceHash: 'b4a1c890123efd456789abcde0123456',
    status: 'passed',
    payload: {
      suite: 'ATROPOS_HOSTED_TEST_SELECTOR_CONTRACT_OK',
      testsPassed: 486,
      durationMs: 1420
    }
  },
  {
    id: 'evt-002',
    seq: 103,
    timestamp: '2026-10-23T01:28:40Z',
    type: 'AST_MUTATION',
    actor: 'AST Self-Healing Specialist',
    action: 'Patched FileSearchModal icon reference from FileCode to InsertDriveFile',
    merkleRoot: '6d1a089b73d09f2e700d0837a915c3b4',
    evidenceHash: 'a390b78f012dec3456789abcdef01234',
    status: 'passed',
    payload: {
      file: 'app/src/main/java/com/atropos/android/app/ide/FileSearchModal.kt',
      linesChanged: 2,
      diffSummary: '+Icons.Default.InsertDriveFile, -Icons.Default.FileCode'
    }
  },
  {
    id: 'evt-003',
    seq: 102,
    timestamp: '2026-10-23T01:25:12Z',
    type: 'MERKLE_SEAL',
    actor: 'Sovereign Supervisor',
    action: 'Certified Clean Competitor Name Scrub across all Web and Android modules',
    merkleRoot: '5c09f78a62c98e1d6ffc97269804b2a3',
    evidenceHash: '9280a67e901cdb23456789abcdef0123',
    status: 'passed',
    payload: {
      rule: 'ZERO_COMPETITOR_NAMES',
      inspectedFiles: 38,
      violationsFound: 0
    }
  },
  {
    id: 'evt-004',
    seq: 101,
    timestamp: '2026-10-23T01:20:05Z',
    type: 'TOOL_EXECUTION',
    actor: 'Sovereign Workstation Bridge',
    action: 'Executed bash scripts/backend-atom-contract-test.sh',
    merkleRoot: '4b98e67951b87d0c5eeb86158793a192',
    evidenceHash: '8170956d890bca123456789abcdef012',
    status: 'passed',
    payload: {
      command: 'scripts/backend-atom-contract-test.sh',
      exitCode: 0,
      stdout: 'ATROPOS_BACKEND_ATOM_CONTRACT_OK'
    }
  }
];

export function SovereignHistoryTimeline() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFilter, setSelectedFilter] = useState<string>('ALL');
  const [activeEvent, setActiveEvent] = useState<AuditEventItem | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const filteredEvents = SAMPLE_HISTORY_EVENTS.filter((evt) => {
    const matchesFilter = selectedFilter === 'ALL' || evt.type === selectedFilter;
    const matchesSearch =
      searchQuery === '' ||
      evt.action.toLowerCase().includes(searchQuery.toLowerCase()) ||
      evt.actor.toLowerCase().includes(searchQuery.toLowerCase()) ||
      evt.merkleRoot.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  const handleCopy = (hash: string) => {
    navigator.clipboard.writeText(hash);
    setCopiedHash(hash);
    setTimeout(() => setCopiedHash(null), 2000);
  };

  const getEventBadge = (type: AuditEventItem['type']) => {
    switch (type) {
      case 'VERIFICATION_GATE':
        return { icon: CheckCircle2, color: 'text-emerald-400 bg-emerald-950/60 border-emerald-800' };
      case 'AST_MUTATION':
        return { icon: FileCode, color: 'text-cyan-400 bg-cyan-950/60 border-cyan-800' };
      case 'MERKLE_SEAL':
        return { icon: Shield, color: 'text-purple-400 bg-purple-950/60 border-purple-800' };
      case 'TOOL_EXECUTION':
        return { icon: Terminal, color: 'text-amber-400 bg-amber-950/60 border-amber-800' };
      case 'AGENT_SPAWN':
        return { icon: Sparkles, color: 'text-indigo-400 bg-indigo-950/60 border-indigo-800' };
    }
  };

  return (
    <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden shadow-2xl text-slate-100 font-sans">
      {/* Search and Filters Bar */}
      <div className="p-4 bg-slate-900 border-b border-slate-800 space-y-3">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search cryptographic ledger by action, actor, or Merkle root..."
              className="w-full bg-slate-950 border border-slate-700 rounded-lg pl-9 pr-3 py-2 text-xs text-slate-100 font-mono focus:outline-none focus:border-cyan-500"
            />
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto text-xs font-mono">
            {['ALL', 'VERIFICATION_GATE', 'AST_MUTATION', 'MERKLE_SEAL', 'TOOL_EXECUTION'].map((filter) => (
              <button
                key={filter}
                onClick={() => setSelectedFilter(filter)}
                className={`px-3 py-1.5 rounded-lg border transition-colors whitespace-nowrap ${
                  selectedFilter === filter
                    ? 'bg-cyan-600 text-white border-cyan-500 font-semibold'
                    : 'bg-slate-800/80 text-slate-400 border-slate-700 hover:bg-slate-800'
                }`}
              >
                {filter.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Audit Event Timeline */}
      <div className="divide-y divide-slate-800/80">
        {filteredEvents.map((evt) => {
          const badge = getEventBadge(evt.type);
          const Icon = badge.icon;

          return (
            <div
              key={evt.id}
              onClick={() => setActiveEvent(evt)}
              className="p-4 hover:bg-slate-900/60 transition-colors cursor-pointer flex items-start justify-between gap-4"
            >
              <div className="flex items-start gap-3.5">
                <div className={`p-2 rounded-lg border ${badge.color} mt-0.5`}>
                  <Icon className="w-4 h-4" />
                </div>

                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold text-slate-200">
                      #{evt.seq}
                    </span>
                    <span className="text-xs font-semibold text-slate-100">{evt.action}</span>
                  </div>

                  <div className="flex items-center gap-3 text-[11px] font-mono text-slate-500">
                    <span className="text-cyan-400 font-medium">{evt.actor}</span>
                    <span>•</span>
                    <span>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                    <span>•</span>
                    <span className="truncate max-w-[140px] text-slate-400">
                      root: {evt.merkleRoot.slice(0, 8)}...
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 flex-shrink-0">
                <span className="px-2 py-0.5 rounded text-[10px] font-mono uppercase bg-emerald-950/80 text-emerald-400 border border-emerald-800">
                  {evt.status}
                </span>
                <ChevronRight className="w-4 h-4 text-slate-600" />
              </div>
            </div>
          );
        })}
      </div>

      {/* Event Details Modal */}
      {activeEvent && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-xl p-6 w-full max-w-xl shadow-2xl space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-mono text-cyan-400">Sequence #{activeEvent.seq}</span>
                <h3 className="text-base font-bold text-slate-100 mt-0.5">{activeEvent.action}</h3>
              </div>
              <button
                onClick={() => setActiveEvent(null)}
                className="text-slate-400 hover:text-slate-200"
              >
                ✕
              </button>
            </div>

            <div className="space-y-2 text-xs font-mono">
              <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 space-y-1.5">
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Merkle Root:</span>
                  <div className="flex items-center gap-1.5 text-slate-200">
                    <span>{activeEvent.merkleRoot}</span>
                    <button
                      onClick={() => handleCopy(activeEvent.merkleRoot)}
                      className="p-1 text-slate-400 hover:text-slate-200"
                    >
                      {copiedHash === activeEvent.merkleRoot ? (
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                      ) : (
                        <Copy className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>

                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Evidence SHA-256:</span>
                  <span className="text-slate-200">{activeEvent.evidenceHash}</span>
                </div>
              </div>

              <div className="p-3 bg-slate-950 rounded-lg border border-slate-800">
                <span className="text-slate-500 block mb-1">Payload Envelope:</span>
                <pre className="text-emerald-400 text-[11px] overflow-x-auto">
                  {JSON.stringify(activeEvent.payload, null, 2)}
                </pre>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setActiveEvent(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-lg"
              >
                Close Inspector
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

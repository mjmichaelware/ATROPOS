'use client';

import React, { useState } from 'react';
import {
  Layers,
  Sliders,
  Play,
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Activity,
  Cpu,
  ShieldCheck,
  Terminal,
  Zap,
  Code2,
  Lock,
  Eye,
  RefreshCw
} from 'lucide-react';
import { EngineSixAnswers } from '@/components/answers/engine-six-answers';
import { QuotaChips } from '@/components/quota/quota-chips';
import { StatusBadge } from '@/components/ui/status-badge';
import { CompletionChip } from '@/components/ui/completion-chip';

export type SandboxComponentType =
  | 'six-answers-radar'
  | 'approval-gate'
  | 'quota-burn-meter'
  | 'command-launcher'
  | 'merkle-seal'
  | 'ast-diagnostic-slice';

export interface SandboxComponentConfig {
  id: SandboxComponentType;
  name: string;
  category: 'Telemetry' | 'Governance' | 'Execution' | 'AST';
  description: string;
}

export const SANDBOX_COMPONENTS: SandboxComponentConfig[] = [
  {
    id: 'six-answers-radar',
    name: 'Engine Six Answers Continuous Radar',
    category: 'Telemetry',
    description: 'Autonomous state projection presenting Objective, Doing, Why, Progress, Next, and Evidence in real time.'
  },
  {
    id: 'approval-gate',
    name: 'Human-in-the-Loop Streaming Approval Gate',
    category: 'Governance',
    description: 'Pre-mutation cryptographic approval card with territory bounds, actor attribution, and decision logs.'
  },
  {
    id: 'quota-burn-meter',
    name: 'Neural Token Burn & Headroom Gauge',
    category: 'Execution',
    description: 'High-frequency telemetry chip monitoring token burn rates, dollar costs, and context window headroom.'
  },
  {
    id: 'command-launcher',
    name: 'Hierarchical Slash (/) Command Taxonomy',
    category: 'Execution',
    description: 'Interactive command category launcher adhering strictly to the <=8 direct commands non-overload invariant.'
  },
  {
    id: 'merkle-seal',
    name: 'Sovereign Merkle Release Certifier',
    category: 'Governance',
    description: 'Cryptographic SHA-256 seal verifying invariant integrity across all workspace territories.'
  },
  {
    id: 'ast-diagnostic-slice',
    name: 'Autonomous AST Diagnostic Slice',
    category: 'AST',
    description: 'Real-time compiler syntax graph inspector highlighting semantic errors and healing recommendations.'
  }
];

export function ComponentSandbox() {
  const [selectedId, setSelectedId] = useState<SandboxComponentType>('six-answers-radar');
  const [healthStatus, setHealthStatus] = useState<'HEALTHY' | 'DEGRADED' | 'CRITICAL'>('HEALTHY');
  const [progressPercent, setProgressPercent] = useState<number>(68);
  const [activeStage, setActiveStage] = useState<string>('Compiling AST passes');
  const [tokenBurnRate, setTokenBurnRate] = useState<number>(420);
  const [approvalDecided, setApprovalDecided] = useState<boolean | null>(null);

  const activeConfig = SANDBOX_COMPONENTS.find((c) => c.id === selectedId)!;

  return (
    <div className="flex flex-col lg:flex-row h-full w-full bg-slate-950 text-slate-100 divide-y lg:divide-y-0 lg:divide-x divide-slate-800">
      {/* Left Column: Component Picker & Controls Matrix */}
      <div className="w-full lg:w-80 flex flex-col bg-slate-900/60 p-5 space-y-6 shrink-0 overflow-y-auto">
        <div className="flex items-center gap-2">
          <Layers className="h-5 w-5 text-cyan-400" />
          <h2 className="text-sm font-mono font-bold uppercase tracking-wider text-white">
            Component Sandbox
          </h2>
        </div>

        {/* Component Selector */}
        <div className="space-y-1.5">
          <label className="text-[11px] font-mono text-slate-400 font-semibold uppercase">
            Target Component
          </label>
          <div className="space-y-1">
            {SANDBOX_COMPONENTS.map((comp) => {
              const isSelected = comp.id === selectedId;
              return (
                <button
                  key={comp.id}
                  onClick={() => {
                    setSelectedId(comp.id);
                    setApprovalDecided(null);
                  }}
                  className={`w-full text-left px-3 py-2.5 rounded-lg border text-xs font-mono transition-all flex items-center justify-between ${
                    isSelected
                      ? 'bg-cyan-500/10 border-cyan-400 text-cyan-300 shadow-sm'
                      : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800'
                  }`}
                >
                  <span className="font-semibold truncate">{comp.name}</span>
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700 ml-1">
                    {comp.category}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Live State Mutators */}
        <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/80 space-y-4">
          <div className="flex items-center gap-2">
            <Sliders className="h-4 w-4 text-cyan-400" />
            <h3 className="text-xs font-mono font-bold uppercase text-white">State Mutators</h3>
          </div>

          {/* Health Status Selector */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-mono text-slate-400">Health Invariant</label>
            <div className="grid grid-cols-3 gap-1">
              {(['HEALTHY', 'DEGRADED', 'CRITICAL'] as const).map((h) => (
                <button
                  key={h}
                  onClick={() => setHealthStatus(h)}
                  className={`px-2 py-1 text-[10px] font-mono rounded border transition-colors ${
                    healthStatus === h
                      ? h === 'HEALTHY'
                        ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300 font-bold'
                        : h === 'DEGRADED'
                        ? 'bg-amber-500/20 border-amber-400 text-amber-300 font-bold'
                        : 'bg-rose-500/20 border-rose-400 text-rose-300 font-bold'
                      : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-white'
                  }`}
                >
                  {h}
                </button>
              ))}
            </div>
          </div>

          {/* Progress Slider */}
          <div className="space-y-1.5">
            <div className="flex justify-between text-[10px] font-mono text-slate-400">
              <span>Progress Percentage</span>
              <span className="text-cyan-400 font-bold">{progressPercent}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={progressPercent}
              onChange={(e) => setProgressPercent(Number(e.target.value))}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
            />
          </div>

          {/* Burn Rate Slider */}
          <div className="space-y-1.5">
            <div className="flex justify-between text-[10px] font-mono text-slate-400">
              <span>Token Burn Rate</span>
              <span className="text-amber-400 font-bold">{tokenBurnRate} tok/s</span>
            </div>
            <input
              type="range"
              min="50"
              max="2500"
              step="50"
              value={tokenBurnRate}
              onChange={(e) => setTokenBurnRate(Number(e.target.value))}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-amber-400"
            />
          </div>

          {/* Reset State Button */}
          <button
            onClick={() => {
              setProgressPercent(68);
              setHealthStatus('HEALTHY');
              setTokenBurnRate(420);
              setApprovalDecided(null);
            }}
            className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg border border-slate-700 bg-slate-800 text-[11px] font-mono text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
          >
            <RotateCcw className="h-3 w-3" />
            <span>Reset Default State</span>
          </button>
        </div>

        {/* Telemetry Contract Invariants */}
        <div className="p-3.5 rounded-xl border border-slate-800/80 bg-slate-900/40 text-[11px] font-mono text-slate-400 space-y-1">
          <p className="text-slate-200 font-semibold">Deterministic Isolation</p>
          <p className="text-[10px] leading-relaxed text-slate-400">
            Components execute inside strict visual boundaries without leaking unverified side effects or synthetic stubs.
          </p>
        </div>
      </div>

      {/* Right Column: Live Component Rendering Canvas */}
      <div className="flex-1 flex flex-col p-6 overflow-y-auto bg-slate-950">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div>
            <h1 className="text-lg font-bold font-mono text-white flex items-center gap-2">
              <span>{activeConfig.name}</span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-cyan-400">
                ACTIVE
              </span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">{activeConfig.description}</p>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono text-slate-500">Render mode: Isolated React Frame</span>
          </div>
        </div>

        {/* Canvas Display Card */}
        <div className="mt-6 p-6 rounded-2xl border border-slate-800 bg-slate-900/50 shadow-2xl relative">
          {selectedId === 'six-answers-radar' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <Activity className="h-4 w-4 text-cyan-400" />
                  <span className="font-mono text-xs font-bold text-white">LIVE TELEMETRY SNAPSHOT</span>
                </div>
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded font-bold border ${
                    healthStatus === 'HEALTHY'
                      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                      : healthStatus === 'DEGRADED'
                      ? 'border-amber-500/40 bg-amber-500/10 text-amber-400'
                      : 'border-rose-500/40 bg-rose-500/10 text-rose-400'
                  }`}
                >
                  SYSTEM {healthStatus}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                <AnswerTile title="1. OBJECTIVE" value="Complete SWE-Bench Pro Verification and restore SpecGraph Vercel production" />
                <AnswerTile title="2. DOING" value={`${activeStage} (${progressPercent}%)`} highlight />
                <AnswerTile title="3. WHY" value="Satisfy mathematical 20,000 LOC quality bar and remote CI green invariants" />
                <AnswerTile title="4. PROGRESS" value={`${progressPercent}% verified complete — 0 regressions across 486 contract tests`} />
                <AnswerTile title="5. NEXT" value="Package Android signed APK release and publish live preview studio suite" />
                <AnswerTile title="6. EVIDENCE" value="SHA-256 Merkle root seal verified — 0 unverified claims permitted" />
              </div>
            </div>
          )}

          {selectedId === 'approval-gate' && (
            <div className="max-w-xl mx-auto space-y-4 font-mono">
              <div className="p-5 rounded-xl border border-amber-500/30 bg-amber-500/5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-2 text-sm font-bold text-amber-400">
                    <ShieldCheck className="h-5 w-5" />
                    PRE-MUTATION APPROVAL REQUEST
                  </span>
                  <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/20 text-amber-300 font-bold border border-amber-500/40">
                    PENDING HUMAN DECISION
                  </span>
                </div>

                <div className="space-y-1 text-xs">
                  <div className="flex justify-between text-slate-400">
                    <span>Target Territory:</span>
                    <span className="text-white font-bold">src/main/kotlin/atropos/core/</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Proposed Action:</span>
                    <span className="text-cyan-400 font-bold">MUTATE_POLICY_GRAPH</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Actor Attribution:</span>
                    <span className="text-white">AutonomousOrchestrator:supervisor-1</span>
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] text-slate-300 leading-relaxed font-sans">
                  The engine requests authorization to adjust the provider fallback cascade to prioritize local offline LPUs during low-network jitter periods.
                </div>

                {approvalDecided === null ? (
                  <div className="flex gap-3 pt-2">
                    <button
                      onClick={() => setApprovalDecided(false)}
                      className="flex-1 py-2 rounded-lg border border-rose-500/40 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 font-bold text-xs transition-colors flex items-center justify-center gap-1.5"
                    >
                      <XCircle className="h-4 w-4" />
                      <span>Decline Action</span>
                    </button>
                    <button
                      onClick={() => setApprovalDecided(true)}
                      className="flex-1 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-black font-bold text-xs transition-colors flex items-center justify-center gap-1.5 shadow-lg shadow-emerald-500/20"
                    >
                      <CheckCircle2 className="h-4 w-4" />
                      <span>Authorize Execution</span>
                    </button>
                  </div>
                ) : (
                  <div
                    className={`p-3 rounded-lg border text-center text-xs font-bold ${
                      approvalDecided
                        ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                        : 'border-rose-500/40 bg-rose-500/10 text-rose-400'
                    }`}
                  >
                    {approvalDecided ? '✓ ACTION AUTHORIZED BY OPERATOR' : '✗ ACTION DECLINED BY OPERATOR'}
                  </div>
                )}
              </div>
            </div>
          )}

          {selectedId === 'quota-burn-meter' && (
            <div className="space-y-5 font-mono text-xs">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-1">
                  <span className="text-slate-400 text-[10px]">CURRENT BURN SPEED</span>
                  <p className="text-2xl font-bold text-amber-400">{tokenBurnRate} <span className="text-xs text-slate-400">tokens/sec</span></p>
                  <p className="text-[10px] text-emerald-400">Headroom optimal</p>
                </div>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-1">
                  <span className="text-slate-400 text-[10px]">SESSION EXPENDITURE</span>
                  <p className="text-2xl font-bold text-white">$0.14 <span className="text-xs text-slate-400">USD</span></p>
                  <p className="text-[10px] text-slate-400">88% subsidized via local LPU</p>
                </div>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-1">
                  <span className="text-slate-400 text-[10px]">ACTIVE CONTEXT WINDOW</span>
                  <p className="text-2xl font-bold text-cyan-400">42,890 <span className="text-xs text-slate-400">/ 200,000</span></p>
                  <p className="text-[10px] text-cyan-400">21.4% capacity consumed</p>
                </div>
              </div>

              <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/40 space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-300 font-bold">Context Window Saturation</span>
                  <span className="text-cyan-400 font-bold">21.4%</span>
                </div>
                <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                  <div className="h-full bg-cyan-400 rounded-full" style={{ width: '21.4%' }} />
                </div>
              </div>
            </div>
          )}

          {selectedId === 'merkle-seal' && (
            <div className="max-w-xl mx-auto space-y-4 font-mono text-xs">
              <div className="p-5 rounded-2xl border border-cyan-500/30 bg-cyan-950/20 space-y-3">
                <div className="flex items-center gap-2 text-cyan-300 font-bold text-sm">
                  <ShieldCheck className="h-5 w-5 text-cyan-400" />
                  <span>CRYPTOGRAPHIC STATE ROOT SEAL</span>
                </div>

                <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] break-all text-emerald-400">
                  sha256:7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069
                </div>

                <div className="space-y-1.5 text-[11px] text-slate-300">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Repository Seal:</span>
                    <span className="text-white">SWE-CLI-MJ/Atropos-SWE-CLI</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Branch & Epoch:</span>
                    <span className="text-cyan-400">main · commit 3ea114b2</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Verification Gate:</span>
                    <span className="text-emerald-400">PASS (486 contract selectors)</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {selectedId === 'command-launcher' && (
            <div className="space-y-3 font-mono text-xs">
              <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60">
                <p className="text-slate-400 text-xs">
                  The Slash Command Palette uses the Non-Overload Architecture:
                </p>
                <div className="mt-3 grid grid-cols-2 md:grid-cols-4 gap-2">
                  {['/goal', '/plan', '/model', '/files', '/tree', '/git', '/status', '/mcp'].map((cmd) => (
                    <div key={cmd} className="p-2.5 rounded-lg border border-slate-800 bg-slate-950 text-cyan-400 font-bold text-center">
                      {cmd}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {selectedId === 'ast-diagnostic-slice' && (
            <div className="space-y-3 font-mono text-xs">
              <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white flex items-center gap-1.5">
                    <Code2 className="h-4 w-4 text-cyan-400" />
                    File: src/main/kotlin/atropos/core/Router.kt
                  </span>
                  <span className="text-emerald-400 text-[10px] font-bold">ALL PASSES VERIFIED</span>
                </div>
                <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] text-slate-300 font-mono">
                  <code>
                    1: package atropos.core<br />
                    2: class Router(val registry: EndpointRegistry) &#123;<br />
                    3: &nbsp;&nbsp;fun dispatch(route: String): Response = registry.resolve(route)<br />
                    4: &#125;
                  </code>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function AnswerTile({
  title,
  value,
  highlight = false
}: {
  title: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <div
      className={`p-3.5 rounded-xl border text-xs font-mono space-y-1.5 transition-colors ${
        highlight
          ? 'border-cyan-500/40 bg-cyan-500/10 text-cyan-300'
          : 'border-slate-800 bg-slate-900/40 text-slate-300'
      }`}
    >
      <span className="text-[10px] font-bold tracking-wider text-slate-400 block">{title}</span>
      <p className="text-xs leading-relaxed font-sans text-white">{value}</p>
    </div>
  );
}

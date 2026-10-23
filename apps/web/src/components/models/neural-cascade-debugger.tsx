/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useMemo, FC } from 'react';
import {
  GitFork,
  Cpu,
  Layers,
  ArrowRight,
  ShieldCheck,
  Zap,
  AlertTriangle,
  Play,
  RotateCcw,
  CheckCircle2,
  XCircle,
  Clock,
  Coins,
  Settings2
} from 'lucide-react';

export interface CascadeTier {
  id: string;
  name: string;
  tierNumber: number;
  provider: string;
  maxTokens: number;
  costPer1kTokens: number; // USD
  typicalLatencyMs: number;
  failureRatePercent: number;
  status: 'ONLINE' | 'DEGRADED' | 'STANDBY';
}

export interface CascadeSimulationEvent {
  step: number;
  tierId: string;
  tierName: string;
  action: string;
  result: 'SUCCESS' | 'TRIGGERED_FALLBACK' | 'FATAL';
  reason: string;
  elapsedMs: number;
}

const DEFAULT_TIERS: CascadeTier[] = [
  {
    id: 'tier-1-fast',
    name: 'Sovereign Speculative Neural-1',
    tierNumber: 1,
    provider: 'Local-Inference-Daemon',
    maxTokens: 32768,
    costPer1kTokens: 0.0,
    typicalLatencyMs: 140,
    failureRatePercent: 12,
    status: 'ONLINE'
  },
  {
    id: 'tier-2-standard',
    name: 'Atropos Balanced Reasoner-3',
    tierNumber: 2,
    provider: 'High-Throughput-API',
    maxTokens: 65536,
    costPer1kTokens: 0.0015,
    typicalLatencyMs: 480,
    failureRatePercent: 4,
    status: 'ONLINE'
  },
  {
    id: 'tier-3-frontier',
    name: 'Sovereign Frontier DeepMind-Grade-5',
    tierNumber: 3,
    provider: 'Frontier-Cluster',
    maxTokens: 262144,
    costPer1kTokens: 0.015,
    typicalLatencyMs: 1650,
    failureRatePercent: 1,
    status: 'STANDBY'
  },
  {
    id: 'tier-4-airgap',
    name: 'Local Deterministic AST Grammar Engine',
    tierNumber: 4,
    provider: 'Termux-In-Process',
    maxTokens: 16384,
    costPer1kTokens: 0.0,
    typicalLatencyMs: 18,
    failureRatePercent: 0,
    status: 'ONLINE'
  }
];

export const NeuralCascadeDebugger: FC = () => {
  const [tiers, setTiers] = useState<CascadeTier[]>(DEFAULT_TIERS);
  const [simPromptLength, setSimPromptLength] = useState<number>(4500);
  const [simForceErrorTier1, setSimForceErrorTier1] = useState<boolean>(true);
  const [simForceErrorTier2, setSimForceErrorTier2] = useState<boolean>(false);
  const [simulationLogs, setSimulationLogs] = useState<CascadeSimulationEvent[] | null>(null);
  const [activeTierId, setActiveTierId] = useState<string | null>(null);

  // Run Cascade Simulation
  const handleRunSimulation = () => {
    const logs: CascadeSimulationEvent[] = [];
    let cumulativeLatency = 0;
    let resolved = false;

    for (let i = 0; i < tiers.length; i++) {
      const tier = tiers[i];
      cumulativeLatency += tier.typicalLatencyMs;

      // Check context length overflow
      if (simPromptLength > tier.maxTokens) {
        logs.push({
          step: i + 1,
          tierId: tier.id,
          tierName: tier.name,
          action: 'Evaluating Context Window Capacity',
          result: 'TRIGGERED_FALLBACK',
          reason: `Prompt tokens (${simPromptLength}) exceeded tier ceiling (${tier.maxTokens}). Cascading to next tier.`,
          elapsedMs: cumulativeLatency
        });
        continue;
      }

      // Check simulated synthetic errors
      if (i === 0 && simForceErrorTier1) {
        logs.push({
          step: i + 1,
          tierId: tier.id,
          tierName: tier.name,
          action: 'Executing Speculative Fast Inference',
          result: 'TRIGGERED_FALLBACK',
          reason: 'Simulated HTTP 429 Rate Limit / Concurrency saturated. Triggering fallback cascade.',
          elapsedMs: cumulativeLatency
        });
        continue;
      }

      if (i === 1 && simForceErrorTier2) {
        logs.push({
          step: i + 1,
          tierId: tier.id,
          tierName: tier.name,
          action: 'Executing Balanced Reasoning Pass',
          result: 'TRIGGERED_FALLBACK',
          reason: 'Network socket timeout (>5000ms) on secondary provider gateway. Triggering fallback cascade.',
          elapsedMs: cumulativeLatency
        });
        continue;
      }

      // Successful dispatch
      logs.push({
        step: i + 1,
        tierId: tier.id,
        tierName: tier.name,
        action: 'Inference Dispatched & Stream Verified',
        result: 'SUCCESS',
        reason: `Model response validated against AST guardrails. Completed successfully in ${tier.typicalLatencyMs}ms.`,
        elapsedMs: cumulativeLatency
      });
      resolved = true;
      setActiveTierId(tier.id);
      break;
    }

    if (!resolved) {
      logs.push({
        step: tiers.length + 1,
        tierId: 'none',
        tierName: 'Cascade Exhausted',
        action: 'All Fallback Tiers Failed',
        result: 'FATAL',
        reason: 'Entire multi-tier routing cascade failed. Activating emergency sovereign quarantine.',
        elapsedMs: cumulativeLatency
      });
      setActiveTierId(null);
    }

    setSimulationLogs(logs);
  };

  const finalOutcome = useMemo(() => {
    if (!simulationLogs || simulationLogs.length === 0) return null;
    return simulationLogs[simulationLogs.length - 1];
  }, [simulationLogs]);

  return (
    <div className="space-y-6 rounded-xl border border-slate-800 bg-slate-950/70 p-6 backdrop-blur-md shadow-sm">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <GitFork className="h-5 w-5 text-cyan-400" />
            <h2 className="text-lg font-semibold text-slate-100">
              Neural Routing & Fallback Cascade Simulator
            </h2>
          </div>
          <p className="mt-1 text-xs text-slate-400">
            Deterministic failover pipeline: orchestrates low-latency local execution with automated fallback across frontier and air-gapped models.
          </p>
        </div>

        <button
          type="button"
          onClick={handleRunSimulation}
          className="flex items-center gap-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white px-3.5 py-1.5 text-xs font-semibold transition"
        >
          <Play className="h-3.5 w-3.5" />
          Test Cascade Execution
        </button>
      </div>

      {/* Tiers Visualizer Pipeline */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        {tiers.map((tier, idx) => {
          const isActive = activeTierId === tier.id;
          return (
            <div
              key={tier.id}
              className={`p-3.5 rounded-xl border transition text-xs space-y-2.5 relative ${
                isActive
                  ? 'border-emerald-500 bg-emerald-500/10 shadow-lg'
                  : 'border-slate-800 bg-slate-900/50'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-mono text-[10px] uppercase font-bold text-cyan-400">
                  TIER {tier.tierNumber}
                </span>
                <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[9px] font-mono text-slate-300">
                  {tier.status}
                </span>
              </div>

              <div>
                <div className="font-semibold text-slate-200 truncate">{tier.name}</div>
                <div className="font-mono text-[10px] text-slate-400">{tier.provider}</div>
              </div>

              <div className="space-y-1 pt-1 border-t border-slate-800/80 font-mono text-[10px] text-slate-400">
                <div className="flex justify-between">
                  <span>Context:</span>
                  <span className="text-slate-200">{(tier.maxTokens / 1024).toFixed(0)}k tokens</span>
                </div>
                <div className="flex justify-between">
                  <span>Latency:</span>
                  <span className="text-slate-200">{tier.typicalLatencyMs} ms</span>
                </div>
                <div className="flex justify-between">
                  <span>Est Cost:</span>
                  <span className="text-slate-200">${tier.costPer1kTokens}/1k</span>
                </div>
              </div>

              {isActive && (
                <div className="flex items-center gap-1 text-[10px] font-bold text-emerald-400 pt-1">
                  <CheckCircle2 className="h-3 w-3" />
                  RESOLVED ACTIVE TARGET
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Simulation Controls & Parameters */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 space-y-3">
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-300">
          <Settings2 className="h-4 w-4 text-cyan-400" />
          <span>Failure Injection & Context Stress Controls</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
          <div>
            <label className="block text-slate-400 mb-1 text-[11px]">
              Simulated Prompt Size: {simPromptLength} tokens
            </label>
            <input
              type="range"
              min="1000"
              max="150000"
              step="5000"
              value={simPromptLength}
              onChange={(e) => setSimPromptLength(Number(e.target.value))}
              className="w-full accent-cyan-400"
            />
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="inject-tier1"
              checked={simForceErrorTier1}
              onChange={(e) => setSimForceErrorTier1(e.target.checked)}
              className="rounded border-slate-700 accent-cyan-500"
            />
            <label htmlFor="inject-tier1" className="text-slate-300 text-[11px]">
              Inject Tier 1 Rate-Limit (429)
            </label>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="inject-tier2"
              checked={simForceErrorTier2}
              onChange={(e) => setSimForceErrorTier2(e.target.checked)}
              className="rounded border-slate-700 accent-cyan-500"
            />
            <label htmlFor="inject-tier2" className="text-slate-300 text-[11px]">
              Inject Tier 2 Socket Timeout
            </label>
          </div>
        </div>
      </div>

      {/* Simulation Results & Trace */}
      {simulationLogs && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-200">
              <Zap className="h-4 w-4 text-amber-400" />
              <span>Cascade Execution Trace ({simulationLogs.length} steps)</span>
            </div>

            {finalOutcome && (
              <span
                className={`flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-bold ${
                  finalOutcome.result === 'SUCCESS'
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                    : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                }`}
              >
                {finalOutcome.result === 'SUCCESS' ? (
                  <>
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    CASCADE RESOLVED ({finalOutcome.elapsedMs}ms total)
                  </>
                ) : (
                  <>
                    <XCircle className="h-3.5 w-3.5" />
                    FAILED ({finalOutcome.elapsedMs}ms total)
                  </>
                )}
              </span>
            )}
          </div>

          <div className="space-y-2">
            {simulationLogs.map((log) => (
              <div
                key={log.step}
                className="p-3 rounded-lg border border-slate-800 bg-slate-950/60 text-xs font-mono space-y-1"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-slate-500">Step {log.step}:</span>
                    <span className="font-semibold text-slate-200">{log.tierName}</span>
                    <span
                      className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                        log.result === 'SUCCESS'
                          ? 'bg-emerald-500/20 text-emerald-400'
                          : log.result === 'TRIGGERED_FALLBACK'
                          ? 'bg-amber-500/20 text-amber-400'
                          : 'bg-rose-500/20 text-rose-400'
                      }`}
                    >
                      {log.result}
                    </span>
                  </div>
                  <span className="text-slate-400">{log.elapsedMs} ms elapsed</span>
                </div>
                <p className="text-[11px] text-slate-400 font-sans">{log.reason}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

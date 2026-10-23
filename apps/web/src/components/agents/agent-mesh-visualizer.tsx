/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';
import {
  Users,
  Cpu,
  Zap,
  Activity,
  Shield,
  Layers,
  ArrowRight,
  CheckCircle,
  Clock,
  Sparkles,
  RefreshCw
} from 'lucide-react';

export interface AgentNodeData {
  id: string;
  name: string;
  role: 'supervisor' | 'planner' | 'coder' | 'tester' | 'verifier';
  status: 'idle' | 'working' | 'blocked' | 'verifying';
  provider: string;
  model: string;
  activeTask?: string;
  tokensUsed: number;
  cpuPercent: number;
  memoryMb: number;
}

const INITIAL_NODES: AgentNodeData[] = [
  {
    id: 'agent-sup',
    name: 'Sovereign Supervisor',
    role: 'supervisor',
    status: 'working',
    provider: 'Frontier Alpha',
    model: 'alpha-3.7-sonnet',
    activeTask: 'Coordinating multi-tier hypothesis sequence and Merkle checkpointing',
    tokensUsed: 12450,
    cpuPercent: 14.2,
    memoryMb: 320
  },
  {
    id: 'agent-plan',
    name: 'Strategic Planner',
    role: 'planner',
    status: 'working',
    provider: 'DeepSeek R1',
    model: 'deepseek-reasoner-v3',
    activeTask: 'Synthesizing DAG topology for AST self-healing and contract proofs',
    tokensUsed: 8900,
    cpuPercent: 9.8,
    memoryMb: 240
  },
  {
    id: 'agent-coder',
    name: 'Code Synthesizer',
    role: 'coder',
    status: 'working',
    provider: 'Groq Llama-3.3',
    model: 'llama-3.3-70b-versatile',
    activeTask: 'Emitting deterministic Kotlin AST transformations and Compose screens',
    tokensUsed: 24100,
    cpuPercent: 32.5,
    memoryMb: 512
  },
  {
    id: 'agent-test',
    name: 'Verification Sentry',
    role: 'tester',
    status: 'verifying',
    provider: 'Gemini 1.5 Pro',
    model: 'gemini-1.5-pro',
    activeTask: 'Running 486 contract selectors and AST signature tests',
    tokensUsed: 6200,
    cpuPercent: 18.0,
    memoryMb: 280
  }
];

export function AgentMeshVisualizer({ projectId }: { projectId: string }) {
  const [nodes, setNodes] = useState<AgentNodeData[]>(INITIAL_NODES);
  const [selectedNodeId, setSelectedNodeId] = useState<string>(INITIAL_NODES[0].id);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) || nodes[0];

  const getRoleBadgeColor = (role: AgentNodeData['role']) => {
    switch (role) {
      case 'supervisor':
        return 'bg-purple-950 text-purple-300 border-purple-800';
      case 'planner':
        return 'bg-indigo-950 text-indigo-300 border-indigo-800';
      case 'coder':
        return 'bg-cyan-950 text-cyan-300 border-cyan-800';
      case 'tester':
      case 'verifier':
        return 'bg-emerald-950 text-emerald-300 border-emerald-800';
    }
  };

  const getStatusBadge = (status: AgentNodeData['status']) => {
    switch (status) {
      case 'working':
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-cyan-400">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
            Working
          </span>
        );
      case 'verifying':
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-emerald-400">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
            Verifying
          </span>
        );
      case 'idle':
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-slate-500">
            <span className="w-2 h-2 rounded-full bg-slate-500" />
            Idle
          </span>
        );
      case 'blocked':
        return (
          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-rose-400">
            <span className="w-2 h-2 rounded-full bg-rose-400" />
            Blocked
          </span>
        );
    }
  };

  return (
    <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden shadow-2xl text-slate-100 font-sans">
      <div className="flex items-center justify-between p-4 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <Layers className="w-5 h-5 text-cyan-400" />
          <div>
            <h3 className="font-semibold text-sm text-slate-100">
              Autonomous Swarm Topology & Mesh
            </h3>
            <p className="text-xs text-slate-400">
              Deterministic task delegation, lane concurrency, and verification checkpoints.
            </p>
          </div>
        </div>

        <button
          onClick={() => {
            // Jitter resource metrics to simulate live heartbeats
            setNodes((prev) =>
              prev.map((n) => ({
                ...n,
                cpuPercent: Math.max(5, Math.min(85, n.cpuPercent + (Math.random() * 6 - 3))),
                memoryMb: Math.max(120, Math.min(1024, n.memoryMb + Math.floor(Math.random() * 8 - 4)))
              }))
            );
          }}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-mono text-slate-300 rounded-lg border border-slate-700 transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Poll Heartbeats
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 divide-y lg:divide-y-0 lg:divide-x divide-slate-800">
        {/* Node Topology Canvas */}
        <div className="lg:col-span-2 p-6 flex flex-col justify-center space-y-6">
          <div className="flex justify-center">
            {/* Tier 1: Supervisor */}
            {nodes.filter((n) => n.role === 'supervisor').map((node) => (
              <div
                key={node.id}
                onClick={() => setSelectedNodeId(node.id)}
                className={`p-4 rounded-xl border cursor-pointer transition-all max-w-sm w-full ${
                  selectedNodeId === node.id
                    ? 'bg-slate-900 border-cyan-500 shadow-lg shadow-cyan-950/40'
                    : 'bg-slate-950/80 border-slate-800 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-[11px] font-mono px-2 py-0.5 rounded border ${getRoleBadgeColor(node.role)}`}>
                    {node.role.toUpperCase()}
                  </span>
                  {getStatusBadge(node.status)}
                </div>
                <h4 className="font-semibold text-slate-200 text-sm">{node.name}</h4>
                <p className="text-xs text-slate-400 mt-1 line-clamp-1">{node.activeTask}</p>
              </div>
            ))}
          </div>

          <div className="flex justify-center text-slate-600 font-mono text-xs">
            <span>▼ Gated Delegation & Checkpointing ▼</span>
          </div>

          {/* Tier 2: Planner & Coder & Verifier */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {nodes.filter((n) => n.role !== 'supervisor').map((node) => (
              <div
                key={node.id}
                onClick={() => setSelectedNodeId(node.id)}
                className={`p-4 rounded-xl border cursor-pointer transition-all ${
                  selectedNodeId === node.id
                    ? 'bg-slate-900 border-cyan-500 shadow-lg shadow-cyan-950/40'
                    : 'bg-slate-950/80 border-slate-800 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-[11px] font-mono px-2 py-0.5 rounded border ${getRoleBadgeColor(node.role)}`}>
                    {node.role.toUpperCase()}
                  </span>
                  {getStatusBadge(node.status)}
                </div>
                <h4 className="font-semibold text-slate-200 text-sm">{node.name}</h4>
                <p className="text-xs text-slate-400 mt-1 line-clamp-2">{node.activeTask}</p>
                <div className="mt-3 pt-2 border-t border-slate-800 flex items-center justify-between text-[11px] font-mono text-slate-500">
                  <span>{node.provider}</span>
                  <span>{node.cpuPercent.toFixed(1)}% CPU</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Selected Node Telemetry Inspector */}
        <div className="p-6 bg-slate-900/40 space-y-5">
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className={`text-xs font-mono px-2.5 py-0.5 rounded border ${getRoleBadgeColor(selectedNode.role)}`}>
                {selectedNode.role.toUpperCase()}
              </span>
              {getStatusBadge(selectedNode.status)}
            </div>
            <h3 className="text-lg font-bold text-slate-100">{selectedNode.name}</h3>
            <p className="text-xs font-mono text-cyan-400 mt-0.5">
              {selectedNode.provider} / {selectedNode.model}
            </p>
          </div>

          <div className="space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Active Objective
            </span>
            <div className="p-3 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-300 leading-relaxed font-mono">
              {selectedNode.activeTask || 'No active task assigned.'}
            </div>
          </div>

          <div className="space-y-3">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Live Hardware Utilization
            </span>
            <div className="space-y-2 text-xs font-mono">
              <div>
                <div className="flex justify-between text-slate-400 mb-1">
                  <span>CPU Load</span>
                  <span className="text-slate-200">{selectedNode.cpuPercent.toFixed(1)}%</span>
                </div>
                <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-cyan-500 rounded-full transition-all duration-300"
                    style={{ width: `${selectedNode.cpuPercent}%` }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-slate-400 mb-1">
                  <span>Memory RSS</span>
                  <span className="text-slate-200">{selectedNode.memoryMb} MB</span>
                </div>
                <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-purple-500 rounded-full transition-all duration-300"
                    style={{ width: `${(selectedNode.memoryMb / 1024) * 100}%` }}
                  />
                </div>
              </div>

              <div className="pt-2 flex justify-between items-center text-slate-400">
                <span>Tokens Consumed:</span>
                <span className="font-semibold text-emerald-400">
                  {selectedNode.tokensUsed.toLocaleString()} tok
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

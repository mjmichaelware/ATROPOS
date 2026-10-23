/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState } from 'react';
import {
  Users,
  Cpu,
  Shield,
  Layers,
  X,
  Plus,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';

export interface AgentAssignmentData {
  name: string;
  role: 'supervisor' | 'planner' | 'coder' | 'tester' | 'verifier';
  provider: string;
  model: string;
  primaryResponsibility: string;
  temperature: number;
  maxTokens: number;
  mcpPermissions: string[];
}

export interface AgentAssignmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAssign: (data: AgentAssignmentData) => void;
}

const AVAILABLE_PROVIDERS = [
  { id: 'primary', name: 'Frontier Neural Engine Alpha', models: ['alpha-3.7-sonnet', 'alpha-3.5-haiku'] },
  { id: 'omega', name: 'Frontier Neural Engine Omega', models: ['omega-4o-hyper', 'omega-mini'] },
  { id: 'gemini', name: 'Gemini Sovereign Hub', models: ['gemini-1.5-pro', 'gemini-1.5-flash'] },
  { id: 'groq', name: 'Groq Realtime Inference', models: ['llama-3.3-70b-versatile'] },
  { id: 'deepseek', name: 'DeepSeek Reasoning Node', models: ['deepseek-reasoner-v3'] }
];

const AVAILABLE_MCP_PERMISSIONS = [
  { id: 'fs_read', label: 'File System (Read Only)', desc: 'Inspect source code and directory trees' },
  { id: 'fs_write', label: 'File System (Safe Write)', desc: 'Apply unified diffs within project territory' },
  { id: 'terminal_exec', label: 'Terminal Command Execution', desc: 'Run build, test, and compilation commands' },
  { id: 'git_mutation', label: 'Git Mutation & Branching', desc: 'Create commits, branches, and rebase tags' },
  { id: 'mcp_bridge', label: 'External MCP Server Relay', desc: 'Invoke external tool servers and plugins' }
];

export function AgentAssignmentModal({ isOpen, onClose, onAssign }: AgentAssignmentModalProps) {
  const [name, setName] = useState('');
  const [role, setRole] = useState<AgentAssignmentData['role']>('coder');
  const [selectedProvider, setSelectedProvider] = useState(AVAILABLE_PROVIDERS[0].id);
  const [selectedModel, setSelectedModel] = useState(AVAILABLE_PROVIDERS[0].models[0]);
  const [primaryResponsibility, setPrimaryResponsibility] = useState('');
  const [temperature, setTemperature] = useState(0.2);
  const [maxTokens, setMaxTokens] = useState(8192);
  const [permissions, setPermissions] = useState<string[]>(['fs_read', 'fs_write']);

  if (!isOpen) return null;

  const currentProviderConfig = AVAILABLE_PROVIDERS.find((p) => p.id === selectedProvider) || AVAILABLE_PROVIDERS[0];

  const togglePermission = (id: string) => {
    setPermissions((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !primaryResponsibility.trim()) return;

    onAssign({
      name: name.trim(),
      role,
      provider: selectedProvider,
      model: selectedModel,
      primaryResponsibility: primaryResponsibility.trim(),
      temperature,
      maxTokens,
      mcpPermissions: permissions
    });

    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-fade-in font-sans">
      <div className="bg-slate-900 border border-slate-800 rounded-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-2xl text-slate-100">
        <div className="flex items-center justify-between p-5 border-b border-slate-800 bg-slate-950/80">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-cyan-950 border border-cyan-800 rounded-lg text-cyan-400">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-base text-slate-100">
                Deploy Autonomous Agent to Swarm
              </h3>
              <p className="text-xs text-slate-400">
                Define specialized responsibilities, model tier, and security boundary.
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-200 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5 text-xs">
          {/* Agent Identity & Role */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                Agent Designation / Name
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. AST Self-Healing Specialist"
                required
                className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-slate-100 font-sans focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                Swarm Role Hierarchy
              </label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as AgentAssignmentData['role'])}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-slate-100 font-sans focus:outline-none focus:border-cyan-500"
              >
                <option value="supervisor">Supervisor (Goal decomposition & Merkle seals)</option>
                <option value="planner">Strategic Planner (Lattice & DAG sequencing)</option>
                <option value="coder">Code Synthesizer (AST and Kotlin generation)</option>
                <option value="tester">Verification Sentry (Test and contract execution)</option>
                <option value="verifier">Release Verifier (Proof generation)</option>
              </select>
            </div>
          </div>

          {/* Neural Provider & Model Selection */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                Inference Provider Engine
              </label>
              <select
                value={selectedProvider}
                onChange={(e) => {
                  setSelectedProvider(e.target.value);
                  const p = AVAILABLE_PROVIDERS.find((prov) => prov.id === e.target.value);
                  if (p && p.models.length > 0) setSelectedModel(p.models[0]);
                }}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-slate-100 font-sans focus:outline-none focus:border-cyan-500"
              >
                {AVAILABLE_PROVIDERS.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                Model Topology
              </label>
              <select
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-slate-100 font-sans focus:outline-none focus:border-cyan-500"
              >
                {currentProviderConfig.models.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Primary Responsibility Specification */}
          <div>
            <label className="block font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
              Primary Responsibility Specification
            </label>
            <textarea
              value={primaryResponsibility}
              onChange={(e) => setPrimaryResponsibility(e.target.value)}
              placeholder="Describe the precise single responsibility for this agent (e.g. Invariant analysis and AST mutation without regression)..."
              rows={3}
              required
              className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-slate-100 font-sans focus:outline-none focus:border-cyan-500 resize-none"
            />
          </div>

          {/* Hyperparameters */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-slate-950/60 p-3.5 rounded-lg border border-slate-800">
            <div>
              <div className="flex justify-between mb-1 font-mono">
                <span className="text-slate-400">Temperature</span>
                <span className="text-cyan-400 font-bold">{temperature}</span>
              </div>
              <input
                type="range"
                min="0.0"
                max="1.0"
                step="0.05"
                value={temperature}
                onChange={(e) => setTemperature(parseFloat(e.target.value))}
                className="w-full accent-cyan-500"
              />
            </div>

            <div>
              <div className="flex justify-between mb-1 font-mono">
                <span className="text-slate-400">Max Completion Tokens</span>
                <span className="text-cyan-400 font-bold">{maxTokens}</span>
              </div>
              <input
                type="range"
                min="1024"
                max="32768"
                step="1024"
                value={maxTokens}
                onChange={(e) => setMaxTokens(parseInt(e.target.value))}
                className="w-full accent-cyan-500"
              />
            </div>
          </div>

          {/* Tool Permissions Checklist */}
          <div>
            <label className="block font-semibold text-slate-300 mb-2 uppercase tracking-wider">
              Tool & MCP Permissions Gating
            </label>
            <div className="space-y-2">
              {AVAILABLE_MCP_PERMISSIONS.map((perm) => (
                <label
                  key={perm.id}
                  onClick={() => togglePermission(perm.id)}
                  className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                    permissions.includes(perm.id)
                      ? 'bg-slate-950 border-cyan-500/50'
                      : 'bg-slate-950/40 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={permissions.includes(perm.id)}
                    onChange={() => {}}
                    className="mt-0.5 accent-cyan-500 rounded"
                  />
                  <div>
                    <span className="font-semibold text-slate-200">{perm.label}</span>
                    <p className="text-[11px] text-slate-400 mt-0.5">{perm.desc}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* Modal Footer */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg font-semibold text-slate-400 hover:bg-slate-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg font-semibold transition-colors shadow-lg shadow-cyan-900/30 flex items-center gap-2"
            >
              <Plus className="w-4 h-4" />
              Deploy Agent
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

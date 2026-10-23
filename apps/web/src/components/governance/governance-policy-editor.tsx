/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useEffect, useMemo, FC } from 'react';
import {
  ShieldAlert,
  ShieldCheck,
  KeyRound,
  FileCode,
  CheckCircle2,
  XCircle,
  Play,
  Download,
  Upload,
  Plus,
  Trash2,
  Info,
  Layers,
  Lock,
  Unlock,
  RefreshCw,
  Sliders,
  Scale
} from 'lucide-react';

export type PolicyActionKind = 'fs_read' | 'fs_write' | 'network_outbound' | 'ast_mutate' | 'mcp_invoke' | 'shell_exec';

export type PolicyEnforcement = 'ALLOW' | 'DENY' | 'REQUIRE_HUMAN_QUORUM';

export interface PolicyRule {
  id: string;
  name: string;
  actionKind: PolicyActionKind;
  resourcePattern: string; // regex pattern
  enforcement: PolicyEnforcement;
  reason: string;
  enabled: boolean;
}

export interface SovereignKeyHolder {
  id: string;
  name: string;
  publicKey: string;
  weight: number;
  signed: boolean;
}

export interface PolicyDocument {
  version: string;
  merkleRoot: string;
  quorumThresholdPercent: number;
  totalWeight: number;
  signedWeight: number;
  rules: PolicyRule[];
  lastAmended: string;
}

const DEFAULT_KEY_HOLDERS: SovereignKeyHolder[] = [
  { id: 'key-1', name: 'Primary Architect (ECDSA)', publicKey: '0x04a37b42f...91c', weight: 40, signed: true },
  { id: 'key-2', name: 'Automated AST Sentinel', publicKey: '0x028bf419e...88a', weight: 25, signed: true },
  { id: 'key-3', name: 'Hardware Attest Security Key', publicKey: '0x03ec1907d...55c', weight: 20, signed: false },
  { id: 'key-4', name: 'Audit & Governance Custodian', publicKey: '0x044621ab7...31f', weight: 15, signed: true }
];

const INITIAL_RULES: PolicyRule[] = [
  {
    id: 'rule-fs-safe',
    name: 'Workspace Sandbox Boundary',
    actionKind: 'fs_write',
    resourcePattern: '^/data/data/com\\.termux/files/home/ATROPOS/.*',
    enforcement: 'ALLOW',
    reason: 'Permit non-destructive repository writes inside designated workspace root.',
    enabled: true
  },
  {
    id: 'rule-fs-block-etc',
    name: 'System Root Immutability',
    actionKind: 'fs_write',
    resourcePattern: '^/(etc|sys|proc|boot)/.*',
    enforcement: 'DENY',
    reason: 'Strictly prohibit system partition mutation to prevent environment corruption.',
    enabled: true
  },
  {
    id: 'rule-net-loopback',
    name: 'Loopback Bridge Access',
    actionKind: 'network_outbound',
    resourcePattern: '^(127\\.0\\.0\\.1|localhost|0\\.0\\.0\\.0):.*',
    enforcement: 'ALLOW',
    reason: 'Allow communication with local sovereign engine daemon ports.',
    enabled: true
  },
  {
    id: 'rule-mcp-quorum',
    name: 'Privileged MCP Tool Execution',
    actionKind: 'mcp_invoke',
    resourcePattern: '^(git_push_remote|delete_database|release_production).*',
    enforcement: 'REQUIRE_HUMAN_QUORUM',
    reason: 'High-blast-radius operations require verified cryptographic quorum signature.',
    enabled: true
  },
  {
    id: 'rule-shell-quarantine',
    name: 'Dangerous Shell Process Guard',
    actionKind: 'shell_exec',
    resourcePattern: '.*(rm -rf /|dd if=|mkfs|chmod -R 777).*',
    enforcement: 'DENY',
    reason: 'Catastrophic destruction shell signatures blocked immediately.',
    enabled: true
  }
];

export const GovernancePolicyEditor: FC = () => {
  const [rules, setRules] = useState<PolicyRule[]>(INITIAL_RULES);
  const [keys, setKeys] = useState<SovereignKeyHolder[]>(DEFAULT_KEY_HOLDERS);
  const [quorumPercent, setQuorumPercent] = useState<number>(66);
  const [merkleRoot, setMerkleRoot] = useState<string>('');
  const [isAmending, setIsAmending] = useState(false);

  // New Rule Form
  const [newRuleName, setNewRuleName] = useState('');
  const [newRuleAction, setNewRuleAction] = useState<PolicyActionKind>('fs_write');
  const [newRulePattern, setNewRulePattern] = useState('');
  const [newRuleEnforcement, setNewRuleEnforcement] = useState<PolicyEnforcement>('ALLOW');
  const [newRuleReason, setNewRuleReason] = useState('');

  // Simulator State
  const [simActionKind, setSimActionKind] = useState<PolicyActionKind>('fs_write');
  const [simTarget, setSimTarget] = useState('/data/data/com.termux/files/home/ATROPOS/app/src/Main.kt');
  const [simResult, setSimResult] = useState<{
    outcome: PolicyEnforcement;
    matchedRule: PolicyRule | null;
    evaluatedAt: string;
  } | null>(null);

  // Calculate Weights and Quorum
  const totalWeight = useMemo(() => keys.reduce((acc, k) => acc + k.weight, 0), [keys]);
  const signedWeight = useMemo(
    () => keys.filter((k) => k.signed).reduce((acc, k) => acc + k.weight, 0),
    [keys]
  );
  const currentQuorumPercent = useMemo(
    () => (totalWeight > 0 ? Math.round((signedWeight / totalWeight) * 100) : 0),
    [signedWeight, totalWeight]
  );
  const isQuorumMet = currentQuorumPercent >= quorumPercent;

  // Compute SHA-256 Merkle Root of current policy rules
  useEffect(() => {
    let active = true;
    const computeHash = async () => {
      const canonicalData = JSON.stringify({
        rules: rules.map((r) => ({
          name: r.name,
          actionKind: r.actionKind,
          resourcePattern: r.resourcePattern,
          enforcement: r.enforcement,
          enabled: r.enabled
        })),
        quorumPercent
      });

      try {
        const encoder = new TextEncoder();
        const data = encoder.encode(canonicalData);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        const hashHex = hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
        if (active) {
          setMerkleRoot(`0x${hashHex}`);
        }
      } catch {
        if (active) {
          setMerkleRoot('0x-unavailable-crypto-subtle');
        }
      }
    };

    void computeHash();
    return () => {
      active = false;
    };
  }, [rules, quorumPercent]);

  // Toggle key signature
  const handleToggleSign = (keyId: string) => {
    setKeys((prev) =>
      prev.map((k) => (k.id === keyId ? { ...k, signed: !k.signed } : k))
    );
  };

  // Add rule
  const handleAddRule = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRuleName.trim() || !newRulePattern.trim()) return;

    const newRule: PolicyRule = {
      id: `rule-${Date.now()}`,
      name: newRuleName.trim(),
      actionKind: newRuleAction,
      resourcePattern: newRulePattern.trim(),
      enforcement: newRuleEnforcement,
      reason: newRuleReason.trim() || 'Custom governance constraint.',
      enabled: true
    };

    setRules((prev) => [newRule, ...prev]);
    setNewRuleName('');
    setNewRulePattern('');
    setNewRuleReason('');
  };

  // Delete rule
  const handleDeleteRule = (id: string) => {
    setRules((prev) => prev.filter((r) => r.id !== id));
  };

  // Toggle rule enabled
  const handleToggleRule = (id: string) => {
    setRules((prev) =>
      prev.map((r) => (r.id === id ? { ...r, enabled: !r.enabled } : r))
    );
  };

  // Run Simulation against Active Rules
  const handleRunSimulation = () => {
    const activeRules = rules.filter((r) => r.enabled && r.actionKind === simActionKind);
    let matched: PolicyRule | null = null;

    for (const rule of activeRules) {
      try {
        const regex = new RegExp(rule.resourcePattern);
        if (regex.test(simTarget)) {
          matched = rule;
          break;
        }
      } catch {
        // invalid regex skip
      }
    }

    const outcome: PolicyEnforcement = matched
      ? matched.enforcement
      : 'DENY'; // default closed posture

    setSimResult({
      outcome,
      matchedRule: matched,
      evaluatedAt: new Date().toLocaleTimeString()
    });
  };

  // Export JSON Bundle
  const handleExportJson = () => {
    const doc: PolicyDocument = {
      version: '1.4.0',
      merkleRoot,
      quorumThresholdPercent: quorumPercent,
      totalWeight,
      signedWeight,
      rules,
      lastAmended: new Date().toISOString()
    };
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `governance-policy-${merkleRoot.slice(2, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6 rounded-xl border border-sg-neutral-200 dark:border-sg-neutral-800 bg-white/70 dark:bg-sg-neutral-900/60 p-6 backdrop-blur-md shadow-sm">
      {/* Top Banner & Merkle Attestation */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-sg-neutral-200 dark:border-sg-neutral-800 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <Scale className="h-5 w-5 text-indigo-500" />
            <h2 className="text-lg font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
              Cryptographic Policy & Quorum Governor
            </h2>
          </div>
          <p className="mt-1 text-xs text-sg-neutral-500 dark:text-sg-neutral-400">
            Enforces deterministic execution constraints across filesystem, outbound network, AST mutations, and MCP host invocations.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950 px-3 py-1.5 font-mono text-xs">
            <KeyRound className="h-3.5 w-3.5 text-indigo-400" />
            <span className="text-sg-neutral-500">Root:</span>
            <span className="text-indigo-600 dark:text-indigo-400 font-semibold" title={merkleRoot}>
              {merkleRoot ? `${merkleRoot.slice(0, 10)}...${merkleRoot.slice(-6)}` : 'Computing...'}
            </span>
          </div>

          <button
            type="button"
            onClick={handleExportJson}
            className="flex items-center gap-1.5 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-sg-neutral-100 dark:bg-sg-neutral-800 px-3 py-1.5 text-xs font-medium text-sg-neutral-800 dark:text-sg-neutral-200 hover:bg-sg-neutral-200 dark:hover:bg-sg-neutral-700 transition"
          >
            <Download className="h-3.5 w-3.5" />
            Export Signed Policy
          </button>
        </div>
      </div>

      {/* Quorum & Sovereign Keyring Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Quorum Health Card */}
        <div className="rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-sg-neutral-500 dark:text-sg-neutral-400">
              Quorum Status
            </span>
            {isQuorumMet ? (
              <span className="flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
                <CheckCircle2 className="h-3 w-3" />
                QUORUM VERIFIED ({currentQuorumPercent}%)
              </span>
            ) : (
              <span className="flex items-center gap-1 rounded-full bg-rose-500/10 px-2 py-0.5 text-[11px] font-semibold text-rose-600 dark:text-rose-400 border border-rose-500/30">
                <XCircle className="h-3 w-3" />
                INSUFFICIENT ({currentQuorumPercent}% / {quorumPercent}%)
              </span>
            )}
          </div>

          <div className="mt-3">
            <div className="flex justify-between text-xs text-sg-neutral-600 dark:text-sg-neutral-300 mb-1">
              <span>Signed Key Weight:</span>
              <span className="font-mono font-medium">
                {signedWeight} / {totalWeight} ({currentQuorumPercent}%)
              </span>
            </div>
            <div className="h-2 w-full rounded-full bg-sg-neutral-200 dark:bg-sg-neutral-800 overflow-hidden">
              <div
                className={`h-full transition-all duration-300 ${
                  isQuorumMet ? 'bg-emerald-500' : 'bg-rose-500'
                }`}
                style={{ width: `${Math.min(100, currentQuorumPercent)}%` }}
              />
            </div>
          </div>

          <div className="mt-4 flex items-center justify-between text-xs">
            <span className="text-sg-neutral-500">Threshold Requirement:</span>
            <div className="flex items-center gap-2">
              <input
                type="range"
                min="50"
                max="100"
                step="5"
                value={quorumPercent}
                onChange={(e) => setQuorumPercent(Number(e.target.value))}
                className="w-20 accent-indigo-500"
              />
              <span className="font-mono text-sg-neutral-700 dark:text-sg-neutral-300">
                {quorumPercent}%
              </span>
            </div>
          </div>
        </div>

        {/* Active Sovereign Keys Multi-Signature Matrix */}
        <div className="md:col-span-2 rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800 bg-sg-neutral-50 dark:bg-sg-neutral-950/60 p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-medium text-sg-neutral-500 dark:text-sg-neutral-400">
              Sovereign Keyring Authority
            </span>
            <span className="text-[11px] text-sg-neutral-400">
              Click signature badge to toggle cryptographic attestation
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-2">
            {keys.map((k) => (
              <div
                key={k.id}
                onClick={() => handleToggleSign(k.id)}
                className={`flex items-center justify-between p-2.5 rounded-lg border cursor-pointer transition ${
                  k.signed
                    ? 'border-emerald-500/40 bg-emerald-500/5 hover:bg-emerald-500/10'
                    : 'border-sg-neutral-200 dark:border-sg-neutral-800 bg-white dark:bg-sg-neutral-900 opacity-60 hover:opacity-100'
                }`}
              >
                <div>
                  <div className="text-xs font-medium text-sg-neutral-900 dark:text-sg-neutral-100">
                    {k.name}
                  </div>
                  <div className="font-mono text-[10px] text-sg-neutral-500">
                    {k.publicKey} · wt {k.weight}
                  </div>
                </div>

                <div className="flex items-center">
                  {k.signed ? (
                    <span className="flex items-center gap-1 text-[10px] font-semibold text-emerald-600 dark:text-emerald-400">
                      <Lock className="h-3 w-3" />
                      SIGNED
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-[10px] font-semibold text-sg-neutral-400">
                      <Unlock className="h-3 w-3" />
                      UNSIGNED
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Simulator Sandbox */}
      <div className="rounded-lg border border-indigo-200 dark:border-indigo-950/60 bg-indigo-50/50 dark:bg-indigo-950/20 p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Play className="h-4 w-4 text-indigo-500" />
            <h3 className="text-sm font-semibold text-indigo-950 dark:text-indigo-200">
              Live Policy Simulation Sandbox
            </h3>
          </div>
          <span className="text-[11px] text-indigo-600 dark:text-indigo-400 font-mono">
            Zero-risk dry run against active rule graph
          </span>
        </div>

        <div className="flex flex-col sm:flex-row items-center gap-3">
          <select
            value={simActionKind}
            onChange={(e) => setSimActionKind(e.target.value as PolicyActionKind)}
            className="w-full sm:w-44 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
          >
            <option value="fs_write">fs_write</option>
            <option value="fs_read">fs_read</option>
            <option value="network_outbound">network_outbound</option>
            <option value="ast_mutate">ast_mutate</option>
            <option value="mcp_invoke">mcp_invoke</option>
            <option value="shell_exec">shell_exec</option>
          </select>

          <input
            type="text"
            value={simTarget}
            onChange={(e) => setSimTarget(e.target.value)}
            placeholder="Simulated target URI, file path, or command..."
            className="flex-1 w-full rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
          />

          <button
            type="button"
            onClick={handleRunSimulation}
            className="w-full sm:w-auto px-4 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs transition flex items-center justify-center gap-1.5"
          >
            <Play className="h-3.5 w-3.5" />
            Simulate
          </button>
        </div>

        {simResult && (
          <div className="mt-3 p-3 rounded-lg border bg-white dark:bg-sg-neutral-900 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-sg-neutral-700 dark:text-sg-neutral-300">
                  Decision:
                </span>
                {simResult.outcome === 'ALLOW' && (
                  <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-bold">
                    <ShieldCheck className="h-4 w-4" />
                    ALLOW (Permitted by policy)
                  </span>
                )}
                {simResult.outcome === 'DENY' && (
                  <span className="flex items-center gap-1 text-rose-600 dark:text-rose-400 font-bold">
                    <ShieldAlert className="h-4 w-4" />
                    DENY (Violation blocked)
                  </span>
                )}
                {simResult.outcome === 'REQUIRE_HUMAN_QUORUM' && (
                  <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-bold">
                    <KeyRound className="h-4 w-4" />
                    QUARANTINE (Human Quorum Signature Required)
                  </span>
                )}
              </div>
              <p className="text-[11px] text-sg-neutral-500 dark:text-sg-neutral-400">
                {simResult.matchedRule
                  ? `Matched Rule [${simResult.matchedRule.name}]: ${simResult.matchedRule.reason}`
                  : 'No explicit matching rule found — evaluated to default closed policy (DENY).'}
              </p>
            </div>
            <span className="font-mono text-[10px] text-sg-neutral-400 shrink-0">
              Evaluated at {simResult.evaluatedAt}
            </span>
          </div>
        )}
      </div>

      {/* Rules Manager & Table */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sliders className="h-4 w-4 text-sg-neutral-600 dark:text-sg-neutral-300" />
            <h3 className="text-sm font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
              Active Constraint Rules ({rules.length})
            </h3>
          </div>

          <button
            type="button"
            onClick={() => setIsAmending(!isAmending)}
            className="flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
          >
            <Plus className="h-3.5 w-3.5" />
            {isAmending ? 'Close Rule Builder' : 'Propose New Rule'}
          </button>
        </div>

        {/* New Rule Proposal Drawer */}
        {isAmending && (
          <form
            onSubmit={handleAddRule}
            className="rounded-lg border border-indigo-200 dark:border-indigo-900 bg-indigo-50/30 dark:bg-indigo-950/10 p-4 space-y-3"
          >
            <h4 className="text-xs font-semibold text-indigo-950 dark:text-indigo-200">
              Draft Sovereign Rule Proposal
            </h4>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <input
                type="text"
                placeholder="Rule Name (e.g. Block External SSH)"
                value={newRuleName}
                onChange={(e) => setNewRuleName(e.target.value)}
                required
                className="rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs text-sg-neutral-800 dark:text-sg-neutral-200"
              />

              <select
                value={newRuleAction}
                onChange={(e) => setNewRuleAction(e.target.value as PolicyActionKind)}
                className="rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
              >
                <option value="fs_write">fs_write</option>
                <option value="fs_read">fs_read</option>
                <option value="network_outbound">network_outbound</option>
                <option value="ast_mutate">ast_mutate</option>
                <option value="mcp_invoke">mcp_invoke</option>
                <option value="shell_exec">shell_exec</option>
              </select>

              <select
                value={newRuleEnforcement}
                onChange={(e) => setNewRuleEnforcement(e.target.value as PolicyEnforcement)}
                className="rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-semibold text-sg-neutral-800 dark:text-sg-neutral-200"
              >
                <option value="ALLOW">ALLOW</option>
                <option value="DENY">DENY</option>
                <option value="REQUIRE_HUMAN_QUORUM">REQUIRE_HUMAN_QUORUM</option>
              </select>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <input
                type="text"
                placeholder="Resource Regex Pattern (e.g. ^/tmp/.*)"
                value={newRulePattern}
                onChange={(e) => setNewRulePattern(e.target.value)}
                required
                className="rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs font-mono text-sg-neutral-800 dark:text-sg-neutral-200"
              />

              <input
                type="text"
                placeholder="Justification & Audit Rationale"
                value={newRuleReason}
                onChange={(e) => setNewRuleReason(e.target.value)}
                className="rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 bg-white dark:bg-sg-neutral-900 px-3 py-1.5 text-xs text-sg-neutral-800 dark:text-sg-neutral-200"
              />
            </div>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setIsAmending(false)}
                className="px-3 py-1 text-xs text-sg-neutral-500 hover:text-sg-neutral-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs transition"
              >
                Add Rule to Ledger
              </button>
            </div>
          </form>
        )}

        {/* Rule List Table */}
        <div className="overflow-x-auto rounded-lg border border-sg-neutral-200 dark:border-sg-neutral-800">
          <table className="w-full text-left text-xs">
            <thead className="bg-sg-neutral-100 dark:bg-sg-neutral-950 text-sg-neutral-500 uppercase tracking-wider text-[10px]">
              <tr>
                <th className="px-4 py-2.5 font-medium">Status</th>
                <th className="px-4 py-2.5 font-medium">Rule Name</th>
                <th className="px-4 py-2.5 font-medium">Action Kind</th>
                <th className="px-4 py-2.5 font-medium">Resource Pattern</th>
                <th className="px-4 py-2.5 font-medium">Enforcement</th>
                <th className="px-4 py-2.5 font-medium">Rationale</th>
                <th className="px-4 py-2.5 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-sg-neutral-200 dark:divide-sg-neutral-800 bg-white dark:bg-sg-neutral-900">
              {rules.map((rule) => (
                <tr
                  key={rule.id}
                  className={`hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-800/40 transition ${
                    !rule.enabled ? 'opacity-40' : ''
                  }`}
                >
                  <td className="px-4 py-3">
                    <input
                      type="checkbox"
                      checked={rule.enabled}
                      onChange={() => handleToggleRule(rule.id)}
                      className="rounded border-sg-neutral-400 accent-indigo-500"
                    />
                  </td>
                  <td className="px-4 py-3 font-semibold text-sg-neutral-900 dark:text-sg-neutral-100">
                    {rule.name}
                  </td>
                  <td className="px-4 py-3 font-mono text-indigo-600 dark:text-indigo-400">
                    {rule.actionKind}
                  </td>
                  <td className="px-4 py-3 font-mono text-sg-neutral-700 dark:text-sg-neutral-300">
                    <code className="bg-sg-neutral-100 dark:bg-sg-neutral-800 px-1.5 py-0.5 rounded">
                      {rule.resourcePattern}
                    </code>
                  </td>
                  <td className="px-4 py-3">
                    {rule.enforcement === 'ALLOW' && (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                        ALLOW
                      </span>
                    )}
                    {rule.enforcement === 'DENY' && (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                        DENY
                      </span>
                    )}
                    {rule.enforcement === 'REQUIRE_HUMAN_QUORUM' && (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                        QUORUM_REQUIRED
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sg-neutral-500 dark:text-sg-neutral-400 max-w-xs truncate" title={rule.reason}>
                    {rule.reason}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => handleDeleteRule(rule.id)}
                      className="p-1 text-sg-neutral-400 hover:text-rose-500 transition"
                      title="Delete Rule"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * CommandTaxonomyPalette (CMD-PAL-004)
 *
 * 4-Tier Trie Command Palette & Cheatsheet for ATROPOS Web.
 * Mirrors HierarchicalCommandTaxonomy across:
 * Tier 1: Domain (repo, factory, agent, memory, governance, toolchain, system)
 * Tier 2: Action
 * Tier 3: Target
 * Tier 4: Deterministic Explanation String
 */

'use client';

import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  Search,
  ChevronRight,
  HelpCircle,
  X,
  Terminal,
  FolderGit2,
  Cpu,
  Bot,
  Database,
  ShieldCheck,
  Wrench,
  Sliders,
} from 'lucide-react';

export interface TaxonomyItem {
  domain: string;
  action: string;
  target: string;
  leafResolution: string;
}

const DEFAULT_TAXONOMY_DATA: TaxonomyItem[] = [
  // repo
  {
    domain: 'repo',
    action: 'rollback',
    target: '<hash>',
    leafResolution: 'Rolls back repository workspace to commit <hash> with CAS baseline invalidation',
  },
  {
    domain: 'repo',
    action: 'status',
    target: 'workspace',
    leafResolution: 'Displays working tree state and uncommitted file modifications',
  },
  {
    domain: 'repo',
    action: 'diff',
    target: 'staged',
    leafResolution: 'Computes AST-level diff against authoritative content-addressed storage',
  },
  {
    domain: 'repo',
    action: 'commit',
    target: '<message>',
    leafResolution: 'Records deterministic immutable tree commit into CAS receipt ledger',
  },
  // factory
  {
    domain: 'factory',
    action: 'generate',
    target: '<spec>',
    leafResolution: 'Synthesizes multi-tier application architecture adhering to formal contracts',
  },
  {
    domain: 'factory',
    action: 'repair',
    target: '<error>',
    leafResolution: 'Invokes Sentry repair coordinator to synthesize corrective mutations',
  },
  {
    domain: 'factory',
    action: 'resume',
    target: '<run_id>',
    leafResolution: 'Restores speculative synthesis wave from persistent event journal',
  },
  {
    domain: 'factory',
    action: 'audit',
    target: 'loc',
    leafResolution: 'Enforces strict 500-LOC ceiling across all synthesized source files',
  },
  // agent
  {
    domain: 'agent',
    action: 'run',
    target: '<goal>',
    leafResolution: 'Launches autonomous multi-turn goal execution within attenuated sandbox',
  },
  {
    domain: 'agent',
    action: 'status',
    target: 'active',
    leafResolution: 'Queries live execution status and active concurrency lane metrics',
  },
  {
    domain: 'agent',
    action: 'cancel',
    target: '<task_id>',
    leafResolution: 'Terminates active synthesis lane and purges speculative workspaces',
  },
  // memory
  {
    domain: 'memory',
    action: 'query',
    target: '<key>',
    leafResolution: 'Retrieves stored AST symbols and learned conflict lemmas from CAS',
  },
  {
    domain: 'memory',
    action: 'prune',
    target: 'stale',
    leafResolution: 'Removes unreferenced ephemeral hypothesis nodes from constraint store',
  },
  // governance
  {
    domain: 'governance',
    action: 'attest',
    target: 'agents.md',
    leafResolution: 'Validates cryptographic root signature of authoritative AGENTS.md ledger',
  },
  {
    domain: 'governance',
    action: 'verify',
    target: 'cas',
    leafResolution: 'Calculates zero-entropy Merkle root over physical workspace bytes',
  },
  // toolchain
  {
    domain: 'toolchain',
    action: 'detect',
    target: 'native',
    leafResolution: 'Scans system PATH via PlatformAdapterRegistry for portable compilers',
  },
  {
    domain: 'toolchain',
    action: 'doctor',
    target: 'env',
    leafResolution: 'Verifies cross-platform toolchain isolation and memory limits',
  },
  // system
  {
    domain: 'system',
    action: 'grep',
    target: '<pattern>',
    leafResolution: 'Searches indexed repository content using bounded regex pattern',
  },
  {
    domain: 'system',
    action: 'undo',
    target: '<step>',
    leafResolution: 'Reverts the last speculative workspace mutation step',
  },
  {
    domain: 'system',
    action: 'alias',
    target: '<name>',
    leafResolution: 'Defines or inspects user command shortcut aliases',
  },
  {
    domain: 'system',
    action: 'history',
    target: '<limit>',
    leafResolution: 'Displays chronological command and execution history',
  },
  {
    domain: 'system',
    action: 'config',
    target: '<key>',
    leafResolution: 'Inspects or updates runtime configuration key-value store',
  },
  {
    domain: 'system',
    action: 'clear',
    target: 'screen',
    leafResolution: 'Clears active viewport buffer and resets cursor position',
  },
];

export interface CommandTaxonomyPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectCommand?: (commandString: string) => void;
  initialQuery?: string;
}

export function CommandTaxonomyPalette({
  isOpen,
  onClose,
  onSelectCommand,
  initialQuery = '',
}: CommandTaxonomyPaletteProps) {
  const [query, setQuery] = useState(initialQuery);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [showCheatsheet, setShowCheatsheet] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setSelectedIndex(0);
      setShowCheatsheet(false);
    }
  }, [isOpen]);

  const filteredItems = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return DEFAULT_TAXONOMY_DATA;

    return DEFAULT_TAXONOMY_DATA.filter((item) => {
      const fullCmd = `/${item.domain} ${item.action} ${item.target}`.toLowerCase();
      return (
        fullCmd.includes(q) ||
        item.leafResolution.toLowerCase().includes(q) ||
        item.domain.toLowerCase().includes(q) ||
        item.action.toLowerCase().includes(q)
      );
    });
  }, [query]);

  // Keep selection bounded
  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % Math.max(1, filteredItems.length));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + filteredItems.length) % Math.max(1, filteredItems.length));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const selected = filteredItems[selectedIndex];
      if (selected && onSelectCommand) {
        onSelectCommand(`/${selected.domain} ${selected.action} ${selected.target}`);
        onClose();
      }
    } else if (e.key === '?' && !query) {
      e.preventDefault();
      setShowCheatsheet((s) => !s);
    }
  };

  const getDomainIcon = (domain: string) => {
    switch (domain) {
      case 'repo':
        return <FolderGit2 className="w-4 h-4 text-emerald-400" />;
      case 'factory':
        return <Cpu className="w-4 h-4 text-amber-400" />;
      case 'agent':
        return <Bot className="w-4 h-4 text-indigo-400" />;
      case 'memory':
        return <Database className="w-4 h-4 text-purple-400" />;
      case 'governance':
        return <ShieldCheck className="w-4 h-4 text-teal-400" />;
      case 'toolchain':
        return <Wrench className="w-4 h-4 text-orange-400" />;
      case 'system':
      default:
        return <Sliders className="w-4 h-4 text-cyan-400" />;
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 bg-black/70 backdrop-blur-sm">
      <div className="w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-xl shadow-2xl overflow-hidden text-slate-100 flex flex-col max-h-[75vh]">
        {/* Search Header */}
        <div className="flex items-center px-4 py-3 border-b border-slate-800 bg-slate-950">
          <Search className="w-5 h-5 text-slate-400 mr-2 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type command prefix (e.g. /repo rollback) or press '?' for cheatsheet..."
            className="w-full bg-transparent text-sm text-slate-100 outline-none placeholder:text-slate-500 font-mono"
            aria-label="Command search query"
          />
          <button
            onClick={() => setShowCheatsheet((s) => !s)}
            className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 ml-2"
            title="Toggle Cheatsheet"
          >
            <HelpCircle className="w-4 h-4" />
          </button>
          <button
            onClick={onClose}
            className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 ml-1"
            title="Close Palette"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Cheatsheet View */}
        {showCheatsheet ? (
          <div className="p-4 overflow-y-auto max-h-[55vh] text-xs font-mono space-y-4 bg-slate-950/50">
            <div className="text-sm font-semibold text-indigo-300 border-b border-slate-800 pb-2">
              4-Tier Trie Command Taxonomy Cheatsheet
            </div>
            {['repo', 'factory', 'agent', 'memory', 'governance', 'toolchain', 'system'].map(
              (domain) => {
                const domainItems = DEFAULT_TAXONOMY_DATA.filter((i) => i.domain === domain);
                return (
                  <div key={domain} className="space-y-1">
                    <div className="flex items-center space-x-1.5 text-slate-300 font-bold uppercase text-[11px]">
                      {getDomainIcon(domain)}
                      <span>/{domain}</span>
                    </div>
                    <div className="pl-5 space-y-1">
                      {domainItems.map((item) => (
                        <div key={`${item.action}-${item.target}`} className="text-slate-400">
                          <span className="text-emerald-400">-> {item.action}</span>{' '}
                          <span className="text-cyan-400">{item.target}</span>: {item.leafResolution}
                        </div>
                      ))}
                    </div>
                  </div>
                );
              }
            )}
          </div>
        ) : (
          /* Command List View */
          <div className="flex-1 overflow-y-auto p-2 space-y-1 max-h-[55vh]">
            {filteredItems.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-500">
                No matching taxonomy commands found.
              </div>
            ) : (
              filteredItems.map((item, idx) => {
                const isSelected = idx === selectedIndex;
                return (
                  <div
                    key={`${item.domain}-${item.action}-${item.target}`}
                    onClick={() => {
                      if (onSelectCommand) {
                        onSelectCommand(`/${item.domain} ${item.action} ${item.target}`);
                        onClose();
                      }
                    }}
                    className={`flex items-start justify-between p-2.5 rounded-lg cursor-pointer transition-colors ${
                      isSelected
                        ? 'bg-indigo-950/60 border border-indigo-700/60 text-slate-100'
                        : 'hover:bg-slate-800/50 text-slate-300 border border-transparent'
                    }`}
                  >
                    <div className="flex items-start space-x-2.5">
                      <div className="mt-0.5">{getDomainIcon(item.domain)}</div>
                      <div>
                        <div className="font-mono text-xs font-semibold flex items-center space-x-1">
                          <span className="text-slate-400">/{item.domain}</span>
                          <ChevronRight className="w-3 h-3 text-slate-600" />
                          <span className="text-emerald-300">{item.action}</span>
                          <span className="text-cyan-300">{item.target}</span>
                        </div>
                        <div className="text-[11px] text-slate-400 mt-0.5 leading-snug">
                          {item.leafResolution}
                        </div>
                      </div>
                    </div>
                    <span className="text-[10px] font-mono text-slate-500 uppercase px-1.5 py-0.5 bg-slate-800 rounded">
                      Tier 4 Leaf
                    </span>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* Footer Hint Bar */}
        <div className="px-4 py-2 border-t border-slate-800 bg-slate-950 text-[11px] text-slate-500 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <span>↑↓ Navigate</span>
            <span>↵ Select</span>
            <span>? Cheatsheet</span>
            <span>Esc Dismiss</span>
          </div>
          <span className="font-mono">{filteredItems.length} commands</span>
        </div>
      </div>
    </div>
  );
}

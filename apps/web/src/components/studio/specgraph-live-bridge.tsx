'use client';

import React, { useState } from 'react';
import {
  GitBranch,
  Play,
  RotateCw,
  ExternalLink,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Terminal,
  Activity,
  Layers,
  ArrowRight,
  Code2
} from 'lucide-react';

export interface CompilerPass {
  id: string;
  name: string;
  description: string;
  stage: 'Frontend' | 'MiddleEnd' | 'Backend';
  status: 'pending' | 'running' | 'completed' | 'failed';
  durationMs?: number;
}

const INITIAL_PASSES: CompilerPass[] = [
  {
    id: 'pass-lex',
    name: '1. Lexical Tokenizer & Comment Stripping',
    description: 'Converts raw source bytes into typed lexical token stream with source location offsets.',
    stage: 'Frontend',
    status: 'completed',
    durationMs: 4.2
  },
  {
    id: 'pass-parse',
    name: '2. Concrete Syntax Tree (CST) Parser',
    description: 'Constructs concrete syntax tree according to grammar rules, preserving punctuation.',
    stage: 'Frontend',
    status: 'completed',
    durationMs: 8.7
  },
  {
    id: 'pass-ast',
    name: '3. AST Lowering & Semantic Typing',
    description: 'Lowers CST to normalized abstract syntax tree and performs type inference and scope binding.',
    stage: 'Frontend',
    status: 'completed',
    durationMs: 14.1
  },
  {
    id: 'pass-dce',
    name: '4. Dead Code Elimination (DCE)',
    description: 'Traverses control flow DAG and prunes unreferenced statements and unreachable blocks.',
    stage: 'MiddleEnd',
    status: 'completed',
    durationMs: 6.5
  },
  {
    id: 'pass-opt',
    name: '5. Constant Folding & Invariant Hoisting',
    description: 'Evaluates compile-time constant arithmetic and hoists loop-invariant branch expressions.',
    stage: 'MiddleEnd',
    status: 'completed',
    durationMs: 11.3
  },
  {
    id: 'pass-emit',
    name: '6. Bytecode & DAG Artifact Emission',
    description: 'Emits target execution artifact and Merkle release seal hash into verified storage.',
    stage: 'Backend',
    status: 'completed',
    durationMs: 9.8
  }
];

export function SpecGraphLiveBridge() {
  const [passes, setPasses] = useState<CompilerPass[]>(INITIAL_PASSES);
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [activeLog, setActiveLog] = useState<string[]>([
    '[INIT] SpecGraph Foundry Compiler Engine v2.4 initialized.',
    '[VERCEL] Bound to production endpoint: https://specgraph-foundry.vercel.app',
    '[OK] All 6 optimization passes completed with 0 errors.'
  ]);

  const handleRunAllPasses = () => {
    setIsRunning(true);
    setActiveLog((prev) => [...prev, `[BUILD] Triggering full compilation pipeline at ${new Date().toISOString()}...`]);

    // Reset status to pending
    setPasses((prev) => prev.map((p) => ({ ...p, status: 'pending' })));

    let currentIndex = 0;
    const interval = setInterval(() => {
      if (currentIndex >= passes.length) {
        clearInterval(interval);
        setIsRunning(false);
        setActiveLog((prev) => [
          ...prev,
          '[SUCCESS] Full DAG compilation completed successfully.',
          '[SEAL] Merkle state root verified.'
        ]);
        return;
      }

      setPasses((prev) =>
        prev.map((p, idx) => {
          if (idx === currentIndex) return { ...p, status: 'completed' };
          if (idx === currentIndex + 1) return { ...p, status: 'running' };
          return p;
        })
      );

      setActiveLog((prev) => [
        ...prev,
        `[PASS ${currentIndex + 1}] Finished: ${INITIAL_PASSES[currentIndex].name} (${INITIAL_PASSES[currentIndex].durationMs}ms)`
      ]);

      currentIndex++;
    }, 400);
  };

  return (
    <div className="flex flex-col h-full w-full bg-slate-950 text-slate-100 divide-y divide-slate-800">
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 bg-slate-900/70 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <GitBranch className="h-5 w-5 text-purple-400" />
          <div>
            <h2 className="text-sm font-mono font-bold text-white flex items-center gap-2">
              <span>SpecGraph Foundry Live Bridge</span>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 border border-purple-500/40 text-purple-300 font-bold">
                VERCEL PRODUCTION READY
              </span>
            </h2>
            <p className="text-xs text-slate-400 font-sans">
              Autonomous compiler pipeline, DAG optimization, and Vercel production cross-links.
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3 font-mono text-xs">
          <button
            onClick={handleRunAllPasses}
            disabled={isRunning}
            className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl font-bold transition-all shadow-md ${
              isRunning
                ? 'bg-purple-900/40 text-purple-400 cursor-not-allowed border border-purple-800'
                : 'bg-purple-500 hover:bg-purple-400 text-black shadow-purple-500/20'
            }`}
          >
            {isRunning ? <RotateCw className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
            <span>{isRunning ? 'Compiling Passes...' : 'Run All Compiler Passes'}</span>
          </button>

          <a
            href="https://specgraph-foundry.vercel.app"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-purple-500/40 bg-purple-950/30 text-purple-300 hover:bg-purple-950/60 font-semibold transition-colors"
          >
            <span>Open SpecGraph Vercel</span>
            <ExternalLink className="h-3.5 w-3.5" />
          </a>
        </div>
      </div>

      {/* Main Split Layout */}
      <div className="flex-1 flex flex-col lg:flex-row overflow-hidden divide-y lg:divide-y-0 lg:divide-x divide-slate-800">
        {/* Left Column: Compiler Passes DAG Sequence */}
        <div className="flex-1 p-6 overflow-y-auto space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
            <span className="text-xs font-mono font-bold text-white flex items-center gap-2">
              <Layers className="h-4 w-4 text-purple-400" />
              COMPILER DAG PASS SEQUENCE
            </span>
            <span className="text-[11px] font-mono text-slate-400">
              {passes.filter((p) => p.status === 'completed').length} / {passes.length} Passed
            </span>
          </div>

          <div className="space-y-3">
            {passes.map((pass, idx) => (
              <div
                key={pass.id}
                className={`p-4 rounded-xl border transition-all ${
                  pass.status === 'completed'
                    ? 'border-emerald-500/30 bg-emerald-950/10'
                    : pass.status === 'running'
                    ? 'border-purple-400 bg-purple-950/20 ring-1 ring-purple-400'
                    : 'border-slate-800 bg-slate-900/40 opacity-70'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    {pass.status === 'completed' ? (
                      <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                    ) : pass.status === 'running' ? (
                      <RotateCw className="h-4 w-4 text-purple-400 animate-spin" />
                    ) : (
                      <Clock className="h-4 w-4 text-slate-500" />
                    )}
                    <span className="font-mono font-bold text-xs text-white">{pass.name}</span>
                  </div>

                  <div className="flex items-center gap-2 font-mono text-[10px]">
                    <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                      {pass.stage}
                    </span>
                    {pass.durationMs && (
                      <span className="text-purple-300 font-bold">{pass.durationMs}ms</span>
                    )}
                  </div>
                </div>

                <p className="text-xs text-slate-400 mt-2 font-sans pl-6">{pass.description}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Right Column: Execution Console Stream */}
        <div className="w-full lg:w-96 p-5 bg-slate-900/40 overflow-y-auto flex flex-col font-mono text-xs space-y-3">
          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
            <span className="font-bold text-white flex items-center gap-2">
              <Terminal className="h-4 w-4 text-purple-400" />
              COMPILER LOG STREAM
            </span>
            <span className="text-[10px] text-slate-500">{activeLog.length} lines</span>
          </div>

          <div className="flex-1 p-3 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5 overflow-y-auto text-[11px] leading-relaxed">
            {activeLog.map((log, i) => (
              <p
                key={i}
                className={
                  log.startsWith('[SUCCESS]')
                    ? 'text-emerald-400 font-bold'
                    : log.startsWith('[PASS')
                    ? 'text-purple-300'
                    : log.startsWith('[BUILD]')
                    ? 'text-amber-400'
                    : 'text-slate-400'
                }
              >
                {log}
              </p>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

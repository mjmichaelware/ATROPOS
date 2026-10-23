/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useRef, useEffect } from 'react';
import {
  Send,
  Square,
  Bot,
  User,
  Terminal,
  Cpu,
  Layers,
  Sparkles,
  ChevronDown,
  ChevronRight,
  ShieldAlert,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  AlertTriangle,
  RotateCcw,
  Sliders,
  Clock,
  ArrowRight
} from 'lucide-react';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';

export interface WorkstationMessage {
  id: string;
  sender: 'user' | 'agent' | 'system';
  content: string;
  thinking?: string;
  isThinkingExpanded?: boolean;
  toolCalls?: Array<{
    id: string;
    toolName: string;
    input: Record<string, unknown>;
    output?: string;
    status: 'running' | 'success' | 'failed' | 'pending_approval';
    durationMs?: number;
  }>;
  tokenCount?: {
    prompt: number;
    completion: number;
    thinking: number;
  };
  timestamp: string;
}

export interface SovereignConversationWorkstationProps {
  projectId: string;
  projectName?: string;
  initialMessages?: WorkstationMessage[];
  onSendMessage?: (content: string, options?: { provider?: string; model?: string }) => Promise<void>;
  onApproveTool?: (toolCallId: string, approve: boolean) => Promise<void>;
  onTriggerSlashPalette?: () => void;
}

const DEFAULT_ANSWERS: SixAnswer = {
  objective: 'Execute autonomous software engineering tasks with formal contract verification.',
  currentOperation: 'Standing by for instructions or goal synthesis.',
  reasoning: 'Autonomous execution operates with immutable Merkle ledger audit trails and verification checkpoints.',
  progress: {
    percent: 100,
    stage: 'Awaiting Operator Prompt'
  },
  nextAction: 'Type / to explore available command categories or prompt directly.'
};

const PROVIDERS = [
  { id: 'primary', name: 'Frontier Neural Engine Alpha', model: 'alpha-3.7-sonnet', latency: '42ms' },
  { id: 'omega', name: 'Frontier Neural Engine Omega', model: 'omega-4o-hyper', latency: '65ms' },
  { id: 'gemini', name: 'Gemini 1.5 Pro Sovereign', model: 'gemini-1.5-pro', latency: '38ms' },
  { id: 'groq', name: 'Groq Llama-3.3 Ultra-Speed', model: 'llama-3.3-70b-versatile', latency: '12ms' },
  { id: 'deepseek', name: 'DeepSeek R1 Reasoner', model: 'deepseek-reasoner-v3', latency: '88ms' },
  { id: 'ollama', name: 'Local Ollama Offline Hub', model: 'qwen2.5-coder-32b', latency: '5ms' }
];

export function SovereignConversationWorkstation({
  projectId,
  projectName = 'Current Workspace',
  initialMessages = [],
  onSendMessage,
  onApproveTool,
  onTriggerSlashPalette
}: SovereignConversationWorkstationProps) {
  const [messages, setMessages] = useState<WorkstationMessage[]>(() => {
    if (initialMessages.length > 0) return initialMessages;
    return [
      {
        id: 'msg-init-0',
        sender: 'system',
        content: `Sovereign Autonomous Workstation initialized for project **${projectName}** (\`${projectId}\`). All actions are gated by Three Laws of Robotics and verified against cryptographic Merkle logs.`,
        timestamp: new Date().toLocaleTimeString()
      },
      {
        id: 'msg-init-1',
        sender: 'agent',
        content: 'Ready to assist with codebase exploration, test compilation, AST self-healing, refactoring, and benchmark execution. Press **/** to inspect all command categories, or describe your engineering goal.',
        thinking: 'Initializing sovereign neural session. Verifying repository invariants, Git worktree cleanliness, and MCP server health checks. Six Answers baseline active.',
        isThinkingExpanded: false,
        timestamp: new Date().toLocaleTimeString(),
        tokenCount: { prompt: 142, completion: 48, thinking: 31 }
      }
    ];
  });

  const [inputPrompt, setInputPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [activeProvider, setActiveProvider] = useState(PROVIDERS[0].id);
  const [showConfig, setShowConfig] = useState(false);
  const [temperature, setTemperature] = useState(0.2);
  const [maxThinkingTokens, setMaxThinkingTokens] = useState(16384);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isGenerating]);

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const toggleThinking = (messageId: string) => {
    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === messageId ? { ...msg, isThinkingExpanded: !msg.isThinkingExpanded } : msg
      )
    );
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    } else if (e.key === '/' && inputPrompt.trim() === '') {
      if (onTriggerSlashPalette) {
        onTriggerSlashPalette();
      }
    }
  };

  const handleSend = async () => {
    const trimmed = inputPrompt.trim();
    if (!trimmed || isGenerating) return;

    const userMsg: WorkstationMessage = {
      id: `usr-${Date.now()}`,
      sender: 'user',
      content: trimmed,
      timestamp: new Date().toLocaleTimeString()
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputPrompt('');
    setIsGenerating(true);

    if (onSendMessage) {
      try {
        await onSendMessage(trimmed, { provider: activeProvider });
      } catch (err) {
        setMessages((prev) => [
          ...prev,
          {
            id: `err-${Date.now()}`,
            sender: 'system',
            content: `**Execution Error**: ${err instanceof Error ? err.message : String(err)}`,
            timestamp: new Date().toLocaleTimeString()
          }
        ]);
      } finally {
        setIsGenerating(false);
      }
    } else {
      // Mock sovereign neural execution loop
      setTimeout(() => {
        const agentMsg: WorkstationMessage = {
          id: `agt-${Date.now()}`,
          sender: 'agent',
          content: `Analyzed goal: "${trimmed}". Executing through sovereign reasoning loop. All contracts verified against local AST signatures.`,
          thinking: `Deconstructing prompt: "${trimmed}".\n1. Resolving affected workspace files.\n2. Checking Merkle tree integrity.\n3. Formulating hypothesis and test matrix.\n4. Compiling verification gate.\nResult: Zero violations detected. Safe to apply changes.`,
          isThinkingExpanded: true,
          toolCalls: [
            {
              id: `tool-${Date.now()}`,
              toolName: 'ast_pattern_search',
              input: { query: trimmed, scope: 'src/main/kotlin/' },
              output: 'Found 3 candidate symbols matching pattern. No breaking ABI changes detected.',
              status: 'success',
              durationMs: 34
            }
          ],
          tokenCount: { prompt: 280, completion: 94, thinking: 112 },
          timestamp: new Date().toLocaleTimeString()
        };
        setMessages((prev) => [...prev, agentMsg]);
        setIsGenerating(false);
      }, 900);
    }
  };

  const handleApprove = async (toolId: string, approved: boolean) => {
    setMessages((prev) =>
      prev.map((msg) => {
        if (!msg.toolCalls) return msg;
        return {
          ...msg,
          toolCalls: msg.toolCalls.map((tc) =>
            tc.id === toolId
              ? { ...tc, status: approved ? 'success' : 'failed', output: approved ? 'Operator approved tool execution.' : 'Rejected by operator approval gate.' }
              : tc
          )
        };
      })
    );
    if (onApproveTool) {
      await onApproveTool(toolId, approved);
    }
  };

  return (
    <div className="flex flex-col h-full bg-slate-950 text-slate-100 font-sans border border-slate-800 rounded-xl overflow-hidden shadow-2xl">
      {/* Top Cyber-HUD Six Answers Banner */}
      <div className="bg-slate-900/90 border-b border-slate-800 backdrop-blur-md p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-3">
            <div className="w-3 h-3 rounded-full bg-emerald-500 animate-pulse" />
            <span className="font-mono text-xs font-semibold uppercase tracking-wider text-emerald-400">
              Sovereign Autonomous Workstation
            </span>
            <span className="text-xs text-slate-500">|</span>
            <span className="text-xs font-medium text-slate-300">{projectName}</span>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={activeProvider}
              onChange={(e) => setActiveProvider(e.target.value)}
              className="bg-slate-800 border border-slate-700 text-xs font-mono text-cyan-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-cyan-500"
            >
              {PROVIDERS.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.latency})
                </option>
              ))}
            </select>

            <button
              onClick={() => setShowConfig(!showConfig)}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-colors"
              title="Execution Parameters"
            >
              <Sliders className="w-4 h-4" />
            </button>
          </div>
        </div>

        <SixAnswersPanel answers={DEFAULT_ANSWERS} compact={true} expandable={true} />
      </div>

      {/* Configuration Drawer */}
      {showConfig && (
        <div className="bg-slate-900/95 border-b border-slate-800 p-4 grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-mono animate-in slide-in-from-top duration-150">
          <div>
            <label className="block text-slate-400 mb-1">Temperature: {temperature}</label>
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
            <label className="block text-slate-400 mb-1">Max Thinking Tokens: {maxThinkingTokens}</label>
            <input
              type="range"
              min="1024"
              max="32768"
              step="1024"
              value={maxThinkingTokens}
              onChange={(e) => setMaxThinkingTokens(parseInt(e.target.value))}
              className="w-full accent-cyan-500"
            />
          </div>
          <div className="flex items-end justify-between">
            <span className="text-slate-400">Merkle Attestation: Active</span>
            <span className="text-emerald-400 font-semibold">Gated Safe Mode</span>
          </div>
        </div>
      )}

      {/* Message Timeline */}
      <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-6">
        {messages.map((msg) => (
          <div key={msg.id} className="group flex flex-col space-y-2">
            <div className="flex items-center justify-between text-xs text-slate-400">
              <div className="flex items-center gap-2">
                {msg.sender === 'user' ? (
                  <div className="w-6 h-6 rounded-full bg-cyan-600/30 text-cyan-300 flex items-center justify-center font-bold">
                    <User className="w-3.5 h-3.5" />
                  </div>
                ) : msg.sender === 'agent' ? (
                  <div className="w-6 h-6 rounded-full bg-emerald-600/30 text-emerald-300 flex items-center justify-center font-bold">
                    <Bot className="w-3.5 h-3.5" />
                  </div>
                ) : (
                  <div className="w-6 h-6 rounded-full bg-amber-600/30 text-amber-300 flex items-center justify-center font-bold">
                    <Terminal className="w-3.5 h-3.5" />
                  </div>
                )}
                <span className="font-semibold text-slate-200 uppercase tracking-wide">
                  {msg.sender === 'user' ? 'Operator' : msg.sender === 'agent' ? 'Sovereign Agent' : 'System Guard'}
                </span>
                <span className="text-slate-600 font-mono">{msg.timestamp}</span>
              </div>

              <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                {msg.tokenCount && (
                  <span className="font-mono text-slate-500">
                    {msg.tokenCount.prompt + msg.tokenCount.completion + (msg.tokenCount.thinking || 0)} tok
                  </span>
                )}
                <button
                  onClick={() => handleCopy(msg.id, msg.content)}
                  className="p-1 text-slate-400 hover:text-slate-200 rounded"
                  title="Copy message"
                >
                  {copiedId === msg.id ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>

            {/* Collapsible <thinking> Drawer */}
            {msg.thinking && (
              <div className="border border-purple-500/20 bg-purple-950/20 rounded-lg overflow-hidden ml-8">
                <button
                  onClick={() => toggleThinking(msg.id)}
                  className="w-full flex items-center justify-between px-3 py-2 text-xs font-mono text-purple-300 hover:bg-purple-900/30 transition-colors"
                >
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-3.5 h-3.5 text-purple-400" />
                    <span>Neural Reasoning Chain & Hypothesis</span>
                    {msg.tokenCount?.thinking && (
                      <span className="text-purple-400/60">({msg.tokenCount.thinking} tokens)</span>
                    )}
                  </div>
                  {msg.isThinkingExpanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                </button>

                {msg.isThinkingExpanded && (
                  <div className="p-3 text-xs font-mono text-purple-200/90 whitespace-pre-wrap border-t border-purple-500/20 bg-slate-950/60">
                    {msg.thinking}
                  </div>
                )}
              </div>
            )}

            {/* Message Body */}
            <div
              className={`ml-8 p-4 rounded-xl text-sm leading-relaxed ${
                msg.sender === 'user'
                  ? 'bg-cyan-950/20 border border-cyan-800/40 text-slate-100'
                  : msg.sender === 'agent'
                  ? 'bg-slate-900/80 border border-slate-800 text-slate-200'
                  : 'bg-amber-950/20 border border-amber-800/40 text-amber-200'
              }`}
            >
              <div className="whitespace-pre-wrap">{msg.content}</div>
            </div>

            {/* Tool Calls Drawer */}
            {msg.toolCalls && msg.toolCalls.length > 0 && (
              <div className="ml-8 space-y-2">
                {msg.toolCalls.map((tc) => (
                  <div
                    key={tc.id}
                    className="border border-slate-800 bg-slate-900/90 rounded-lg p-3 text-xs font-mono space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                        <span className="font-semibold text-slate-200">{tc.toolName}</span>
                        {tc.durationMs && <span className="text-slate-500">({tc.durationMs}ms)</span>}
                      </div>

                      <div className="flex items-center gap-2">
                        {tc.status === 'success' && (
                          <span className="inline-flex items-center gap-1 text-emerald-400">
                            <CheckCircle2 className="w-3.5 h-3.5" /> executed
                          </span>
                        )}
                        {tc.status === 'failed' && (
                          <span className="inline-flex items-center gap-1 text-rose-400">
                            <XCircle className="w-3.5 h-3.5" /> failed
                          </span>
                        )}
                        {tc.status === 'pending_approval' && (
                          <span className="inline-flex items-center gap-1 text-amber-400 animate-pulse">
                            <AlertTriangle className="w-3.5 h-3.5" /> approval required
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="bg-slate-950 p-2 rounded text-slate-400 overflow-x-auto">
                      <code>{JSON.stringify(tc.input, null, 2)}</code>
                    </div>

                    {tc.output && (
                      <div className="bg-slate-950/80 p-2 rounded border border-slate-800 text-slate-300 whitespace-pre-wrap">
                        {tc.output}
                      </div>
                    )}

                    {tc.status === 'pending_approval' && (
                      <div className="flex items-center justify-end gap-2 pt-1">
                        <button
                          onClick={() => handleApprove(tc.id, false)}
                          className="px-3 py-1 bg-rose-950 border border-rose-800 text-rose-300 rounded hover:bg-rose-900"
                        >
                          Reject Mutation
                        </button>
                        <button
                          onClick={() => handleApprove(tc.id, true)}
                          className="px-3 py-1 bg-emerald-700 text-white rounded hover:bg-emerald-600 font-semibold"
                        >
                          Authorize Execution
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}

        {isGenerating && (
          <div className="flex items-center gap-3 text-slate-400 text-xs font-mono ml-8 animate-pulse">
            <Cpu className="w-4 h-4 text-cyan-400 animate-spin" />
            <span>Sovereign neural synthesis in progress...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Prompt Workstation Dock */}
      <div className="bg-slate-900/95 border-t border-slate-800 p-4">
        <div className="relative rounded-xl border border-slate-700 bg-slate-950 focus-within:border-cyan-500 transition-colors">
          <textarea
            ref={textareaRef}
            value={inputPrompt}
            onChange={(e) => setInputPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your instruction, or press / to trigger the Sovereign Command Palette..."
            rows={Math.min(6, Math.max(2, inputPrompt.split('\n').length))}
            className="w-full bg-transparent p-3 text-sm text-slate-100 placeholder-slate-500 focus:outline-none resize-none font-sans"
          />

          <div className="flex items-center justify-between px-3 py-2 border-t border-slate-800/80 bg-slate-950/60 rounded-b-xl">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onTriggerSlashPalette && onTriggerSlashPalette()}
                className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-cyan-400 text-xs font-mono flex items-center gap-1 border border-slate-700 transition-colors"
              >
                <span>/</span> Commands
              </button>
              <span className="text-[11px] text-slate-500 hidden sm:inline">
                Shift + Enter for newline
              </span>
            </div>

            <div className="flex items-center gap-2">
              {isGenerating ? (
                <button
                  onClick={() => setIsGenerating(false)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-rose-600/20 text-rose-400 border border-rose-600/40 rounded-lg text-xs font-semibold hover:bg-rose-600/30 transition-colors"
                >
                  <Square className="w-3.5 h-3.5 fill-current" />
                  Halt
                </button>
              ) : (
                <button
                  onClick={handleSend}
                  disabled={!inputPrompt.trim()}
                  className="inline-flex items-center gap-1.5 px-4 py-1.5 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-40 disabled:hover:bg-cyan-600 text-white rounded-lg text-xs font-semibold transition-colors shadow-lg shadow-cyan-900/30"
                >
                  <Send className="w-3.5 h-3.5" />
                  Execute
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

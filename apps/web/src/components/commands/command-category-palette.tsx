'use client';

import { useState, useEffect, useMemo, useRef } from 'react';
import {
  Search,
  X,
  ChevronRight,
  ArrowLeft,
  Terminal,
  Zap,
  Cpu,
  FolderCode,
  GitBranch,
  Wrench,
  Activity,
  Layers,
  Info,
  Play,
  Copy,
  CheckCircle2,
  ShieldCheck,
  AlertTriangle
} from 'lucide-react';

export interface WebCommandSpec {
  name: string;
  category: string;
  subcategory?: string;
  synopsis: string;
  description: string;
  usage: string;
  safetyTier: 'READ_ONLY' | 'LOCAL_MUTATION' | 'NETWORK_EGRESS' | 'CRITICAL_GOVERNANCE';
  arguments?: Array<{ name: string; description: string; required: boolean }>;
  flags?: Array<{ flag: string; description: string }>;
  examples: string[];
}

export const WEB_COMMAND_CATALOG: WebCommandSpec[] = [
  // Category: Agent & Autonomous Goals
  {
    name: '/goal',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Trajectory & Work',
    synopsis: 'Launch a long-horizon autonomous task trajectory',
    description: 'Submits a sovereign objective to the AutonomousOrchestrator. The engine initializes a LongHorizonTrajectoryEngine instance, verifies workspace territory boundaries, synthesizes plan steps, and will not stop until verified complete or explicit human preemption.',
    usage: '/goal <objective-text> [--priority <level>]',
    safetyTier: 'LOCAL_MUTATION',
    arguments: [{ name: 'objective', description: 'Goal description to achieve', required: true }],
    flags: [{ flag: '--priority', description: 'Execution priority (low, normal, critical)' }],
    examples: ['/goal fix all null checks in router', '/goal add unit tests for user service']
  },
  {
    name: '/plan',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Trajectory & Work',
    synopsis: 'Generate structured multi-step DAG plan for approval',
    description: 'Synthesizes an execution DAG without performing mutations. Displays affected file territories, required provider models, risk analysis, and estimated token quota before committing to work.',
    usage: '/plan <task-description>',
    safetyTier: 'READ_ONLY',
    arguments: [{ name: 'task', description: 'Task description to decompose', required: true }],
    examples: ['/plan migrate database schema from v1 to v2']
  },
  {
    name: '/run',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Trajectory & Work',
    synopsis: 'Execute the currently approved plan DAG',
    description: 'Triggers DagExecutionService to execute wave batches in parallel across isolated worktrees. Synchronizes progress telemetry to the Six Answers HUD.',
    usage: '/run [--parallelism <count>]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/run', '/run -j 4']
  },
  {
    name: '/resume',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Trajectory & Work',
    synopsis: 'Resume interrupted execution from persistent checkpoint',
    description: 'Loads the last verified execution checkpoint from EventJournalService and resumes active trajectory from the exact node where it was halted.',
    usage: '/resume [checkpoint-id]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/resume']
  },
  {
    name: '/checkpoint',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Queue & Hierarchy',
    synopsis: 'Capture immutable cryptographic snapshot of state',
    description: 'Forces immediate serialization of current workspace AST, git tree, and conversation state into an attested checkpoint envelope.',
    usage: '/checkpoint [label]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/checkpoint pre-refactor']
  },
  {
    name: '/cancel',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Queue & Hierarchy',
    synopsis: 'Immediately abort current task and revert uncommitted mutations',
    description: 'Signals QueueInterruptCoordinator to forcibly interrupt active worker threads, revoke pending tool executions, and reset working files to clean git HEAD.',
    usage: '/cancel [--keep-changes]',
    safetyTier: 'CRITICAL_GOVERNANCE',
    examples: ['/cancel']
  },
  {
    name: '/tree',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Queue & Hierarchy',
    synopsis: 'Display active DAG dependency lattice and execution waves',
    description: 'Renders an ASCII or structured tree view of all task nodes in the current goal graph, highlighting completed, running, blocked, and failed nodes.',
    usage: '/tree [--filter <state>]',
    safetyTier: 'READ_ONLY',
    examples: ['/tree']
  },
  {
    name: '/queue',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Queue & Hierarchy',
    synopsis: 'Inspect and manipulate the background agent task queue',
    description: 'Lists all pending, blocked, and running background jobs. Allows prioritizing tasks or deleting orphaned work items.',
    usage: '/queue [list | clear]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/queue list']
  },
  {
    name: '/worker',
    category: 'Agent & Autonomous Execution',
    subcategory: 'Queue & Hierarchy',
    synopsis: 'Inspect active hierarchical worker agent pool',
    description: 'Displays the status, provider assignment, current AST node, and token spend of all child workers operating under the supervisor.',
    usage: '/worker list',
    safetyTier: 'READ_ONLY',
    examples: ['/worker list']
  },

  // Category: Providers & Neural Models (<= 8 commands: renders directly!)
  {
    name: '/providers',
    category: 'Providers & Neural Cascades',
    synopsis: 'List all configured model providers and live health status',
    description: 'Queries ProviderOnboardingService and StaticProviderDescriptorRegistry to list all supported neural providers (Groq, Gemini, DeepSeek, Bedrock, Ollama, Frontier Alpha, Frontier Omega), showing credential availability, latency, and operational health.',
    usage: '/providers',
    safetyTier: 'READ_ONLY',
    examples: ['/providers']
  },
  {
    name: '/providers test',
    category: 'Providers & Neural Cascades',
    synopsis: 'Execute live lightweight probe against specified provider',
    description: 'Sends a zero-token or minimal probe request to verify network connectivity, authentication validity, and rate limit headroom for a target provider.',
    usage: '/providers test <provider-id>',
    safetyTier: 'NETWORK_EGRESS',
    examples: ['/providers test gemini']
  },
  {
    name: '/providers enable',
    category: 'Providers & Neural Cascades',
    synopsis: 'Enable a provider for automatic cascade routing',
    description: 'Marks a healthy provider as eligible for participation in dynamic issue routing and multi-tier cascades.',
    usage: '/providers enable <provider-id>',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/providers enable groq']
  },
  {
    name: '/providers prefer',
    category: 'Providers & Neural Cascades',
    synopsis: 'Set prioritized primary provider for autonomous execution',
    description: 'Reorders the provider cascade hierarchy to route initial hypothesis and synthesis tasks to this provider first.',
    usage: '/providers prefer <provider-id>',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/providers prefer gemini']
  },
  {
    name: '/model',
    category: 'Providers & Neural Cascades',
    synopsis: 'Inspect or override current active model for session',
    description: 'Allows overriding the dynamically selected model for the current interactive conversation or worker lane.',
    usage: '/model [model-id]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/model sovereign-v1']
  },
  {
    name: '/cascade',
    category: 'Providers & Neural Cascades',
    synopsis: 'View and configure multi-tier cost/performance cascade',
    description: 'Inspects the Free-to-Paid fallback ladder: Free/Local -> Mid-tier -> Frontier Tier.',
    usage: '/cascade [--mode <auto|local-only>]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/cascade']
  },
  {
    name: '/quota',
    category: 'Providers & Neural Cascades',
    synopsis: 'Display token consumption, cost, and rate-limit headroom',
    description: 'Reads the QuotaLedger to show prompt tokens, completion tokens, cached tokens, and estimated monetary cost across all providers in current billing cycle.',
    usage: '/quota',
    safetyTier: 'READ_ONLY',
    examples: ['/quota']
  },

  // Category: Code & AST Healing
  {
    name: '/heal',
    category: 'AST Parsing & Self-Healing',
    synopsis: 'Trigger AutonomousAstHealingLoop on compiler diagnostics',
    description: 'Inspects compiler diagnostics, extracts affected AST symbol subtrees, synthesizes candidate repairs, and verifies each against the compiler until zero diagnostics remain.',
    usage: '/heal [file-path]',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/heal', '/heal src/Router.kt']
  },
  {
    name: '/ast',
    category: 'AST Parsing & Self-Healing',
    synopsis: 'Parse and inspect AST node tree for a file or symbol',
    description: 'Produces a structured AST representation of functions, classes, imports, and variables with token spans and type signatures.',
    usage: '/ast <file-path>',
    safetyTier: 'READ_ONLY',
    examples: ['/ast src/Main.kt']
  },
  {
    name: '/diagnose',
    category: 'AST Parsing & Self-Healing',
    synopsis: 'Run compiler diagnostics across active workspace',
    description: 'Executes non-mutating compiler checks to surface syntax errors, missing imports, type mismatches, and deprecation warnings.',
    usage: '/diagnose',
    safetyTier: 'READ_ONLY',
    examples: ['/diagnose']
  },
  {
    name: '/symbols',
    category: 'AST Parsing & Self-Healing',
    synopsis: 'Search AST symbol table across the entire project',
    description: 'Searches the global symbol index by name or signature to identify definitions, callers, and interface implementations.',
    usage: '/symbols <query>',
    safetyTier: 'READ_ONLY',
    examples: ['/symbols ProviderOnboarding']
  },

  // Category: Workspace & Files
  {
    name: '/files',
    category: 'Workspace, Territory & Files',
    synopsis: 'List and navigate workspace file tree',
    description: 'Returns the structured directory tree within the allowed territory boundaries, respecting gitignore and hidden file policies.',
    usage: '/files [path]',
    safetyTier: 'READ_ONLY',
    examples: ['/files', '/files src/']
  },
  {
    name: '/cat',
    category: 'Workspace, Territory & Files',
    synopsis: 'Read and display file contents with line numbers',
    description: 'Displays file contents up to safety limit (512 KB), with optional line range slicing.',
    usage: '/cat <file-path>',
    safetyTier: 'READ_ONLY',
    examples: ['/cat README.md']
  },
  {
    name: '/diff',
    category: 'Workspace, Territory & Files',
    synopsis: 'Compute unified diff between working tree and git HEAD',
    description: 'Calculates colored unified diff showing modified, added, and deleted lines.',
    usage: '/diff [file-path]',
    safetyTier: 'READ_ONLY',
    examples: ['/diff']
  },

  // Category: Git & Version Control
  {
    name: '/git status',
    category: 'Git & Version Control',
    synopsis: 'Display working tree status and staged changes',
    description: 'Invokes ShellCommandRunner gitStatus to display modified, untracked, and staged files through the bounded agency gate.',
    usage: '/git status',
    safetyTier: 'READ_ONLY',
    examples: ['/git status']
  },
  {
    name: '/git diff',
    category: 'Git & Version Control',
    synopsis: 'Show uncommitted git diffs',
    description: 'Outputs git diff against HEAD with line-level addition and deletion markers.',
    usage: '/git diff',
    safetyTier: 'READ_ONLY',
    examples: ['/git diff']
  },
  {
    name: '/git commit',
    category: 'Git & Version Control',
    synopsis: 'Create a verified git commit with structured message',
    description: 'Stages modified files and executes git commit with mandatory sovereign attribution and progress ledger reference.',
    usage: '/git commit -m "<message>"',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/git commit -m "fix: update router"']
  },

  // Category: MCP & Tool Integrations
  {
    name: '/mcp list',
    category: 'MCP Servers & Tool Governance',
    synopsis: 'List all configured MCP servers and active tool budgets',
    description: 'Queries McpHostManager to inspect running stdio and HTTP Model Context Protocol servers, showing tool counts, health status, and response latency.',
    usage: '/mcp list',
    safetyTier: 'READ_ONLY',
    examples: ['/mcp list']
  },
  {
    name: '/mcp test',
    category: 'MCP Servers & Tool Governance',
    synopsis: 'Ping all MCP servers and record health attestation',
    description: 'Executes initialize and tools/list handshakes on all declared servers, writing health timestamps to health.tsv.',
    usage: '/mcp test',
    safetyTier: 'LOCAL_MUTATION',
    examples: ['/mcp test']
  },

  // Category: Diagnostics & Telemetry
  {
    name: '/doctor',
    category: 'Telemetry, Doctor & HUD',
    synopsis: 'Comprehensive system diagnostic and health audit',
    description: 'Executes BackendDoctor to inspect JVM runtime, bridge port readiness, git state, provider credentials, MCP servers, and zero-retention compliance.',
    usage: '/doctor',
    safetyTier: 'READ_ONLY',
    examples: ['/doctor']
  },
  {
    name: '/answers',
    category: 'Telemetry, Doctor & HUD',
    synopsis: 'Display the Six Continuous Answers state snapshot',
    description: 'Outputs the current objective, current action, invariant law, changes made, verification outcome, and confidence metric.',
    usage: '/answers',
    safetyTier: 'READ_ONLY',
    examples: ['/answers']
  },

  // Category: SpecGraph Foundry
  {
    name: '/specgraph',
    category: 'SpecGraph & Merkle Sealing',
    synopsis: 'Display active SpecGraph project specification lattice',
    description: 'Inspects the project\'s SpecGraph nodes, showing requirement statements, source bindings, research dependencies, and acceptance criteria.',
    usage: '/specgraph',
    safetyTier: 'READ_ONLY',
    examples: ['/specgraph']
  },
  {
    name: '/certify',
    category: 'SpecGraph & Merkle Sealing',
    synopsis: 'Run MerkleReleaseCertifier and generate cryptographic master seal',
    description: 'Verifies JAR SHA-256, Android APK SHA-256, Web Standalone SHA-256, and contract test passes, outputting an immutable Merkle root.',
    usage: '/certify [--seal]',
    safetyTier: 'CRITICAL_GOVERNANCE',
    examples: ['/certify']
  }
];

export const CATEGORIES_METADATA = [
  { id: 'Agent & Autonomous Execution', icon: Zap, color: 'text-amber-400', desc: 'Goals, plans, checkpoints, trajectories & worker hierarchy' },
  { id: 'Providers & Neural Cascades', icon: Cpu, color: 'text-purple-400', desc: 'Frontier neural models, Groq, Gemini, DeepSeek & cost cascades' },
  { id: 'AST Parsing & Self-Healing', icon: Wrench, color: 'text-cyan-400', desc: 'Symbol trees, compiler diagnostics & automated AST repair' },
  { id: 'Workspace, Territory & Files', icon: FolderCode, color: 'text-emerald-400', desc: 'Directory trees, file buffers, diffs & territory boundaries' },
  { id: 'Git & Version Control', icon: GitBranch, color: 'text-rose-400', desc: 'Working tree status, diffs, safe commits & rebase gates' },
  { id: 'MCP Servers & Tool Governance', icon: Terminal, color: 'text-blue-400', desc: 'Model Context Protocol hosts, sandbox limits & tools' },
  { id: 'Telemetry, Doctor & HUD', icon: Activity, color: 'text-teal-400', desc: 'Six answers, backend doctor diagnostics & dopamine quota' },
  { id: 'SpecGraph & Merkle Sealing', icon: Layers, color: 'text-indigo-400', desc: 'Requirement lattices, Vercel production & release seals' }
];

interface CommandCategoryPaletteProps {
  isOpen: boolean;
  onClose: () => Unit | void;
  onSelectCommand: (command: string) => void;
  onExecuteCommand: (command: string) => void;
}

type Unit = void;

/**
 * Interactive Slash (/) Command Category Palette for Web UI.
 *
 * Implements the non-overload navigation rule:
 * - Typing '/' opens category picker
 * - If category contains <= 8 commands, shows all commands directly
 * - If category contains > 8 commands, shows subcategories first
 * - Detailed information drawer available for every command
 */
export function CommandCategoryPalette({
  isOpen,
  onClose,
  onSelectCommand,
  onExecuteCommand
}: CommandCategoryPaletteProps) {
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedSubcategory, setSelectedSubcategory] = useState<string | null>(null);
  const [detailCommand, setDetailCommand] = useState<WebCommandSpec | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [argValues, setArgValues] = useState<Record<string, string>>({});
  const [flagValues, setFlagValues] = useState<Record<string, boolean>>({});

  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    } else {
      setSelectedCategory(null);
      setSelectedSubcategory(null);
      setDetailCommand(null);
      setSearchQuery('');
      setSelectedIndex(0);
      setArgValues({});
      setFlagValues({});
    }
  }, [isOpen]);

  // Reset selectedIndex whenever navigation level changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [selectedCategory, selectedSubcategory, searchQuery, detailCommand]);

  // Filter commands by search or active category
  const filteredCommands = useMemo(() => {
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().replace('/', '');
      return WEB_COMMAND_CATALOG.filter(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          c.synopsis.toLowerCase().includes(q) ||
          c.category.toLowerCase().includes(q)
      );
    }
    if (!selectedCategory) return [];
    let list = WEB_COMMAND_CATALOG.filter((c) => c.category === selectedCategory);
    if (selectedSubcategory) {
      list = list.filter((c) => c.subcategory === selectedSubcategory);
    }
    return list;
  }, [searchQuery, selectedCategory, selectedSubcategory]);

  // Subcategories for current category (if > 8 commands)
  const availableSubcategories = useMemo(() => {
    if (!selectedCategory) return [];
    const catCommands = WEB_COMMAND_CATALOG.filter((c) => c.category === selectedCategory);
    if (catCommands.length <= 8) return [];
    const subs = new Set<string>();
    catCommands.forEach((c) => {
      if (c.subcategory) subs.add(c.subcategory);
    });
    return Array.from(subs);
  }, [selectedCategory]);

  // Current navigable items count
  const currentItemCount = useMemo(() => {
    if (detailCommand) return 0;
    if (searchQuery.trim().length > 0) return filteredCommands.length;
    if (!selectedCategory) return CATEGORIES_METADATA.length;
    if (availableSubcategories.length > 0 && !selectedSubcategory) {
      return availableSubcategories.length;
    }
    return filteredCommands.length;
  }, [detailCommand, searchQuery, selectedCategory, availableSubcategories, selectedSubcategory, filteredCommands]);

  // Compute finalized command invocation string with entered args & flags
  const computedInvocation = useMemo(() => {
    if (!detailCommand) return '';
    let inv = detailCommand.name;
    if (detailCommand.arguments) {
      for (const arg of detailCommand.arguments) {
        const val = argValues[arg.name];
        if (val && val.trim().length > 0) {
          inv += val.includes(' ') ? ` "${val.trim()}"` : ` ${val.trim()}`;
        }
      }
    }
    if (detailCommand.flags) {
      for (const flag of detailCommand.flags) {
        if (flagValues[flag.flag]) {
          inv += ` ${flag.flag}`;
        }
      }
    }
    return inv;
  }, [detailCommand, argValues, flagValues]);

  // Keyboard navigation handler
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        if (detailCommand) {
          setDetailCommand(null);
        } else if (selectedSubcategory) {
          setSelectedSubcategory(null);
        } else if (selectedCategory) {
          setSelectedCategory(null);
        } else {
          onClose();
        }
        return;
      }

      if (e.key === 'Backspace' && searchQuery === '') {
        if (detailCommand) {
          e.preventDefault();
          setDetailCommand(null);
        } else if (selectedSubcategory) {
          e.preventDefault();
          setSelectedSubcategory(null);
        } else if (selectedCategory) {
          e.preventDefault();
          setSelectedCategory(null);
        }
        return;
      }

      if (detailCommand) return; // In detail view, arrows are used inside inputs

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (currentItemCount > 0 ? (prev + 1) % currentItemCount : 0));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (currentItemCount > 0 ? (prev - 1 + currentItemCount) % currentItemCount : 0));
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (searchQuery.trim().length > 0) {
          if (filteredCommands[selectedIndex]) {
            setDetailCommand(filteredCommands[selectedIndex]);
          }
        } else if (!selectedCategory) {
          if (CATEGORIES_METADATA[selectedIndex]) {
            setSelectedCategory(CATEGORIES_METADATA[selectedIndex].id);
          }
        } else if (availableSubcategories.length > 0 && !selectedSubcategory) {
          if (availableSubcategories[selectedIndex]) {
            setSelectedSubcategory(availableSubcategories[selectedIndex]);
          }
        } else {
          if (filteredCommands[selectedIndex]) {
            setDetailCommand(filteredCommands[selectedIndex]);
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, detailCommand, selectedSubcategory, selectedCategory, searchQuery, currentItemCount, selectedIndex, filteredCommands, availableSubcategories, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4">
      <div className="relative w-full max-w-3xl max-h-[85vh] rounded-2xl border border-sg-border-dim bg-sg-panel-surface/95 shadow-2xl flex flex-col overflow-hidden text-sg-text-primary">
        
        {/* Header Bar */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-sg-border-dim bg-sg-card-surface/60">
          <div className="flex items-center gap-3">
            {selectedCategory && (
              <button
                onClick={() => {
                  if (detailCommand) {
                    setDetailCommand(null);
                  } else if (selectedSubcategory) {
                    setSelectedSubcategory(null);
                  } else {
                    setSelectedCategory(null);
                  }
                }}
                className="p-1 rounded-lg text-cyan-400 hover:bg-sg-panel-surface transition-colors"
                title="Back (Backspace)"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
            )}
            <Terminal className="h-5 w-5 text-cyan-400" />
            <h2 className="text-base font-bold font-mono text-white">
              {detailCommand
                ? detailCommand.name
                : selectedSubcategory
                ? selectedSubcategory
                : selectedCategory
                ? selectedCategory
                : 'COMMAND CATEGORIES (/) '}
            </h2>
          </div>

          <div className="flex items-center gap-3">
            <span className="hidden sm:inline text-xs text-sg-text-muted font-mono">
              [↑↓ Navigate] [Enter Select] [Esc Back]
            </span>
            <button
              onClick={() => onClose()}
              className="p-1.5 rounded-lg text-sg-text-muted hover:text-white hover:bg-sg-panel-surface transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Search Input */}
        <div className="p-4 border-b border-sg-border-dim/60 bg-sg-gutter-bg/40">
          <div className="relative">
            <Search className="absolute left-3.5 top-3 h-4 w-4 text-cyan-400" />
            <input
              ref={searchInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search all commands or filter by keyword..."
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-sg-card-surface border border-sg-border-dim text-sm font-mono text-white placeholder-sg-text-muted focus:outline-none focus:border-cyan-400 transition-colors"
            />
          </div>
        </div>

        {/* Content Pane */}
        <div className="flex-1 overflow-y-auto p-5">
          {detailCommand ? (
            /* Command Detail View with Parameter Form */
            <div className="space-y-5 font-mono text-xs">
              <div className="p-4 rounded-xl border border-cyan-500/30 bg-cyan-500/5 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-lg font-bold text-cyan-400">{detailCommand.name}</span>
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold border border-cyan-400/40 text-cyan-300">
                    {detailCommand.safetyTier}
                  </span>
                </div>
                <p className="text-sg-text-secondary text-sm font-sans">{detailCommand.synopsis}</p>
              </div>

              <div>
                <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-1">SYNTAX SPECIFICATION</p>
                <div className="p-3 rounded-lg bg-sg-gutter-bg border border-sg-border-dim text-emerald-400 font-mono">
                  {detailCommand.usage}
                </div>
              </div>

              <div>
                <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-1">DESCRIPTION</p>
                <p className="text-sg-text-primary font-sans leading-relaxed text-xs">
                  {detailCommand.description}
                </p>
              </div>

              {detailCommand.arguments && detailCommand.arguments.length > 0 && (
                <div>
                  <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-2">ARGUMENTS & PARAMETERS</p>
                  <div className="space-y-2">
                    {detailCommand.arguments.map((arg, i) => (
                      <div key={i} className="p-3 rounded-lg bg-sg-gutter-bg border border-sg-border-dim space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="text-cyan-400 font-bold">{arg.name} {arg.required && <span className="text-red-400">*</span>}</span>
                          <span className="text-sg-text-secondary text-[11px]">{arg.description}</span>
                        </div>
                        <input
                          type="text"
                          value={argValues[arg.name] || ''}
                          onChange={(e) => setArgValues({ ...argValues, [arg.name]: e.target.value })}
                          placeholder={`Enter value for <${arg.name}>...`}
                          className="w-full px-3 py-1.5 rounded-lg bg-sg-card-surface border border-sg-border-dim text-xs font-mono text-white focus:outline-none focus:border-cyan-400"
                        />
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {detailCommand.flags && detailCommand.flags.length > 0 && (
                <div>
                  <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-2">FLAGS & SWITCHES</p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {detailCommand.flags.map((flag, i) => (
                      <label key={i} className="flex items-center gap-2.5 p-2.5 rounded-lg bg-sg-gutter-bg border border-sg-border-dim cursor-pointer hover:border-cyan-500/30">
                        <input
                          type="checkbox"
                          checked={!!flagValues[flag.flag]}
                          onChange={(e) => setFlagValues({ ...flagValues, [flag.flag]: e.target.checked })}
                          className="rounded border-sg-border-dim text-cyan-400 focus:ring-cyan-400"
                        />
                        <div className="flex-1 min-w-0">
                          <p className="text-cyan-300 font-bold text-xs">{flag.flag}</p>
                          <p className="text-[10px] text-sg-text-muted truncate">{flag.description}</p>
                        </div>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-2">DYNAMIC PREVIEW INVOCATION</p>
                <div className="p-3 rounded-lg bg-cyan-950/30 border border-cyan-500/30 text-cyan-300 font-mono text-xs break-all">
                  {computedInvocation}
                </div>
              </div>

              <div>
                <p className="text-sg-text-muted font-bold text-[10px] tracking-wider mb-2">CANONICAL EXAMPLES</p>
                <div className="space-y-1.5">
                  {detailCommand.examples.map((ex, i) => (
                    <div key={i} className="p-2 rounded bg-sg-gutter-bg border border-sg-border-dim text-white text-xs">
                      {ex}
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex gap-3 pt-4 border-t border-sg-border-dim">
                <button
                  onClick={() => {
                    onSelectCommand(computedInvocation);
                    onClose();
                  }}
                  className="flex-1 py-2.5 rounded-lg border border-sg-border-dim bg-sg-card-surface hover:bg-sg-panel-surface text-cyan-400 font-bold transition-colors"
                >
                  Insert into Composer
                </button>
                <button
                  onClick={() => {
                    onExecuteCommand(computedInvocation);
                    onClose();
                  }}
                  className="flex-1 py-2.5 rounded-lg bg-cyan-400 text-black font-bold hover:bg-cyan-300 transition-colors shadow-lg shadow-cyan-500/20"
                >
                  Execute Now
                </button>
              </div>
            </div>
          ) : searchQuery.trim().length > 0 ? (
            /* Search Results */
            <div className="space-y-2">
              {filteredCommands.length === 0 ? (
                <div className="text-center py-12 text-sg-text-muted text-sm font-mono">
                  No commands matching &ldquo;{searchQuery}&rdquo;
                </div>
              ) : (
                filteredCommands.map((cmd, idx) => (
                  <CommandRow
                    key={cmd.name}
                    command={cmd}
                    isSelected={idx === selectedIndex}
                    onSelect={() => {
                      onSelectCommand(cmd.name);
                      onClose();
                    }}
                    onInspect={() => setDetailCommand(cmd)}
                  />
                ))
              )}
            </div>
          ) : !selectedCategory ? (
            /* Level 0: 8 Categories Grid */
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {CATEGORIES_METADATA.map((cat, idx) => {
                const IconComponent = cat.icon;
                const count = WEB_COMMAND_CATALOG.filter((c) => c.category === cat.id).length;
                const isSelected = idx === selectedIndex;
                return (
                  <button
                    key={cat.id}
                    onClick={() => {
                      setSelectedCategory(cat.id);
                    }}
                    className={`flex items-start gap-4 p-4 rounded-xl border transition-all text-left group ${
                      isSelected
                        ? 'border-cyan-400 bg-cyan-950/40 ring-1 ring-cyan-400 shadow-lg shadow-cyan-500/10'
                        : 'border-sg-border-dim bg-sg-card-surface/70 hover:bg-sg-panel-surface hover:border-cyan-500/40'
                    }`}
                  >
                    <div className="p-2.5 rounded-lg bg-sg-gutter-bg border border-sg-border-dim group-hover:border-cyan-400/40 transition-colors">
                      <IconComponent className={`h-5 w-5 ${cat.color}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-sm text-white group-hover:text-cyan-300 transition-colors font-mono">
                          {cat.id}
                        </span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                          {count}
                        </span>
                      </div>
                      <p className="text-xs text-sg-text-muted mt-1 leading-relaxed line-clamp-2">
                        {cat.desc}
                      </p>
                    </div>
                    <ChevronRight className="h-4 w-4 text-sg-text-muted group-hover:text-cyan-400 self-center" />
                  </button>
                );
              })}
            </div>
          ) : availableSubcategories.length > 0 && !selectedSubcategory ? (
            /* Level 1: Subcategories (for categories > 8 commands) */
            <div className="space-y-3">
              <p className="text-xs text-sg-text-muted font-mono mb-2">
                This category contains {WEB_COMMAND_CATALOG.filter((c) => c.category === selectedCategory).length} commands. Select a subcategory to browse:
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {availableSubcategories.map((sub, idx) => {
                  const count = WEB_COMMAND_CATALOG.filter(
                    (c) => c.category === selectedCategory && c.subcategory === sub
                  ).length;
                  const isSelected = idx === selectedIndex;
                  return (
                    <button
                      key={sub}
                      onClick={() => setSelectedSubcategory(sub)}
                      className={`flex items-center justify-between p-4 rounded-xl border transition-colors text-left ${
                        isSelected
                          ? 'border-cyan-400 bg-cyan-950/40 ring-1 ring-cyan-400'
                          : 'border-sg-border-dim bg-sg-card-surface hover:border-cyan-400'
                      }`}
                    >
                      <div>
                        <p className="font-bold text-sm text-white font-mono">{sub}</p>
                        <p className="text-xs text-cyan-400 font-mono mt-0.5">{count} commands</p>
                      </div>
                      <ChevronRight className="h-4 w-4 text-sg-text-muted" />
                    </button>
                  );
                })}
              </div>
            </div>
          ) : (
            /* Level 2: Command List (<= 8 commands or within chosen subcategory) */
            <div className="space-y-2">
              {filteredCommands.map((cmd, idx) => (
                <CommandRow
                  key={cmd.name}
                  command={cmd}
                  isSelected={idx === selectedIndex}
                  onSelect={() => {
                    onSelectCommand(cmd.name);
                    onClose();
                  }}
                  onInspect={() => setDetailCommand(cmd)}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function CommandRow({
  command,
  isSelected,
  onSelect,
  onInspect
}: {
  command: WebCommandSpec;
  isSelected?: boolean;
  onSelect: () => void;
  onInspect: () => void;
}) {
  return (
    <div className={`flex items-center justify-between p-3.5 rounded-xl border transition-all font-mono ${
      isSelected
        ? 'border-cyan-400 bg-cyan-950/40 ring-1 ring-cyan-400'
        : 'border-sg-border-dim bg-sg-card-surface/70 hover:bg-sg-panel-surface'
    }`}>
      <div className="flex-1 min-w-0 pr-4">
        <div className="flex items-center gap-2">
          <span className="font-bold text-cyan-400 text-sm">{command.name}</span>
          <span className="text-[10px] text-sg-text-muted px-1.5 py-0.5 rounded bg-sg-gutter-bg border border-sg-border-dim">
            {command.safetyTier}
          </span>
        </div>
        <p className="text-xs text-sg-text-secondary mt-1 font-sans truncate">{command.synopsis}</p>
        <p className="text-[11px] text-sg-text-muted mt-0.5 truncate">{command.usage}</p>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={onInspect}
          title="Inspect Documentation & Parameters"
          className="p-1.5 rounded-lg border border-sg-border-dim bg-sg-gutter-bg text-sg-text-secondary hover:text-white transition-colors"
        >
          <Info className="h-4 w-4" />
        </button>
        <button
          onClick={onSelect}
          className="px-3 py-1.5 text-xs font-bold rounded-lg bg-cyan-400/10 border border-cyan-400/30 text-cyan-400 hover:bg-cyan-400 hover:text-black transition-colors"
        >
          Use
        </button>
      </div>
    </div>
  );
}

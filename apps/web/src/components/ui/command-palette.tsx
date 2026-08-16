'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Search, ArrowRight, Settings, Home, FileText, Users, Zap, Clock } from 'lucide-react';
import { useProjects, useWorkItems } from '@/lib/api-atropos/hooks';
import {
  developerToolsItem,
  navigationSpine,
  projectSections,
} from '@/components/navigation/routes';
import { useOptionalSessionState } from '@/lib/contexts/session-state-context';
import { useEngineCommands } from '@/lib/engine/use-engine-commands';
import { engine } from '@/lib/engine/client';
import { COMMON_SHORTCUTS, useKeyboardShortcuts } from '@/lib/hooks/use-keyboard-shortcuts';

interface CommandItem {
  id: string;
  label: string;
  description?: string;
  icon: React.ReactNode;
  action: () => void;
  category: 'navigation' | 'project' | 'task' | 'action';
  keywords?: string[];
}

export function CommandPalette() {
  const router = useRouter();
  const { data: projects } = useProjects();
  // Chrome must not take down the shell over an optional preference: with
  // no provider, Developer Tools stay hidden and no project is active.
  const session = useOptionalSessionState()?.session;

  // §5.4: the palette must be reachable from the keyboard anywhere in the app.
  useKeyboardShortcuts([
    {
      ...COMMON_SHORTCUTS.COMMAND_PALETTE,
      handler: () => {
        setOpen((prev) => !prev);
        setSearch('');
        setSelectedIndex(0);
      },
    },
  ]);
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  /** What the last engine command printed, or the refusal it met. */
  const [commandResult, setCommandResult] = useState<string | null>(null);
  const [commandRunning, setCommandRunning] = useState(false);

  // Navigation commands
  //
  // §5.4 requires the palette to reach every primary action. It is derived
  // from the same spine the sidebar renders rather than a hand-kept copy: a
  // second hardcoded list is how Automation and Developer Tools went missing
  // from here while existing as routes.
  const navigationCommands: CommandItem[] = useMemo(() => {
    const spine = navigationSpine.map((item) => ({
      id: item.id,
      label: item.label,
      description: `Go to ${item.label}`,
      icon: <Home className="w-4 h-4" />,
      action: () => {
        router.push(item.href);
        setOpen(false);
      },
      category: 'navigation' as const,
      keywords: [item.id, item.label.toLowerCase()],
    }));

    // §2.10: only offered once the operator has opted in, so the palette does
    // not reintroduce a surface the navigation deliberately hides.
    if (session?.developerToolsEnabled) {
      spine.push({
        id: developerToolsItem.id,
        label: developerToolsItem.label,
        description: 'Inspectors, SpecGraph subsystem, runtime internals',
        icon: <Settings className="w-4 h-4" />,
        action: () => {
          router.push(developerToolsItem.href);
          setOpen(false);
        },
        category: 'navigation' as const,
        keywords: ['developer', 'tools', 'inspector', 'specgraph'],
      });
    }

    // Project sections are reachable only when a project is actually open.
    if (session?.activeProjectId) {
      const projectId = session.activeProjectId;
      projectSections.forEach((section) => {
        spine.push({
          id: `section-${section.id}`,
          label: `${section.label} (current project)`,
          description: `Open ${section.label} for the active project`,
          icon: <FileText className="w-4 h-4" />,
          action: () => {
            router.push(section.build(projectId));
            setOpen(false);
          },
          category: 'navigation' as const,
          keywords: [section.id, section.label.toLowerCase(), 'project'],
        });
      });
    }

    return spine;
  }, [router, session?.developerToolsEnabled, session?.activeProjectId]);

  // Project commands
  const projectCommands: CommandItem[] = useMemo(
    () =>
      projects?.map((project) => ({
        id: `project-${project.id}`,
        label: project.name,
        description: project.status,
        icon: <FileText className="w-4 h-4" />,
        action: () => {
          router.push(`/projects/${project.id}/work`);
          setOpen(false);
        },
        category: 'project' as const,
        keywords: [project.name.toLowerCase()],
      })) ?? [],
    [projects, router]
  );

  // Engine commands.
  //
  // HOE-A07 requires the palette to reach *every* primary action, and
  // SUP.UX.COMMAND-REGISTRY names the single registry it must come from. The
  // spine above covers navigation; the engine's own command vocabulary was
  // absent entirely, so the palette could not reach a single slash command.
  // These are read from /v1/commands rather than restated here — a copy would
  // be the second registry the atom exists to prevent.
  const { commands: engineRegistry } = useEngineCommands();
  const engineCommands: CommandItem[] = useMemo(
    () =>
      engineRegistry.map((entry) => ({
        id: `engine-${entry.command}`,
        label: entry.command,
        description: entry.description,
        icon: <Zap className="w-4 h-4" />,
        action: () => {
          // Run it, don't copy it. This used to write the command to the
          // clipboard because the bridge had no execution path — POST
          // /v1/command is that path now, and it builds the same router the
          // terminal uses, so the browser reimplements nothing and the shell
          // family is refused once, on the engine side, for every surface.
          //
          // The palette stays open while it runs: closing on the result would
          // discard the one thing the operator asked for.
          setCommandRunning(true);
          setCommandResult(null);
          void engine.runCommand(entry.command).then((result) => {
            setCommandRunning(false);
            if (!result.ok) {
              setCommandResult(`${result.detail}\n${result.remedy}`);
              return;
            }
            setCommandResult(
              result.data.ok
                ? result.data.output || '(the command produced no output)'
                : result.data.failure ?? 'the command failed'
            );
          });
        },
        category: 'action' as const,
        keywords: [entry.command.toLowerCase(), entry.description.toLowerCase()],
      })),
    [engineRegistry]
  );

  // All commands
  const allCommands = useMemo(
    () => [...navigationCommands, ...projectCommands, ...engineCommands],
    [navigationCommands, projectCommands, engineCommands]
  );

  // Filter commands based on search
  const filteredCommands = useMemo(() => {
    if (!search) return allCommands;

    const query = search.toLowerCase();
    return allCommands.filter((cmd) => {
      const matchesLabel = cmd.label.toLowerCase().includes(query);
      const matchesDescription = cmd.description?.toLowerCase().includes(query);
      const matchesKeywords = cmd.keywords?.some((kw) => kw.includes(query));
      return matchesLabel || matchesDescription || matchesKeywords;
    });
  }, [search, allCommands]);

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd/Ctrl+K is a global binding and lives in useKeyboardShortcuts. The
      // keys below are modal-scoped: they only mean anything while the palette
      // is open, so they stay with the palette.

      // Escape closes palette
      if (e.key === 'Escape' && open) {
        setOpen(false);
      }

      // Arrow keys navigate
      if (open && e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % filteredCommands.length);
      }
      if (open && e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) =>
          prev === 0 ? filteredCommands.length - 1 : prev - 1
        );
      }

      // Enter executes command
      if (open && e.key === 'Enter' && filteredCommands.length > 0) {
        e.preventDefault();
        filteredCommands[selectedIndex].action();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, filteredCommands, selectedIndex]);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="hidden md:flex items-center gap-2 px-3 py-2 rounded-lg border border-sg-neutral-300 dark:border-sg-neutral-700 hover:bg-sg-neutral-50 dark:hover:bg-sg-neutral-900 transition-colors text-sm text-sg-neutral-600 dark:text-sg-neutral-400"
      >
        <Search className="w-4 h-4" />
        <span>Search... Cmd+K</span>
      </button>
    );
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm"
        onClick={() => setOpen(false)}
      />

      {/* Palette */}
      <div className="fixed top-1/4 left-1/2 -translate-x-1/2 z-50 w-full max-w-md">
        <div className="bg-white dark:bg-sg-neutral-900 rounded-lg shadow-lg border border-sg-neutral-200 dark:border-sg-neutral-800 overflow-hidden">
          {/* Search input */}
          <div className="flex items-center gap-3 px-4 py-3 border-b border-sg-neutral-200 dark:border-sg-neutral-800">
            <Search className="w-5 h-5 text-sg-neutral-400" />
            <input
              autoFocus
              type="text"
              placeholder="Search projects, commands..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setSelectedIndex(0);
              }}
              className="flex-1 bg-transparent outline-none text-sg-neutral-900 dark:text-sg-neutral-50 placeholder-sg-neutral-500"
            />
            <span className="text-xs text-sg-neutral-500">ESC</span>
          </div>

          {/* What the last engine command said. Rendered verbatim: the
              engine's own output is the answer, and reformatting it here would
              be a second presentation of the same run. */}
          {(commandRunning || commandResult !== null) && (
            <div className="px-4 py-3 border-b border-sg-neutral-200 dark:border-sg-neutral-800">
              {commandRunning ? (
                <p className="text-sm text-sg-neutral-600 dark:text-sg-neutral-400">Running…</p>
              ) : (
                <>
                  <pre className="max-h-40 overflow-auto whitespace-pre-wrap break-words text-xs text-sg-neutral-800 dark:text-sg-neutral-200">
                    {commandResult}
                  </pre>
                  <button
                    onClick={() => setCommandResult(null)}
                    className="mt-2 text-xs text-sg-neutral-500 hover:text-sg-neutral-700 dark:hover:text-sg-neutral-300"
                  >
                    Dismiss
                  </button>
                </>
              )}
            </div>
          )}

          {/* Results */}
          <div className="max-h-96 overflow-y-auto">
            {filteredCommands.length === 0 ? (
              <div className="px-4 py-8 text-center text-sg-neutral-600 dark:text-sg-neutral-400">
                <p>No commands found</p>
              </div>
            ) : (
              <div className="py-2">
                {/* Group by category */}
                {Object.entries(
                  filteredCommands.reduce(
                    (groups, cmd) => {
                      if (!groups[cmd.category]) groups[cmd.category] = [];
                      groups[cmd.category].push(cmd);
                      return groups;
                    },
                    {} as Record<string, CommandItem[]>
                  )
                ).map(([category, commands]) => (
                  <div key={category}>
                    <div className="px-4 py-2 text-xs font-semibold text-sg-neutral-600 dark:text-sg-neutral-400 uppercase tracking-wider">
                      {category}
                    </div>
                    {commands.map((cmd, idx) => {
                      const isSelected =
                        filteredCommands.indexOf(cmd) === selectedIndex;
                      return (
                        <button
                          key={cmd.id}
                          onClick={cmd.action}
                          className={`w-full flex items-center gap-3 px-4 py-2 text-left transition-colors ${
                            isSelected
                              ? 'bg-sg-red-100 dark:bg-sg-red-900/20 text-sg-red-900 dark:text-sg-red-100'
                              : 'text-sg-neutral-900 dark:text-sg-neutral-50 hover:bg-sg-neutral-100 dark:hover:bg-sg-neutral-800'
                          }`}
                        >
                          <div className="flex-shrink-0 w-5 h-5 text-sg-neutral-500">
                            {cmd.icon}
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="font-medium">{cmd.label}</p>
                            {cmd.description && (
                              <p className="text-xs opacity-75">{cmd.description}</p>
                            )}
                          </div>
                          {isSelected && (
                            <ArrowRight className="w-4 h-4 flex-shrink-0" />
                          )}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="px-4 py-2 border-t border-sg-neutral-200 dark:border-sg-neutral-800 text-xs text-sg-neutral-500 flex items-center justify-between">
            <div className="flex gap-2">
              <kbd className="px-2 py-1 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded">
                ↑↓
              </kbd>
              <span>Navigate</span>
              <kbd className="px-2 py-1 bg-sg-neutral-100 dark:bg-sg-neutral-800 rounded">
                Enter
              </kbd>
              <span>Select</span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

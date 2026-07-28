'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Search, ArrowRight, Settings, Home, FileText, Users, Zap, Clock } from 'lucide-react';
import { useProjects, useWorkItems } from '@/lib/api-atropos/hooks';

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
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);

  // Navigation commands
  const navigationCommands: CommandItem[] = [
    {
      id: 'home',
      label: 'Go to Home',
      description: 'View system status and recent activity',
      icon: <Home className="w-4 h-4" />,
      action: () => {
        router.push('/');
        setOpen(false);
      },
      category: 'navigation',
      keywords: ['home', 'dashboard'],
    },
    {
      id: 'projects',
      label: 'View All Projects',
      description: 'Browse all projects',
      icon: <FileText className="w-4 h-4" />,
      action: () => {
        router.push('/projects');
        setOpen(false);
      },
      category: 'navigation',
      keywords: ['projects', 'list'],
    },
    {
      id: 'models',
      label: 'Models & Providers',
      description: 'View provider configuration',
      icon: <Zap className="w-4 h-4" />,
      action: () => {
        router.push('/models');
        setOpen(false);
      },
      category: 'navigation',
      keywords: ['models', 'providers'],
    },
    {
      id: 'history',
      label: 'View History',
      description: 'Browse all events and activity',
      icon: <Clock className="w-4 h-4" />,
      action: () => {
        router.push('/history');
        setOpen(false);
      },
      category: 'navigation',
      keywords: ['history', 'events', 'log'],
    },
    {
      id: 'settings',
      label: 'Settings',
      description: 'Configure preferences and workspace',
      icon: <Settings className="w-4 h-4" />,
      action: () => {
        router.push('/settings');
        setOpen(false);
      },
      category: 'navigation',
      keywords: ['settings', 'preferences', 'config'],
    },
  ];

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

  // All commands
  const allCommands = useMemo(
    () => [...navigationCommands, ...projectCommands],
    [navigationCommands, projectCommands]
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
      // Cmd+K or Ctrl+K opens palette
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen((prev) => !prev);
        setSearch('');
        setSelectedIndex(0);
      }

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

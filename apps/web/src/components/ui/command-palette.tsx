'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

interface Command {
  id: string;
  label: string;
  description?: string;
  category: string;
  action: () => void;
  shortcut?: string;
}

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(0);
  const router = useRouter();

  const commands: Command[] = [
    {
      id: 'home',
      label: 'Go to Home',
      category: 'navigation',
      shortcut: 'Cmd+H',
      action: () => router.push('/'),
    },
    {
      id: 'projects',
      label: 'Go to Projects',
      category: 'navigation',
      shortcut: 'Cmd+P',
      action: () => router.push('/projects'),
    },
    {
      id: 'settings',
      label: 'Open Settings',
      category: 'navigation',
      action: () => router.push('/settings'),
    },
    {
      id: 'toggle-theme',
      label: 'Toggle Dark/Light',
      category: 'theme',
      action: () => {
        const current = localStorage.getItem('atropos-theme-customization');
        if (current) {
          const theme = JSON.parse(current);
          theme.mode = theme.mode === 'dark' ? 'light' : 'dark';
          localStorage.setItem('atropos-theme-customization', JSON.stringify(theme));
          window.location.reload();
        }
      },
    },
  ];

  const filtered = commands.filter(
    cmd =>
      cmd.label.toLowerCase().includes(search.toLowerCase()) ||
      cmd.description?.toLowerCase().includes(search.toLowerCase())
  );

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen(!open);
        setSearch('');
        setSelected(0);
      }

      if (!open) return;

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelected(prev => (prev + 1) % filtered.length);
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelected(prev => (prev - 1 + filtered.length) % filtered.length);
          break;
        case 'Enter':
          e.preventDefault();
          if (filtered[selected]) {
            filtered[selected].action();
            setOpen(false);
          }
          break;
        case 'Escape':
          e.preventDefault();
          setOpen(false);
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, filtered, selected]);

  if (!open) {
    return (
      <button
        className="command-palette-trigger"
        onClick={() => setOpen(true)}
        title="Open command palette (Cmd+K)"
      >
        ⌘K
      </button>
    );
  }

  return (
    <>
      <div className="command-palette-overlay" onClick={() => setOpen(false)} />

      <div className="command-palette">
        <input
          autoFocus
          type="text"
          placeholder="Type a command or search..."
          value={search}
          onChange={e => {
            setSearch(e.target.value);
            setSelected(0);
          }}
          className="command-input"
        />

        <div className="command-list">
          {filtered.length === 0 ? (
            <div className="command-empty">No commands found</div>
          ) : (
            filtered.map((cmd, idx) => (
              <button
                key={cmd.id}
                className={`command-item ${selected === idx ? 'selected' : ''}`}
                onClick={() => {
                  cmd.action();
                  setOpen(false);
                }}
              >
                <div className="command-info">
                  <div className="command-label">{cmd.label}</div>
                  {cmd.description && (
                    <div className="command-description">{cmd.description}</div>
                  )}
                </div>
                {cmd.shortcut && (
                  <div className="command-shortcut">{cmd.shortcut}</div>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      <style jsx>{`
        .command-palette-trigger {
          padding: var(--sg-space-2) var(--sg-space-3);
          background: var(--sg-border);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-md);
          cursor: pointer;
          font-size: var(--sg-type-sm);
          color: var(--sg-text-secondary);
          font-family: var(--sg-font-mono);

          &:hover {
            background: color-mix(in srgb, var(--sg-accent) 14%, var(--sg-surface));
            border-color: var(--sg-accent);
          }
        }

        .command-palette-overlay {
          position: fixed;
          inset: 0;
          background: rgba(0, 0, 0, 0.5);
          z-index: 999;
        }

        .command-palette {
          position: fixed;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          width: 90%;
          max-width: 600px;
          max-height: 400px;
          background: var(--sg-elevated);
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-lg);
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
          z-index: 1000;
          display: flex;
          flex-direction: column;
          overflow: hidden;
        }

        .command-input {
          padding: var(--sg-space-3);
          border: none;
          border-bottom: 1px solid var(--sg-border);
          background: transparent;
          color: var(--sg-text-primary);
          font-size: var(--sg-type-base);

          &:focus {
            outline: none;
            border-bottom-color: var(--sg-accent);
          }

          &::placeholder {
            color: var(--sg-text-muted);
          }
        }

        .command-list {
          overflow-y: auto;
          max-height: 300px;
        }

        .command-empty {
          padding: var(--sg-space-4);
          text-align: center;
          color: var(--sg-text-muted);
          font-size: var(--sg-type-sm);
        }

        .command-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 100%;
          padding: var(--sg-space-3);
          border: none;
          background: transparent;
          color: inherit;
          text-align: left;
          cursor: pointer;
          transition: background-color 0.2s;

          &:hover,
          &.selected {
            background: var(--sg-surface);
          }
        }

        .command-info {
          display: flex;
          flex-direction: column;
          gap: 2px;
          flex: 1;
        }

        .command-label {
          font-weight: var(--sg-weight-medium);
          font-size: var(--sg-type-sm);
          color: var(--sg-text-primary);
        }

        .command-description {
          font-size: var(--sg-type-xs);
          color: var(--sg-text-secondary);
        }

        .command-shortcut {
          font-family: var(--sg-font-mono);
          font-size: var(--sg-type-xs);
          color: var(--sg-text-muted);
          padding: var(--sg-space-1) var(--sg-space-2);
          background: var(--sg-border);
          border-radius: 3px;
        }
      `}</style>
    </>
  );
}

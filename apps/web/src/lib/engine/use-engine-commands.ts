/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { useEffect, useState } from 'react';
import { engineBaseUrl } from './client';

/**
 * The command palette's entries, read from the engine's registry.
 *
 * `SUP.UX.COMMAND-REGISTRY` is explicit: "Generate palette from single command
 * registry; never hard-code entries." The web palette held its own list, which
 * is that hard-coded set one process away — it drifts the moment a command is
 * added to the engine, and the drift is invisible because both sides stay
 * internally consistent. `HOE-A07` then fails silently: the palette stops
 * reaching every primary action and nothing reports it.
 *
 * Returns an empty list rather than a fallback list when the engine cannot be
 * reached. A fallback would be a second registry with extra steps, and an
 * operator offered commands the engine has never heard of is worse served than
 * one told the palette is unavailable.
 */
export interface EngineCommand {
  command: string;
  description: string;
}

export interface EngineCommandsState {
  commands: EngineCommand[];
  quickAccess: string[];
  loading: boolean;
  /** True when the registry could not be read; the palette says so. */
  unavailable: boolean;
}

export function useEngineCommands(): EngineCommandsState {
  const [state, setState] = useState<EngineCommandsState>({
    commands: [],
    quickAccess: [],
    loading: true,
    unavailable: false,
  });

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const response = await fetch(`${engineBaseUrl()}/v1/commands`, {
          cache: 'no-store',
          headers: { accept: 'application/json' },
        });
        if (!response.ok) throw new Error(String(response.status));
        const body = (await response.json()) as {
          commands?: EngineCommand[];
          quickAccess?: string[];
        };
        if (cancelled) return;
        setState({
          commands: body.commands ?? [],
          quickAccess: body.quickAccess ?? [],
          loading: false,
          unavailable: false,
        });
      } catch {
        if (cancelled) return;
        setState({ commands: [], quickAccess: [], loading: false, unavailable: true });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

/**
 * Filters the registry for the palette's search box.
 *
 * Matches the command and its description so an operator who knows what they
 * want but not what it is called can still find it. Ordering puts a prefix
 * match first — typing `/pro` should reach `/project` before `/agent apply
 * --profile`, which contains the same letters further in.
 */
export function filterCommands(commands: EngineCommand[], query: string): EngineCommand[] {
  const needle = query.trim().toLowerCase();
  if (needle === '') return commands;
  const matches = commands.filter(
    (entry) =>
      entry.command.toLowerCase().includes(needle) ||
      entry.description.toLowerCase().includes(needle),
  );
  return matches.sort((a, b) => {
    const aPrefix = a.command.toLowerCase().startsWith(needle) ? 0 : 1;
    const bPrefix = b.command.toLowerCase().startsWith(needle) ? 0 : 1;
    if (aPrefix !== bPrefix) return aPrefix - bPrefix;
    return a.command.length - b.command.length;
  });
}

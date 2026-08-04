/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { filterCommands, type EngineCommand } from './use-engine-commands';

const commands: EngineCommand[] = [
  { command: '/project', description: 'durable project registry' },
  { command: '/project list', description: 'list registered projects' },
  { command: '/agent apply --profile', description: 'apply with a profile' },
  { command: '/status', description: 'session status' },
  { command: '/help', description: 'show command help' },
];

describe('engine command palette filtering', () => {
  it('returns everything for an empty query', () => {
    expect(filterCommands(commands, '   ')).toHaveLength(commands.length);
  });

  it('puts a prefix match ahead of a mid-string match', () => {
    // 'pro' without the slash also matches '--profile' further in, which is
    // exactly the case the ordering exists to resolve.
    const result = filterCommands(commands, 'pro');
    expect(result[0].command).toBe('/project');
    expect(result.map((c) => c.command)).toContain('/agent apply --profile');
    expect(result.indexOf(result.find((c) => c.command === '/project')!)).toBeLessThan(
      result.indexOf(result.find((c) => c.command === '/agent apply --profile')!),
    );
  });

  it('matches on description so an operator can search by intent', () => {
    const result = filterCommands(commands, 'registry');
    expect(result.map((c) => c.command)).toEqual(['/project']);
  });

  it('orders shorter commands first among equal prefix matches', () => {
    const result = filterCommands(commands, '/project');
    expect(result.map((c) => c.command)).toEqual(['/project', '/project list']);
  });

  it('is case insensitive', () => {
    expect(filterCommands(commands, 'STATUS').map((c) => c.command)).toEqual(['/status']);
  });

  it('returns nothing rather than guessing when there is no match', () => {
    expect(filterCommands(commands, 'zzzz')).toEqual([]);
  });
});

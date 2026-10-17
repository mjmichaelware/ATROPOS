/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BinaryIpcSocket } from '../binary-ipc-socket';

describe('BinaryIpcSocket (WS-MIRROR-002)', () => {
  let socket: BinaryIpcSocket;

  beforeEach(() => {
    socket = new BinaryIpcSocket({
      url: 'ws://127.0.0.1:8787/',
      reconnect: false,
    });
  });

  it('starts in disconnected state', () => {
    expect(socket.getState()).toBe('disconnected');
  });

  it('tracks state change listeners', () => {
    const states: string[] = [];
    const unsubscribe = socket.onStateChange((state) => {
      states.push(state);
    });

    expect(states).toContain('disconnected');
    unsubscribe();
  });

  it('handles frame subscription and unsubscription cleanly', () => {
    const listener = vi.fn();
    const unsubscribe = socket.onFrame(listener);

    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });

  it('disconnects safely without throwing when uninitialized', () => {
    expect(() => socket.disconnect()).not.toThrow();
    expect(socket.getState()).toBe('disconnected');
  });
});

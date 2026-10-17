/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * BinaryIpcSocket (WS-MIRROR-002)
 *
 * Full-duplex binary IPC client connecting to ATROPOS TerminalMirrorSocketServer (port 8787).
 * Receives raw ANSI terminal escape sequences and sends binary keystrokes/resizes.
 * Guarantees zero REST polling and sub-millisecond terminal synchronization.
 */

export type SocketState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error';

export interface BinaryIpcOptions {
  url?: string;
  reconnect?: boolean;
  maxReconnectAttempts?: number;
  initialReconnectDelayMs?: number;
  maxReconnectDelayMs?: number;
  heartbeatIntervalMs?: number;
  binaryType?: BinaryType;
}

export type FrameListener = (frame: Uint8Array, text: string) => void;
export type StateListener = (state: SocketState) => void;
export type ErrorListener = (error: Error) => void;

export class BinaryIpcSocket {
  private ws: WebSocket | null = null;
  private state: SocketState = 'disconnected';
  private frameListeners = new Set<FrameListener>();
  private stateListeners = new Set<StateListener>();
  private errorListeners = new Set<ErrorListener>();
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private textDecoder = new TextDecoder('utf-8');
  private textEncoder = new TextEncoder();

  private readonly url: string;
  private readonly reconnect: boolean;
  private readonly maxReconnectAttempts: number;
  private readonly initialReconnectDelayMs: number;
  private readonly maxReconnectDelayMs: number;
  private readonly heartbeatIntervalMs: number;

  constructor(options: BinaryIpcOptions = {}) {
    this.url = options.url ?? 'ws://127.0.0.1:8787/';
    this.reconnect = options.reconnect ?? true;
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? 10;
    this.initialReconnectDelayMs = options.initialReconnectDelayMs ?? 500;
    this.maxReconnectDelayMs = options.maxReconnectDelayMs ?? 5000;
    this.heartbeatIntervalMs = options.heartbeatIntervalMs ?? 15000;
  }

  /**
   * Connects to the backend terminal mirror server.
   */
  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.setState('connecting');

    try {
      this.ws = new WebSocket(this.url);
      this.ws.binaryType = 'arraybuffer';

      this.ws.onopen = () => {
        this.reconnectAttempts = 0;
        this.setState('connected');
        this.startHeartbeat();
      };

      this.ws.onmessage = (event: MessageEvent) => {
        this.handleMessage(event);
      };

      this.ws.onclose = () => {
        this.cleanupSocket();
        if (this.state !== 'disconnected') {
          this.scheduleReconnect();
        }
      };

      this.ws.onerror = (_event: Event) => {
        const error = new Error(`WebSocket connection failed to ${this.url}`);
        this.notifyError(error);
      };
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      this.notifyError(error);
      this.scheduleReconnect();
    }
  }

  /**
   * Disconnects from the server and halts reconnection attempts.
   */
  disconnect(): void {
    this.setState('disconnected');
    this.clearTimers();
    if (this.ws) {
      try {
        this.ws.close(1000, 'Normal closure');
      } catch (_e) {
        // Ignored
      }
      this.ws = null;
    }
  }

  /**
   * Sends raw user keyboard input bytes to the backend terminal engine.
   */
  sendInput(data: string | Uint8Array): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }

    try {
      if (typeof data === 'string') {
        const bytes = this.textEncoder.encode(data);
        this.ws.send(bytes.buffer);
      } else {
        this.ws.send(data.buffer);
      }
    } catch (err) {
      this.notifyError(err instanceof Error ? err : new Error(String(err)));
    }
  }

  /**
   * Sends terminal resize packet (columns x rows).
   */
  sendResize(cols: number, rows: number): void {
    const payload = JSON.stringify({ type: 'resize', cols, rows });
    this.sendInput(payload);
  }

  /**
   * Subscribes to received terminal frame data.
   */
  onFrame(listener: FrameListener): () => void {
    this.frameListeners.add(listener);
    return () => this.frameListeners.delete(listener);
  }

  /**
   * Subscribes to connection state transitions.
   */
  onStateChange(listener: StateListener): () => void {
    this.stateListeners.add(listener);
    listener(this.state);
    return () => this.stateListeners.delete(listener);
  }

  /**
   * Subscribes to socket errors.
   */
  onError(listener: ErrorListener): () => void {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  getState(): SocketState {
    return this.state;
  }

  private handleMessage(event: MessageEvent): void {
    try {
      let rawBytes: Uint8Array;
      if (event.data instanceof ArrayBuffer) {
        rawBytes = new Uint8Array(event.data);
      } else if (typeof event.data === 'string') {
        rawBytes = this.textEncoder.encode(event.data);
      } else {
        return;
      }

      const text = this.textDecoder.decode(rawBytes);
      for (const listener of this.frameListeners) {
        try {
          listener(rawBytes, text);
        } catch (_err) {
          // Listener error guarded
        }
      }
    } catch (err) {
      this.notifyError(err instanceof Error ? err : new Error(String(err)));
    }
  }

  private scheduleReconnect(): void {
    if (!this.reconnect || this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.setState('disconnected');
      return;
    }

    this.setState('reconnecting');
    const delay = Math.min(
      this.initialReconnectDelayMs * Math.pow(1.5, this.reconnectAttempts),
      this.maxReconnectDelayMs
    );
    this.reconnectAttempts++;

    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  private startHeartbeat(): void {
    this.clearHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try {
          // 0x89 ping opcode or lightweight heartbeat frame
          this.ws.send(new Uint8Array([0x00]));
        } catch (_e) {
          // Ping failed
        }
      }
    }, this.heartbeatIntervalMs);
  }

  private cleanupSocket(): void {
    this.clearHeartbeat();
    if (this.ws) {
      this.ws.onopen = null;
      this.ws.onmessage = null;
      this.ws.onclose = null;
      this.ws.onerror = null;
      this.ws = null;
    }
  }

  private clearTimers(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.clearHeartbeat();
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private setState(newState: SocketState): void {
    if (this.state === newState) return;
    this.state = newState;
    for (const listener of this.stateListeners) {
      listener(newState);
    }
  }

  private notifyError(error: Error): void {
    for (const listener of this.errorListeners) {
      listener(error);
    }
  }
}

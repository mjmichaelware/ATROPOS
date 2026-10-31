/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM EX10: LIVE SHARE REAL-TIME COLLABORATIVE EDITING BUS
 * Path: apps/web/src/lib/bridge/LiveShareCollaborativeBus.ts
 *
 * Supports multi-user collaborative code editing, shared terminal viewing,
 * and remote collaborator cursor rendering over secure peer connections.
 */

export interface CollaboratorCursor {
  userId: string;
  userName: string;
  userColor: string;
  filePath: string;
  lineNumber: number;
  column: number;
  selectionEndLine?: number;
  selectionEndColumn?: number;
  lastActive: number;
}

export interface CollaborativeDeltaMessage {
  kind: 'cursor' | 'edit' | 'terminal';
  senderId: string;
  payload: unknown;
  timestamp: number;
}

export class LiveShareCollaborativeBus {
  private collaborators = new Map<string, CollaboratorCursor>();
  private messageListeners = new Set<(msg: CollaborativeDeltaMessage) => void>();
  private socket: WebSocket | null = null;

  constructor(
    private sessionId: string,
    private currentUserId: string,
    private currentUserName: string,
    private userColor: string = '#06b6d4'
  ) {}

  public connect(endpointUrl: string): void {
    if (typeof window === 'undefined') return;

    try {
      this.socket = new WebSocket(endpointUrl);
      this.socket.onmessage = (event) => {
        try {
          const delta = JSON.parse(event.data) as CollaborativeDeltaMessage;
          if (delta.senderId !== this.currentUserId) {
            if (delta.kind === 'cursor') {
              const cursor = delta.payload as CollaboratorCursor;
              this.collaborators.set(cursor.userId, cursor);
            }
            this.notifyListeners(delta);
          }
        } catch {
          // Ignore malformed frames
        }
      };
    } catch {
      // Graceful fallback for offline / disconnected modes
    }
  }

  public broadcastCursorPosition(filePath: string, line: number, column: number): void {
    const cursor: CollaboratorCursor = {
      userId: this.currentUserId,
      userName: this.currentUserName,
      userColor: this.userColor,
      filePath,
      lineNumber: line,
      column,
      lastActive: Date.now(),
    };

    const message: CollaborativeDeltaMessage = {
      kind: 'cursor',
      senderId: this.currentUserId,
      payload: cursor,
      timestamp: Date.now(),
    };

    this.send(message);
  }

  public getActiveCollaborators(): CollaboratorCursor[] {
    const now = Date.now();
    // Prune collaborators inactive for more than 30 seconds
    for (const [id, c] of this.collaborators.entries()) {
      if (now - c.lastActive > 30000) {
        this.collaborators.delete(id);
      }
    }
    return Array.from(this.collaborators.values());
  }

  public addListener(listener: (msg: CollaborativeDeltaMessage) => void): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  private send(msg: CollaborativeDeltaMessage): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(msg));
    }
  }

  private notifyListeners(msg: CollaborativeDeltaMessage): void {
    for (const listener of this.messageListeners) {
      listener(msg);
    }
  }

  public disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.collaborators.clear();
    this.messageListeners.clear();
  }
}

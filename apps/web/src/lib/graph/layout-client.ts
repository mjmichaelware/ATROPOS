import type { LayoutPosition } from "./schemas";
import type { LayoutWorkerRequest, LayoutWorkerResponse } from "./layout-types";

export type WorkerLike = {
  postMessage(message: LayoutWorkerRequest): void;
  terminate(): void;
  onmessage: ((event: MessageEvent<LayoutWorkerResponse>) => void) | null;
  onerror: ((event: ErrorEvent) => void) | null;
};

export type WorkerFactory = () => WorkerLike;

export class LayoutSupersededError extends Error {
  constructor() {
    super("Layout request superseded by a newer request");
    this.name = "LayoutSupersededError";
  }
}

/**
 * Owns the layout Web Worker lifecycle and enforces generation-based
 * supersession: only the response matching the most recently sent request
 * generation is ever used. Slower, superseded responses are discarded
 * safely rather than overwriting newer state. Accepts an injectable worker
 * factory so tests can supply a fake worker instead of a real one.
 */
export class GraphLayoutClient {
  private worker: WorkerLike | null = null;
  private generationCounter = 0;
  private latestGeneration = 0;
  private pendingResolve: ((positions: Record<string, LayoutPosition>) => void) | null = null;
  private pendingReject: ((error: Error) => void) | null = null;

  constructor(private readonly factory: WorkerFactory) {}

  nextGeneration(): number {
    this.generationCounter += 1;
    return this.generationCounter;
  }

  requestLayout(request: LayoutWorkerRequest): Promise<Record<string, LayoutPosition>> {
    this.supersedePending();
    this.latestGeneration = request.generation;
    const worker = this.ensureWorker();
    return new Promise((resolve, reject) => {
      this.pendingResolve = resolve;
      this.pendingReject = reject;
      worker.postMessage(request);
    });
  }

  terminate() {
    this.supersedePending();
    this.worker?.terminate();
    this.worker = null;
  }

  private ensureWorker(): WorkerLike {
    if (!this.worker) {
      this.worker = this.factory();
      this.worker.onmessage = (event) => this.handleMessage(event.data);
      this.worker.onerror = () => this.settle(undefined, new Error("Layout worker error"));
    }
    return this.worker;
  }

  private handleMessage(response: LayoutWorkerResponse) {
    if (response.generation !== this.latestGeneration) {
      return;
    }
    if (response.ok) {
      this.settle(response.positions, undefined);
    } else {
      this.settle(undefined, new Error(response.error));
    }
  }

  private supersedePending() {
    this.pendingReject?.(new LayoutSupersededError());
    this.pendingResolve = null;
    this.pendingReject = null;
  }

  private settle(positions: Record<string, LayoutPosition> | undefined, error: Error | undefined) {
    if (error) {
      this.pendingReject?.(error);
    } else if (positions) {
      this.pendingResolve?.(positions);
    }
    this.pendingResolve = null;
    this.pendingReject = null;
  }
}

export function createBrowserGraphLayoutWorker(): WorkerLike {
  return new Worker(new URL("../../workers/graph-layout.worker.ts", import.meta.url), { type: "module" }) as unknown as WorkerLike;
}

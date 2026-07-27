import { describe, expect, it, vi } from "vitest";
import { computeElkLayout } from "./elk-adapter";
import { GraphLayoutClient, LayoutSupersededError, type WorkerFactory, type WorkerLike } from "./layout-client";
import { buildLayoutRequest, normalizeLayoutOptions } from "./layout-normalize";
import type { LayoutWorkerRequest, LayoutWorkerResponse } from "./layout-types";
import { relationsToRendererContent } from "./transform";

const CONTENT = relationsToRendererContent([
  { id: "rel-1", from_atom_id: "atom-b", to_atom_id: "atom-a", relation_type: "REQUIRES" },
  { id: "rel-2", from_atom_id: "atom-c", to_atom_id: "atom-a", relation_type: "REFINES" },
]);

describe("layout normalization", () => {
  it("produces a deterministic request regardless of input order", () => {
    const reversed = { nodes: [...CONTENT.nodes].reverse(), edges: [...CONTENT.edges].reverse() };
    const requestA = buildLayoutRequest(CONTENT, "blueprint", 1);
    const requestB = buildLayoutRequest(reversed, "blueprint", 1);
    expect(requestA.nodes.map((n) => n.id)).toEqual(requestB.nodes.map((n) => n.id));
    expect(requestA.edges.map((e) => e.id)).toEqual(requestB.edges.map((e) => e.id));
  });

  it("normalizes direction and spacing options deterministically per algorithm", () => {
    expect(normalizeLayoutOptions("blueprint").direction).toBe("DOWN");
    expect(normalizeLayoutOptions("compact").nodeSpacing).toBeLessThan(normalizeLayoutOptions("blueprint").nodeSpacing);
  });
});

describe("ELK layout adapter", () => {
  it("produces identical output for identical normalized input", async () => {
    const request = buildLayoutRequest(CONTENT, "blueprint", 1);
    const first = await computeElkLayout(request);
    const second = await computeElkLayout(request);
    expect(first).toEqual(second);
  });

  it("positions every requested node and never returns physical negative-infinite/NaN coordinates", async () => {
    const request = buildLayoutRequest(CONTENT, "blueprint", 1);
    const positions = await computeElkLayout(request);
    for (const node of request.nodes) {
      const position = positions[node.id];
      expect(position).toBeDefined();
      expect(Number.isFinite(position.x)).toBe(true);
      expect(Number.isFinite(position.y)).toBe(true);
    }
  });

  it("returns no positions for an empty graph without error", async () => {
    const positions = await computeElkLayout({ generation: 1, algorithm: "blueprint", options: normalizeLayoutOptions("blueprint"), nodes: [], edges: [] });
    expect(positions).toEqual({});
  });
});

function createFakeWorkerFactory(behavior: (request: LayoutWorkerRequest) => LayoutWorkerResponse | "silent"): { factory: WorkerFactory; terminated: () => boolean } {
  let terminated = false;
  const factory: WorkerFactory = () => {
    const worker: WorkerLike = {
      onmessage: null,
      onerror: null,
      postMessage: (request) => {
        const outcome = behavior(request);
        if (outcome === "silent") return;
        queueMicrotask(() => worker.onmessage?.({ data: outcome } as unknown as MessageEvent<LayoutWorkerResponse>));
      },
      terminate: () => {
        terminated = true;
      },
    };
    return worker;
  };
  return { factory, terminated: () => terminated };
}

describe("GraphLayoutClient worker protocol", () => {
  it("resolves with the positions from a matching-generation response", async () => {
    const { factory } = createFakeWorkerFactory((request) => ({ generation: request.generation, ok: true, positions: { n1: { x: 1, y: 2 } } }));
    const client = new GraphLayoutClient(factory);
    const request = buildLayoutRequest(CONTENT, "blueprint", client.nextGeneration());
    await expect(client.requestLayout(request)).resolves.toEqual({ n1: { x: 1, y: 2 } });
  });

  it("discards a stale response whose generation does not match the latest request", async () => {
    let capturedFirstWorker: WorkerLike | undefined;
    const factory: WorkerFactory = () => {
      const worker: WorkerLike = { onmessage: null, onerror: null, postMessage: vi.fn(), terminate: vi.fn() };
      capturedFirstWorker ??= worker;
      return worker;
    };
    const client = new GraphLayoutClient(factory);
    const first = buildLayoutRequest(CONTENT, "blueprint", client.nextGeneration());
    const firstPromise = client.requestLayout(first).catch((error: Error) => error);
    const second = buildLayoutRequest(CONTENT, "blueprint", client.nextGeneration());
    const secondPromise = client.requestLayout(second);

    capturedFirstWorker?.onmessage?.({ data: { generation: first.generation, ok: true, positions: { stale: { x: 0, y: 0 } } } } as unknown as MessageEvent<LayoutWorkerResponse>);
    capturedFirstWorker?.onmessage?.({ data: { generation: second.generation, ok: true, positions: { fresh: { x: 9, y: 9 } } } } as unknown as MessageEvent<LayoutWorkerResponse>);

    await expect(secondPromise).resolves.toEqual({ fresh: { x: 9, y: 9 } });
    const firstResult = await firstPromise;
    expect(firstResult).toBeInstanceOf(LayoutSupersededError);
  });

  it("rejects with a safe recoverable error on worker failure and preserves no stale state", async () => {
    const { factory } = createFakeWorkerFactory(() => "silent");
    const client = new GraphLayoutClient(factory);
    const request = buildLayoutRequest(CONTENT, "blueprint", client.nextGeneration());
    const promise = client.requestLayout(request);
    // Simulate an onerror event manually since our fake never responds.
    const worker = (client as unknown as { worker: WorkerLike }).worker;
    worker.onerror?.({} as ErrorEvent);
    await expect(promise).rejects.toThrow("Layout worker error");
  });

  it("terminates the underlying worker on cleanup", async () => {
    const { factory, terminated } = createFakeWorkerFactory(() => "silent");
    const client = new GraphLayoutClient(factory);
    const pending = client.requestLayout(buildLayoutRequest(CONTENT, "blueprint", client.nextGeneration())).catch((error: Error) => error);
    client.terminate();
    expect(terminated()).toBe(true);
    expect(await pending).toBeInstanceOf(LayoutSupersededError);
  });
});

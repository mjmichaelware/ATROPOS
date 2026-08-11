import { computeElkLayout } from "@/lib/graph/elk-adapter";
import type { LayoutWorkerRequest, LayoutWorkerResponse } from "@/lib/graph/layout-types";

self.onmessage = async (event: MessageEvent<LayoutWorkerRequest>) => {
  const request = event.data;
  try {
    const positions = await computeElkLayout(request);
    const response: LayoutWorkerResponse = { generation: request.generation, ok: true, positions };
    self.postMessage(response);
  } catch (error) {
    const response: LayoutWorkerResponse = {
      generation: request.generation,
      ok: false,
      error: error instanceof Error ? error.message : "Layout failed",
    };
    self.postMessage(response);
  }
};

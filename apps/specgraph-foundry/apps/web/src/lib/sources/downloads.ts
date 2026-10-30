import { safeArtifactName } from "@/lib/handoff/downloads";

/**
 * Turns a base64-encoded file (as returned by GET
 * /v1/documents/{id}/atoms/export) into a real device download. The API
 * only ever returns JSON, so there is no signed-URL path here like the
 * plan-export flow - the bytes already arrived in the response body and
 * just need to be handed to the browser as a Blob.
 */
export function downloadBase64File(
  filename: unknown,
  mediaType: string,
  base64: string,
  triggerImpl: (blob: Blob, name: string) => void = defaultTrigger,
): boolean {
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    const blob = new Blob([bytes], { type: mediaType });
    triggerImpl(blob, safeArtifactName(filename));
    return true;
  } catch {
    return false;
  }
}

function defaultTrigger(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

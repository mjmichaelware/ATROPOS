import { readPublicEnv } from "@/lib/config/client-env";
import { validateSignedUploadUrl } from "./security";

export type UploadProgress = {
  loaded: number;
  total: number;
  percent: number;
};

export function uploadToSignedUrl(
  signedUrl: string,
  file: File,
  headers: Record<string, string>,
  onProgress: (progress: UploadProgress) => void,
  signal?: AbortSignal,
) {
  const env = readPublicEnv();
  const target = validateSignedUploadUrl(signedUrl, env.NEXT_PUBLIC_SUPABASE_URL);
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("PUT", target.toString());
    for (const [name, value] of Object.entries(headers)) {
      request.setRequestHeader(name, value);
    }
    request.upload.onprogress = (event) => {
      const total = event.total || file.size;
      onProgress({ loaded: event.loaded, total, percent: total ? Math.round((event.loaded / total) * 100) : 0 });
    };
    request.onerror = () => reject(new Error("Signed upload failed"));
    request.onabort = () => reject(new DOMException("Upload aborted", "AbortError"));
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        resolve();
      } else {
        reject(new Error("Signed upload was rejected"));
      }
    };
    signal?.addEventListener("abort", () => request.abort(), { once: true });
    request.send(file);
  });
}

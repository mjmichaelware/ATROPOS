/* SPDX-License-Identifier: AGPL-3.0-only */

import { readEngine, writeEngine, engineBaseUrl, type EngineFailure } from '@/lib/engine/client';

/**
 * Client for the bridge files API.
 *
 * `ADD-W-029`: @file upload → files API; show content + envelope hashes.
 * The engine returns the SHA-256 and size for each uploaded file.
 */

export interface UploadedFile {
  filename: string;
  sha256: string;
  size: number;
}

export interface FilesListPayload {
  count: number;
  files: Array<{
    filename: string;
    size: number;
    sha256: string;
  }>;
}

export interface UploadedFileResult {
  ok: true;
  filename: string;
  sha256: string;
  size: number;
}

export type FilesResult<T> =
  | { ok: true; data: T }
  | ({ ok: false } & Omit<EngineFailure, 'ok'>);

/**
 * Upload a file to the session's upload folder.
 *
 * The engine expects a Base64-encoded body and returns the SHA-256
 * and size of the stored file.
 */
export async function uploadFile(
  sessionId: string,
  filename: string,
  file: File
): Promise<FilesResult<UploadedFile>> {
  // Validate filename locally before sending
  if (!/^[a-zA-Z0-9_.-]+\.[a-zA-Z0-9]+$/.test(filename)) {
    return {
      ok: false,
      reason: 'malformed-response',
      detail: 'Filename must be a portable identifier with extension.',
      remedy: 'Use only alphanumeric, underscore, hyphen, and dot characters with an extension.',
    };
  }

  // Read file as Base64
  const base64 = await fileToBase64(file);

  const result = await writeEngine<{ ok: true; filename: string; sha256: string; size: number }>(
    `${engineBaseUrl()}/v1/files?session=${encodeURIComponent(sessionId)}&filename=${encodeURIComponent(filename)}`,
    { content: base64 }
  );

  if (!result.ok) {
    return {
      ok: false,
      reason: result.reason ?? 'bridge-refused',
      detail: result.detail ?? 'Upload rejected',
      remedy: result.remedy ?? 'Check filename and session; ensure session exists.',
    };
  }

  return {
    ok: true,
    data: {
      filename: result.data.filename,
      sha256: result.data.sha256,
      size: result.data.size,
    },
  };
}

/**
 * List files in a session's upload folder.
 */
export async function listFiles(sessionId: string): Promise<FilesResult<FilesListPayload>> {
  const result = await readEngine<FilesListPayload>(
    `${engineBaseUrl()}/v1/files?session=${encodeURIComponent(sessionId)}`
  );
  return result;
}

/**
 * Convert a File to a Base64 string.
 */
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      // Remove the data: prefix if present
      const base64 = result.split(',')[1] ?? result;
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * The session ID used for file uploads.
 * In a real implementation this would come from the session context.
 * For now, we derive it from the current conversation or use a default.
 */
export function getDefaultSessionId(): string {
  // In a real app, this would come from the active session
  // For now, use a default that matches the bridge's session folder
  return 'default';
}

/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * @file upload component (ADD-W-029).
 *
 * Renders a file picker that uploads to /v1/files and shows the
 * returned envelope hash (SHA-256) and size. The component is
 * designed to be used in the composer or as a standalone action.
 *
 * The engine returns the SHA-256 and size for each uploaded file,
 * which are displayed as an attested envelope.
 */

'use client';

import { useCallback, useState } from 'react';
import { uploadFile, listFiles, getDefaultSessionId, type UploadedFile } from '@/lib/files/client';

interface FileUploadResult {
  filename: string;
  sha256: string;
  size: number;
}

export function FileUpload({
  sessionId = getDefaultSessionId(),
  onUpload,
}: {
  sessionId?: string;
  onUpload?: (result: FileUploadResult) => void;
}) {
  const [uploading, setUploading] = useState<string | null>(null);
  const [results, setResults] = useState<FileUploadResult[]>([]);
  const [error, setError] = useState<string | null>(null);

  const handleFileSelect = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file) return;

      setError(null);
      setUploading(file.name);

      // Import the client dynamically to avoid SSR issues
      const { uploadFile } = await import('@/lib/files/client');

      const result = await uploadFile(sessionId, file.name, file);
      setUploading(null);

      if (!result.ok) {
        setError(`${result.detail} ${result.remedy}`);
        return;
      }

      const uploadResult = {
        filename: result.data.filename,
        sha256: result.data.sha256,
        size: result.data.size,
      };

      setResults((prev) => [...prev, uploadResult]);
      onUpload?.(uploadResult);
    },
    [sessionId]
  );

  return (
    <div className="wb-file-upload" data-testid="file-upload">
      <label className="wb-file-upload-label">
        <input
          type="file"
          className="wb-file-upload-input"
          onChange={handleFileSelect}
          disabled={uploading !== null}
          accept=".txt,.md,.json,.yaml,.yml,.toml,.py,.js,.ts,.tsx,.kt,.java,.go,.rs,.cpp,.h,.c,.sh,.sql,.xml,.html,.css"
        />
        <span className="wb-file-upload-text">
          {uploading ? `Uploading ${uploading}…` : 'Choose file to upload'}
        </span>
      </label>

      {error && (
        <p role="alert" className="wb-fault text-sm">
          {error}
        </p>
      )}

      {results.length > 0 && (
        <ul className="wb-upload-results space-y-2" aria-label="Uploaded files">
          {results.map((result, idx) => (
            <li key={`${result.filename}-${idx}`} className="wb-upload-result">
              <div className="flex items-baseline gap-2 flex-wrap">
                <span className="font-mono text-sm text-sg-neutral-900 dark:text-sg-neutral-100">
                  {result.filename}
                </span>
                <span className="wb-upload-size text-xs text-sg-neutral-500">
                  {(result.size / 1024).toFixed(1)} KB
                </span>
                <button
                  type="button"
                  className="wb-upload-copy text-xs underline underline-offset-2 text-sg-neutral-600 hover:text-sg-neutral-900 dark:text-sg-neutral-400 dark:hover:text-sg-neutral-100"
                  onClick={() => navigator.clipboard.writeText(result.sha256)}
                  aria-label="Copy SHA-256"
                >
                  Copy hash
                </button>
              </div>
              <div className="wb-upload-hash font-mono text-xs text-sg-neutral-500 break-all">
                sha256:{result.sha256}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * Hook to get the most recent upload result for composer insertion.
 */
export function useLatestUpload(): FileUploadResult | null {
  // This would be connected to a context in a real implementation
  // For now, returns null
  return null;
}

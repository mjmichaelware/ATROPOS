/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React, { useState, useRef, DragEvent, ChangeEvent } from 'react';

/**
 * ADD-W-029: Attested Multi-Format File Upload and Envelope Validator
 * Section 1.9 mandate: Passing context files to agents produces an attested,
 * content-addressed envelope sealed into CAS.
 */

export interface AttestedFileEnvelope {
  filename: string;
  mediaType: string;
  sizeBytes: number;
  sha256Hex: string;
  sessionId: string;
  uploadedAt: string;
}

export interface AttestedFileUploadProps {
  sessionId: string;
  onFileAttested: (envelope: AttestedFileEnvelope) => void;
  onUploadError: (errorMessage: string) => void;
}

const SUPPORTED_EXTENSIONS = ['.txt', '.md', '.json', '.yaml', '.yml', '.docx', '.pdf', '.png', '.jpg', '.jpeg'];
const MAX_BYTES = 10 * 1024 * 1024; // 10MB ceiling

export function AttestedFileEnvelopeView({
  sessionId,
  onFileAttested,
  onUploadError,
}: AttestedFileUploadProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [lastEnvelope, setLastEnvelope] = useState<AttestedFileEnvelope | null>(null);
  const [copied, setCopied] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const validateFile = (file: File): string | null => {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!SUPPORTED_EXTENSIONS.includes(ext)) {
      return `Unsupported format '${ext}'. Allowed: ${SUPPORTED_EXTENSIONS.join(', ')}`;
    }
    if (file.size > MAX_BYTES) {
      return `File exceeds 10MB limit (${(file.size / (1024 * 1024)).toFixed(2)} MB)`;
    }
    return null;
  };

  const processFile = async (file: File) => {
    const validationError = validateFile(file);
    if (validationError) {
      onUploadError(validationError);
      return;
    }

    setIsUploading(true);
    setUploadProgress(20);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('sessionId', sessionId);

      setUploadProgress(50);
      const res = await fetch('/v1/files', {
        method: 'POST',
        body: formData,
      });

      setUploadProgress(80);
      if (!res.ok) {
        throw new Error(`Upload failed with status ${res.status}`);
      }

      const envelope: AttestedFileEnvelope = await res.json();
      setUploadProgress(100);
      setLastEnvelope(envelope);
      onFileAttested(envelope);
    } catch (err: unknown) {
      const message = (err as Error).message || 'File upload error';
      onUploadError(message);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      void processFile(file);
    }
  };

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      void processFile(file);
    }
  };

  const copyDigest = () => {
    if (!lastEnvelope) return;
    const token = `@file:${lastEnvelope.filename} [sha256:${lastEnvelope.sha256Hex}]`;
    navigator.clipboard.writeText(token);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-white/10 bg-[#07100D] p-5 text-white font-mono shadow-2xl">
      <div className="border-b border-white/10 pb-3">
        <h3 className="text-base font-bold text-emerald-400">ATTESTED FILE UPLOAD</h3>
        <p className="text-xs text-slate-400">Section 1.9 Content-Addressed CAS Envelope Validator</p>
      </div>

      {/* Drag & Drop Intake Zone */}
      <div
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={() => fileInputRef.current?.click()}
        className={`flex flex-col items-center justify-center p-8 rounded-lg border-2 border-dashed cursor-pointer transition-all ${
          isDragging
            ? 'border-emerald-400 bg-emerald-950/20'
            : 'border-white/10 bg-slate-900/40 hover:border-emerald-500/40'
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          onChange={handleFileChange}
          accept={SUPPORTED_EXTENSIONS.join(',')}
        />
        <span className="text-xs font-semibold text-slate-200">
          {isDragging ? 'Drop file to attest...' : 'Drop context files here or click to browse'}
        </span>
        <span className="text-[10px] text-slate-500 mt-1">
          Supported: .txt, .md, .json, .yaml, .docx, .pdf, .png, .jpg (Max 10MB)
        </span>
      </div>

      {/* Upload Progress Bar */}
      {isUploading && (
        <div className="flex flex-col gap-1">
          <div className="flex justify-between text-xs text-slate-400">
            <span>Uploading & Attesting Digest...</span>
            <span>{uploadProgress}%</span>
          </div>
          <div className="h-1.5 w-full rounded bg-slate-800 overflow-hidden">
            <div
              className="h-full bg-emerald-500 transition-all duration-300"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
        </div>
      )}

      {/* Attested Hash Preview & Token Extraction */}
      {lastEnvelope && (
        <div className="rounded-lg border border-emerald-900/50 bg-emerald-950/20 p-4 flex flex-col gap-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-bold text-emerald-300">ATTESTED CAS ENVELOPE</span>
            <span className="text-[10px] text-slate-400">{(lastEnvelope.sizeBytes / 1024).toFixed(1)} KB</span>
          </div>

          <div className="flex flex-col gap-1">
            <span className="text-[10px] text-slate-400">FILENAME</span>
            <span className="text-slate-200">{lastEnvelope.filename}</span>
          </div>

          <div className="flex flex-col gap-1">
            <span className="text-[10px] text-slate-400">SHA-256 DIGEST</span>
            <span className="text-emerald-400 select-all break-all">{lastEnvelope.sha256Hex}</span>
          </div>

          <div className="mt-2 flex items-center justify-between border-t border-white/5 pt-2">
            <span className="text-[10px] text-slate-400">Composer reference ready</span>
            <button
              onClick={copyDigest}
              className="px-3 py-1 rounded bg-emerald-800 hover:bg-emerald-700 text-xs font-bold text-white transition-all"
            >
              {copied ? 'Copied Token!' : 'Copy @file Token'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

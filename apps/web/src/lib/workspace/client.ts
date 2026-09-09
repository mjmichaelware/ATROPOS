/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Client for the workspace project tree and file operations (F-WEB-004, F-WEB-005).
 *
 * These routes bridge the editor to the operator's workspace. They are
 * separate from the session `/v1/files` endpoint (which serves uploads) and
 * operate on the actual project tree. Reads and writes cross the bridge so
 * the surface never touches the operator's filesystem directly.
 */

import { readEngine, writeEngine, engineBaseUrl } from '@/lib/engine/client';

export interface WorkspaceTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  size?: number;
  children?: WorkspaceTreeNode[];
}

export interface WorkspaceTreePayload {
  ok: true;
  tree: WorkspaceTreeNode[];
}

export interface WorkspaceFilePayload {
  ok: true;
  path: string;
  content: string;
  encoding: 'utf-8';
}

export interface WorkspaceWriteResult {
  ok: true;
  path: string;
  bytesWritten: number;
  sha256: string;
}

/**
 * Reads the project file tree from the bridge.
 */
export async function readWorkspaceTree(): Promise<WorkspaceTreePayload> {
  const result = await readEngine<WorkspaceTreePayload>('/v1/workspace/tree');
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}

/**
 * Reads a single file from the project.
 */
export async function readWorkspaceFile(path: string): Promise<WorkspaceFilePayload> {
  const encodedPath = encodeURIComponent(path);
  const result = await readEngine<WorkspaceFilePayload>(`/v1/workspace/file?path=${encodedPath}`);
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}

/**
 * Writes a file to the project through the bridge.
 */
export async function writeWorkspaceFile(path: string, content: string): Promise<WorkspaceWriteResult> {
  const result = await writeEngine<WorkspaceWriteResult>('/v1/workspace/file', { path, content });
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}

/**
 * Checks if a path is within the operator's declared territory.
 */
export interface TerritoryCheckResult {
  ok: true;
  inside: boolean;
  path: string;
  assignment?: {
    owner: string;
    path: string;
  };
}

export async function checkTerritoryMembership(path: string): Promise<TerritoryCheckResult> {
  const encodedPath = encodeURIComponent(path);
  const result = await readEngine<TerritoryCheckResult>(`/v1/territory/check?path=${encodedPath}`);
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}

/**
 * Lists all territory assignments.
 */
export interface TerritoryAssignment {
  owner: string;
  path: string;
  recursive: boolean;
}

export interface TerritoryAssignmentsPayload {
  ok: true;
  assignments: TerritoryAssignment[];
}

export async function listTerritoryAssignments(): Promise<TerritoryAssignmentsPayload> {
  const result = await readEngine<TerritoryAssignmentsPayload>('/v1/territory');
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}

/**
 * Lists territory violations.
 */
export interface TerritoryViolation {
  path: string;
  owner: string;
  reason: string;
}

export interface TerritoryViolationsPayload {
  ok: true;
  violations: TerritoryViolation[];
}

export async function listTerritoryViolations(): Promise<TerritoryViolationsPayload> {
  const result = await readEngine<TerritoryViolationsPayload>('/v1/territory/violations');
  if (!result.ok) {
    throw new Error(result.detail);
  }
  return result.data;
}
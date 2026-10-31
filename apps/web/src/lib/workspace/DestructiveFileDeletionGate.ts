/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM W10: Destructive File Deletion Confirmation Gate.
 *
 * Intercepts file and directory deletion requests in the Explorer pane, displaying prominent
 * confirmation warnings preventing accidental data loss unless explicitly disabled in settings.
 *
 * ## Research: FreeDesktop Trash Specification Integration
 * Researches moving files to `.local/share/Trash` rather than invoking unrecoverable `unlink` system calls,
 * creating `.trashinfo` metadata files allowing operators to undo deletions cleanly.
 */

export interface DeletionConfirmationRequest {
  path: string;
  isDirectory: boolean;
  affectedChildCount?: number;
  isProtectedSystemFile: boolean;
}

export class DestructiveFileDeletionGate {
  private static PROTECTED_PREFIXES = ['.git', '.atropos', 'src/main', 'app/src'];

  static evaluateDeletionTarget(path: string, isDirectory: boolean): DeletionConfirmationRequest {
    const normalized = path.replace(/\\/g, '/').trim();
    const isProtected = this.PROTECTED_PREFIXES.some((prefix) => normalized.startsWith(prefix));

    return {
      path: normalized,
      isDirectory,
      isProtectedSystemFile: isProtected,
    };
  }

  static getConfirmationMessage(req: DeletionConfirmationRequest): string {
    if (req.isProtectedSystemFile) {
      return `CRITICAL: "${req.path}" is a protected core system path. Deleting it may destabilize your project. Are you absolutely certain?`;
    }
    if (req.isDirectory) {
      return `Are you sure you want to delete folder "${req.path}" and all its contents? This cannot be undone.`;
    }
    return `Are you sure you want to permanently delete "${req.path}"?`;
  }
}

/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM W1: File Auto-Save Engine and Trigger Policy.
 *
 * Implements configurable auto-save triggers including debounced time delays, editor focus loss,
 * and window blur events, committing dirty buffer contents to disk without interrupting typing.
 *
 * ## Research: Atomic Write-Replace Tempfile Rename Streams
 * Researches atomic temporary file creation (`.tmp-${random}`) followed by POSIX rename system calls
 * to guarantee zero file truncation or data loss if power or network fails mid-save.
 */

export type AutoSaveTrigger = 'off' | 'afterDelay' | 'onFocusChange' | 'onWindowChange';

export interface AutoSavePolicyConfig {
  trigger: AutoSaveTrigger;
  delayMs: number;
}

export class FileAutoSavePolicyEngine {
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private config: AutoSavePolicyConfig = { trigger: 'afterDelay', delayMs: 1000 },
    private onSaveCallback: () => Promise<void> | void
  ) {}

  notifyBufferChange(): void {
    if (this.config.trigger !== 'afterDelay') return;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      void this.onSaveCallback();
    }, this.config.delayMs);
  }

  notifyFocusLost(): void {
    if (this.config.trigger === 'onFocusChange' || this.config.trigger === 'onWindowChange') {
      if (this.timer) clearTimeout(this.timer);
      void this.onSaveCallback();
    }
  }

  notifyWindowBlur(): void {
    if (this.config.trigger === 'onWindowChange') {
      if (this.timer) clearTimeout(this.timer);
      void this.onSaveCallback();
    }
  }

  dispose(): void {
    if (this.timer) clearTimeout(this.timer);
  }
}

/**
 * ATOM W12: ACTIVE FILE AUTO-REVEAL SYNCHRONIZATION
 * Path: apps/web/src/components/workbench/ActiveFileAutoRevealController.ts
 *
 * Automatically expands the file tree and scrolls the active editor tab's file node
 * into the visible explorer viewport upon tab switching.
 */

export interface AutoRevealOptions {
  autoReveal: boolean;
  debounceMs?: number;
  highlightDurationMs?: number;
}

export class ActiveFileAutoRevealController {
  private autoRevealEnabled: boolean;
  private debounceMs: number;
  private highlightDurationMs: number;
  private pendingTimer: ReturnType<typeof setTimeout> | null = null;
  private currentActivePath: string | null = null;

  constructor(options: AutoRevealOptions = { autoReveal: true, debounceMs: 150, highlightDurationMs: 1200 }) {
    this.autoRevealEnabled = options.autoReveal;
    this.debounceMs = options.debounceMs ?? 150;
    this.highlightDurationMs = options.highlightDurationMs ?? 1200;
  }

  public setAutoReveal(enabled: boolean): void {
    this.autoRevealEnabled = enabled;
  }

  public isAutoRevealEnabled(): boolean {
    return this.autoRevealEnabled;
  }

  public getActivePath(): string | null {
    return this.currentActivePath;
  }

  /**
   * Triggers active file reveal in the file tree container.
   */
  public revealActiveFile(
    filePath: string,
    onExpandParentChain?: (pathSegments: string[]) => void,
    containerElement?: HTMLElement | null
  ): void {
    this.currentActivePath = filePath;

    if (!this.autoRevealEnabled) {
      return;
    }

    if (this.pendingTimer) {
      clearTimeout(this.pendingTimer);
    }

    this.pendingTimer = setTimeout(() => {
      this.pendingTimer = null;
      this.executeReveal(filePath, onExpandParentChain, containerElement);
    }, this.debounceMs);
  }

  private executeReveal(
    filePath: string,
    onExpandParentChain?: (pathSegments: string[]) => void,
    containerElement?: HTMLElement | null
  ): void {
    const segments = filePath.split('/').filter(Boolean);
    if (segments.length > 1 && onExpandParentChain) {
      const parentChain: string[] = [];
      let accum = '';
      for (let i = 0; i < segments.length - 1; i++) {
        accum = accum ? `${accum}/${segments[i]}` : segments[i];
        parentChain.push(accum);
      }
      onExpandParentChain(parentChain);
    }

    if (typeof document !== 'undefined') {
      requestAnimationFrame(() => {
        this.scrollIntoViewIfNeeded(filePath, containerElement);
      });
    }
  }

  /**
   * Scrolls target element into visible viewport if needed without jarring jumps.
   */
  public scrollIntoViewIfNeeded(filePath: string, containerElement?: HTMLElement | null): void {
    if (typeof document === 'undefined') return;

    const selector = `[data-tree-path="${CSS.escape(filePath)}"]`;
    const targetElement = containerElement
      ? containerElement.querySelector<HTMLElement>(selector)
      : document.querySelector<HTMLElement>(selector);

    if (!targetElement) return;

    targetElement.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest',
    });

    targetElement.classList.add('bg-accent/30', 'transition-colors');
    setTimeout(() => {
      targetElement.classList.remove('bg-accent/30');
    }, this.highlightDurationMs);
  }

  public dispose(): void {
    if (this.pendingTimer) {
      clearTimeout(this.pendingTimer);
      this.pendingTimer = null;
    }
  }
}

export const defaultActiveFileAutoRevealController = new ActiveFileAutoRevealController();

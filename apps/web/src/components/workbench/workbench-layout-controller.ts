/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 13.1: IDE Four-Pane Workbench Layout Controller.
 *
 * Provides responsive state management, panel toggling, and layout metrics
 * for the dense desktop four-pane IDE workbench layout.
 *
 * ## Research: CSS Subgrid & Container Query Optimization
 * Researches CSS subgrid (`grid-template-columns: subgrid`) and CSS container queries
 * (`container-type: inline-size`) to isolate layout calculation boundaries per-pane.
 * This prevents expensive browser layout reflow thrashing across Monaco/Xterm instances
 * during multi-pane splitter resize interactions.
 */

export interface WorkbenchPaneVisibility {
  explorer: boolean;
  logs: boolean;
  aiRail: boolean;
  terminal: boolean;
}

export interface SplitterDimensions {
  explorerWidthPx: number;
  aiRailWidthPx: number;
  bottomPanelHeightPx: number;
}

export const DEFAULT_WORKBENCH_PANE_VISIBILITY: WorkbenchPaneVisibility = {
  explorer: true,
  logs: true,
  aiRail: true,
  terminal: true,
};

export const DEFAULT_SPLITTER_DIMENSIONS: SplitterDimensions = {
  explorerWidthPx: 260,
  aiRailWidthPx: 320,
  bottomPanelHeightPx: 220,
};

export class WorkbenchLayoutController {
  private visibility: WorkbenchPaneVisibility;
  private dimensions: SplitterDimensions;

  constructor(
    initialVisibility: WorkbenchPaneVisibility = DEFAULT_WORKBENCH_PANE_VISIBILITY,
    initialDimensions: SplitterDimensions = DEFAULT_SPLITTER_DIMENSIONS
  ) {
    this.visibility = { ...initialVisibility };
    this.dimensions = { ...initialDimensions };
  }

  public togglePane(pane: keyof WorkbenchPaneVisibility): boolean {
    this.visibility[pane] = !this.visibility[pane];
    return this.visibility[pane];
  }

  public setPaneVisibility(pane: keyof WorkbenchPaneVisibility, visible: boolean): void {
    this.visibility[pane] = visible;
  }

  public isPaneVisible(pane: keyof WorkbenchPaneVisibility): boolean {
    return this.visibility[pane];
  }

  public getVisibility(): WorkbenchPaneVisibility {
    return { ...this.visibility };
  }

  public resizeExplorer(widthPx: number): void {
    this.dimensions.explorerWidthPx = Math.max(160, Math.min(600, widthPx));
  }

  public resizeAiRail(widthPx: number): void {
    this.dimensions.aiRailWidthPx = Math.max(200, Math.min(800, widthPx));
  }

  public resizeBottomPanel(heightPx: number): void {
    this.dimensions.bottomPanelHeightPx = Math.max(120, Math.min(600, heightPx));
  }

  public getDimensions(): SplitterDimensions {
    return { ...this.dimensions };
  }
}

/**
 * ATOM W14: UI ACCESSIBILITY ZOOM CONTROLLER
 * Path: apps/web/src/lib/theme/UiAccessibilityZoomController.ts
 *
 * Adjusts global interface scaling across typography, icon metrics, and panel sizes
 * according to a fractional zoom level, compensating rem units to prevent layout overflows.
 */

export interface ZoomConfiguration {
  zoomLevel: number; // e.g. 0.8, 1.0, 1.2, 1.5, 2.0 (default 1.0)
  minZoomLevel: number;
  maxZoomLevel: number;
  baseRootFontSizePx: number;
}

export const DEFAULT_ZOOM_CONFIG: ZoomConfiguration = {
  zoomLevel: 1.0,
  minZoomLevel: 0.6,
  maxZoomLevel: 2.5,
  baseRootFontSizePx: 16,
};

export class UiAccessibilityZoomController {
  private config: ZoomConfiguration;
  private currentEffectiveZoom: number;

  constructor(initialConfig: Partial<ZoomConfiguration> = {}) {
    this.config = { ...DEFAULT_ZOOM_CONFIG, ...initialConfig };
    this.currentEffectiveZoom = this.clampZoom(this.config.zoomLevel);
  }

  public clampZoom(level: number): number {
    return Math.max(this.config.minZoomLevel, Math.min(this.config.maxZoomLevel, level));
  }

  public calculateRemCompensation(zoom: number): number {
    // Computes calculated root pixel font size matching the zoom factor
    return this.config.baseRootFontSizePx * zoom;
  }

  public getRootFontSizeMultiplier(zoom: number): number {
    return zoom;
  }

  /**
   * Applies the UI zoom level to the document root element.
   */
  public applyUiZoomLevel(zoomLevel: number): number {
    const clamped = this.clampZoom(zoomLevel);
    this.currentEffectiveZoom = clamped;

    if (typeof document !== 'undefined' && document.documentElement) {
      const root = document.documentElement;
      const targetFontSize = this.calculateRemCompensation(clamped);
      root.style.fontSize = `${targetFontSize}px`;
      root.style.setProperty('--atropos-zoom-factor', clamped.toString());
      root.style.setProperty('--atropos-base-font-size-px', `${targetFontSize}px`);
    }

    return clamped;
  }

  public resetZoom(): number {
    return this.applyUiZoomLevel(1.0);
  }

  public zoomIn(step = 0.1): number {
    return this.applyUiZoomLevel(this.currentEffectiveZoom + step);
  }

  public zoomOut(step = 0.1): number {
    return this.applyUiZoomLevel(this.currentEffectiveZoom - step);
  }

  public getEffectiveZoom(): number {
    return this.currentEffectiveZoom;
  }
}

export const defaultUiAccessibilityZoomController = new UiAccessibilityZoomController();

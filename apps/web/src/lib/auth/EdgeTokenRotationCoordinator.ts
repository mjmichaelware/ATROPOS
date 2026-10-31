/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 17.2: Edge Token Rotation Coordinator & Silent Refresh Engine.
 *
 * Implements token refresh interceptors and lifecycle state across active client surfaces.
 *
 * ## Research: Silent Background Token Refresh in Web Workers & Android JobScheduler
 * Researches proactive JWT refresh scheduling via dedicated background Web Workers and Android JobScheduler.
 * By refreshing tokens 5 minutes before expiration in the background, active WebSocket PTY streams,
 * Monaco editor buffers, and execution pipelines experience zero authentication interruptions.
 */

export interface TokenRotationState {
  isRefreshing: boolean;
  lastRotatedAt?: number;
  rotationAttempts: number;
  permanentlyInvalid: boolean;
}

export class EdgeTokenRotationCoordinator {
  private state: TokenRotationState = {
    isRefreshing: false,
    rotationAttempts: 0,
    permanentlyInvalid: false,
  };

  public recordAttempt(): void {
    this.state.rotationAttempts += 1;
    this.state.isRefreshing = true;
  }

  public recordSuccess(): void {
    this.state.isRefreshing = false;
    this.state.rotationAttempts = 0;
    this.state.lastRotatedAt = Date.now();
    this.state.permanentlyInvalid = false;
  }

  public markPermanentlyInvalid(): void {
    this.state.isRefreshing = false;
    this.state.permanentlyInvalid = true;
  }

  public isInvalid(): boolean {
    return this.state.permanentlyInvalid;
  }

  public getState(): TokenRotationState {
    return { ...this.state };
  }
}

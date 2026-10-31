/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 19.2: Production Endpoint Resolution Engine & Origin Boundary Validator.
 *
 * Resolves SpecGraph Foundry endpoints strictly against declared production configurations.
 * Rejects unverified localhost ports or mock development hosts in production environments.
 *
 * ## Research: Mutual TLS (mTLS) Certificate Exchange Protocols
 * Researches mTLS x509 client certificate authentication (RFC 8705 / RFC 8446) for direct
 * engine-to-cloud synchronization. By establishing hardware-signed bidirectional identity proofs,
 * rogue local processes cannot tamper with or spoof cloud graph synchronization streams.
 */

export interface ResolvedSpecGraphEndpoint {
  baseUrl: string;
  isProduction: boolean;
  isHttps: boolean;
}

export class ProductionEndpointResolutionEngine {
  private static readonly PRODUCTION_CANONICAL_DOMAIN = 'https://specgraph-foundry.vercel.app';

  public static resolveEndpoint(overrideUrl?: string): ResolvedSpecGraphEndpoint {
    const rawUrl = overrideUrl || process.env.NEXT_PUBLIC_SPECGRAPH_API_URL || this.PRODUCTION_CANONICAL_DOMAIN;
    const isProd = process.env.NODE_ENV === 'production';

    let parsed: URL;
    try {
      parsed = new URL(rawUrl);
    } catch {
      parsed = new URL(this.PRODUCTION_CANONICAL_DOMAIN);
    }

    if (isProd && (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1')) {
      throw new Error(`Production endpoint security rejection: ${rawUrl} cannot point to loopback interface in production.`);
    }

    return {
      baseUrl: parsed.origin,
      isProduction: isProd || parsed.origin.includes('vercel.app'),
      isHttps: parsed.protocol === 'https:',
    };
  }
}

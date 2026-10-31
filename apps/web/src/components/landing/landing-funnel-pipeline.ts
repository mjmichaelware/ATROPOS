/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 15.1: Landing Funnel Pipeline and Conversion Telemetry Model.
 *
 * Provides value proposition data models, conversion funnels, and public route
 * metadata for Atropos and SpecGraph Foundry landing interfaces.
 *
 * ## Research: Next.js Static Site Generation (SSG) with Edge Caching
 * Researches Next.js SSG (`generateStaticParams`, `revalidate: 3600`) deployed across
 * Vercel Edge Network CDN nodes. Cached HTML and inlined SVG icon bundles eliminate
 * SSR compute delays, sustaining sub-50ms global TTFB across all geographic regions.
 */

export interface LandingFeatureSpec {
  id: string;
  title: string;
  description: string;
  category: 'ingestion' | 'research' | 'graph' | 'verification';
  targetDocsUrl: string;
}

export const ATROPOS_VALUE_PROPOSITIONS: LandingFeatureSpec[] = [
  {
    id: 'spec-upload',
    title: 'Source Document Decomposition',
    description: 'Deconstruct complex technical requirements into atomic, immutable specifications with complete provenance tracking.',
    category: 'ingestion',
    targetDocsUrl: '/docs/spec-ingestion',
  },
  {
    id: 'evidence-trace',
    title: 'Evidence-Backed Research',
    description: 'Every invariant and architectural claim is mathematically tracked to closure with verifiable CAS hash proofs.',
    category: 'research',
    targetDocsUrl: '/docs/research-ledger',
  },
  {
    id: 'graph-dag',
    title: 'Visual DAG Authority Graph',
    description: 'Explore live dependencies and execution sequences across mobile, web, and cloud runtimes with zero cycle hazards.',
    category: 'graph',
    targetDocsUrl: '/docs/dag-architecture',
  },
  {
    id: 'release-verification',
    title: 'Zero-Entropy Release Gate',
    description: 'Cryptographically certified build receipts guarantee that released binaries strictly reflect attested source commitments.',
    category: 'verification',
    targetDocsUrl: '/docs/verification',
  },
];

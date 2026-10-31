/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM M5.2: GitHub Docs-Structured Documentation Portal.
 *
 * Implements a comprehensive documentation portal navigation taxonomy mirroring GitHub Docs.
 * Structures quickstarts, CLI syntax references, architecture laws, and tutorial guides.
 *
 * ## Research: Algolia DocSearch vs Edge In-Memory Vector Search
 * Researches client-side MiniSearch/Orama WASM vector indices versus Algolia DocSearch
 * to provide sub-10ms full-text documentation search with zero external network dependencies.
 */

import type { Route } from 'next';

export interface DocsArticleDescriptor {
  id: string;
  title: string;
  slug: string;
  summary: string;
  category: 'quickstart' | 'cli' | 'architecture' | 'deployment' | 'tutorials';
  href: Route;
}

export interface DocsSectionGroup {
  id: string;
  title: string;
  articles: DocsArticleDescriptor[];
}

export const DOCS_PORTAL_TAXONOMY: DocsSectionGroup[] = [
  {
    id: 'get-started',
    title: 'Getting Started',
    articles: [
      {
        id: 'overview',
        title: 'Introduction to ATROPOS',
        slug: 'introduction',
        summary: 'Overview of the local-first sovereign AI coding engine.',
        category: 'quickstart',
        href: '/docs/introduction' as Route,
      },
      {
        id: 'standalone-jar',
        title: 'Running the Standalone JAR',
        slug: 'standalone-jar',
        summary: 'How to download and run ATROPOS.jar on POSIX and Windows.',
        category: 'quickstart',
        href: '/docs/standalone-jar' as Route,
      },
    ],
  },
  {
    id: 'cli-reference',
    title: 'CLI Command Reference',
    articles: [
      {
        id: 'cli-core',
        title: 'Core Engine Commands',
        slug: 'cli-core',
        summary: 'Complete command reference for /verify, /self-host, /bridge, and /mcp.',
        category: 'cli',
        href: '/docs/cli-reference' as Route,
      },
      {
        id: 'cli-parity',
        title: 'Android & Web CLI Parity',
        slug: 'cli-parity',
        summary: 'Identical CLI semantics across mobile PTY and web terminals.',
        category: 'cli',
        href: '/docs/cli-parity' as Route,
      },
    ],
  },
  {
    id: 'architecture-laws',
    title: 'Architecture & Governance',
    articles: [
      {
        id: 'phase20-laws',
        title: 'Phase 20 Inside-Out Self-Build Laws',
        slug: 'phase20-laws',
        summary: 'Cryptographic proof-of-authority and exit-zero verification gates.',
        category: 'architecture',
        href: '/docs/architecture/phase20' as Route,
      },
      {
        id: 'specgraph-foundry',
        title: 'SpecGraph Foundry Planning Pipeline',
        slug: 'specgraph-foundry',
        summary: 'Decomposing source documents into verified DAG obligations.',
        category: 'architecture',
        href: '/docs/architecture/specgraph' as Route,
      },
    ],
  },
];

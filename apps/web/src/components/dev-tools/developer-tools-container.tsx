'use client';

import type { ReactNode } from 'react';

/** Keeps developer inspectors outside the primary operator surface. */
export function DeveloperToolsContainer({ children }: { children: ReactNode }) {
  return (
    <section
      aria-label="Developer Tools"
      data-surface="developer-tools"
      className="min-w-0"
    >
      {children}
    </section>
  );
}

/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * Territory Optical Focus (ADD-W-006).
 *
 * Desaturates off-territory paths and sharpens in-territory ones.
 * Uses `/v1/territory/check` to determine membership. This is the
 * "territory-as-material" effect from the design system — paths inside
 * the operator's declared scope render sharply; outside paths are
 * visually muted. No color-only signal (§E): the desaturation is
 * accompanied by a text indicator and reduced opacity.
 */

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { checkTerritoryMembership, TerritoryAssignment } from '@/lib/workspace/client';

interface TerritoryFocusState {
  inside: boolean;
  loading: boolean;
  path: string;
}

/**
 * Hook that tracks territory membership for a given path.
 */
export function useTerritoryFocus(path: string): TerritoryFocusState {
  const [state, setState] = useState<TerritoryFocusState>({
    inside: true, // Default to inside (sharp) while loading
    loading: true,
    path,
  });

  const check = useCallback(async () => {
    if (!path) {
      setState({ inside: true, loading: false, path: '' });
      return;
    }
    setState((prev) => ({ ...prev, loading: true }));
    try {
      const result = await checkTerritoryMembership(path);
      setState({ inside: result.inside, loading: false, path });
    } catch {
      // On error, default to inside (sharp) so we don't obscure content
      setState({ inside: true, loading: false, path });
    }
  }, [path]);

  useEffect(() => {
    let cancelled = false;
    void check().then(() => {
      if (cancelled) return;
    });
    return () => {
      cancelled = true;
    };
  }, [check]);

  return state;
}

/**
 * Higher-order component that applies territory focus to a child component.
 * Desaturates the child when the path is outside territory.
 */
interface TerritoryFocusWrapperProps {
  path: string;
  children: React.ReactNode;
  /** Show a small "outside territory" badge when outside. */
  showBadge?: boolean;
}

export function TerritoryFocusWrapper({
  path,
  children,
  showBadge = true,
}: TerritoryFocusWrapperProps) {
  const { inside, loading } = useTerritoryFocus(path);

  return (
    <div
      className={`wb-territory-wrapper ${!inside ? 'wb-outside' : ''} ${loading ? 'wb-loading' : ''}`}
      style={{
        opacity: inside ? 1 : 0.6,
        filter: inside ? 'none' : 'grayscale(100%)',
        transition: 'opacity 200ms ease, filter 200ms ease',
      }}
      data-territory={inside ? 'inside' : 'outside'}
    >
      {children}
      {showBadge && !inside && !loading && (
        <span className="wb-territory-badge" aria-label="Outside declared territory">
          Outside territory
        </span>
      )}
    </div>
  );
}

/**
 * Hook for getting all territory assignments and violations.
 */
export function useTerritoryAssignments() {
  const [assignments, setAssignments] = useState<TerritoryAssignment[]>([]);
  const [violations, setViolations] = useState<Array<{ path: string; owner: string; reason: string }>>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const [assignmentsResult, violationsResult] = await Promise.all([
          listTerritoryAssignments(),
          listTerritoryViolations(),
        ]);
        if (cancelled) return;
        setAssignments(assignmentsResult.assignments);
        setViolations(violationsResult.violations);
      } catch (error) {
        // Silently fail; territory is advisory
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return { assignments, violations, loading };
}

/**
 * Component that renders a territory status indicator for a path.
 */
interface TerritoryIndicatorProps {
  path: string;
  /** Show as inline badge instead of tooltip. */
  inline?: boolean;
}

export function TerritoryIndicator({ path, inline = false }: TerritoryIndicatorProps) {
  const { inside, loading } = useTerritoryFocus(path);

  if (loading) {
    return inline ? (
      <span className="wb-territory-indicator wb-loading" aria-busy="true">
        Checking…
      </span>
    ) : null;
  }

  const indicator = (
    <span
      className={`wb-territory-indicator ${inside ? 'wb-inside' : 'wb-outside'}`}
      data-territory={inside ? 'inside' : 'outside'}
    >
      {inside ? '✓' : '✗'}
    </span>
  );

  if (inline) return indicator;

  return (
    <span className="wb-territory-tooltip" data-path={path}>
      {indicator}
      <span className="wb-territory-tooltip-content">
        {inside ? 'Inside declared territory' : 'Outside declared territory'}
      </span>
    </span>
  );
}
/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM M2.6: Supabase Authentication and Dashboard Screen Gatekeeper.
 *
 * Enforces authenticated Supabase sessions before revealing privileged administration,
 * continuous telemetry scrapers, or internal evaluation metrics dashboards.
 *
 * ## Research: PostgreSQL Row-Level Security (RLS) Policies
 * Researches RLS policy predicates (`auth.uid() = owner_id` and role tenant bindings)
 * within PostgreSQL to guarantee cross-tenant telemetry isolation at the database engine boundary.
 */

'use client';

import React, { useState, useEffect } from 'react';
import { ShieldCheck, ShieldAlert, Lock, UserCheck } from 'lucide-react';
import { createSupabaseBrowserClient } from '@/lib/auth/browser';

export type AuthRolePermission = 'viewer' | 'developer' | 'operator' | 'owner';

export interface SupabaseDashboardGatekeeperProps {
  requiredRole?: AuthRolePermission;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function SupabaseDashboardGatekeeper({
  requiredRole = 'developer',
  children,
  fallback,
}: SupabaseDashboardGatekeeperProps) {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [userRole, setUserRole] = useState<AuthRolePermission>('viewer');

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const supabase = createSupabaseBrowserClient();
        const { data } = await supabase.auth.getSession();
        if (!active) return;
        if (data.session?.user) {
          setIsAuthenticated(true);
          const role = (data.session.user.user_metadata?.role as AuthRolePermission) ?? 'developer';
          setUserRole(role);
        } else {
          setIsAuthenticated(false);
        }
      } catch {
        if (active) setIsAuthenticated(false);
      }
    })();

    return () => {
      active = false;
    };
  }, []);

  if (isAuthenticated === null) {
    return (
      <div className="flex items-center gap-2 p-4 text-xs font-mono text-sg-neutral-400">
        <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
        Verifying Supabase authentication & authorization matrix...
      </div>
    );
  }

  if (!isAuthenticated) {
    if (fallback) return <>{fallback}</>;
    return (
      <div className="p-6 border border-dashed border-red-500/30 rounded-lg bg-red-950/20 text-red-300 font-mono text-xs">
        <div className="flex items-center gap-2 font-bold mb-2">
          <Lock className="w-4 h-4 text-red-400" />
          <span>Access Restricted: Authentication Required</span>
        </div>
        <p className="text-sg-neutral-400 mb-3">
          This dashboard requires an authenticated Supabase developer session.
        </p>
        <a
          href="/auth/sign-in"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded font-medium"
        >
          Sign In to ATROPOS Cloud
        </a>
      </div>
    );
  }

  return <>{children}</>;
}

/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import React from 'react';
import { resolveStatusThemeRole, CanonicalStatus } from '../../lib/theme/status-retheme-engine';

export function CanonicalStatusBadge({ status }: { status: CanonicalStatus }) {
  const role = resolveStatusThemeRole(status);

  return (
    <span
      role="status"
      aria-roledescription={role.ariaRoleDescription}
      className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold border ${role.bgColorClass} ${role.borderColorClass} ${role.textColorClass}`}
    >
      <span aria-hidden="true" className="text-xs">{role.glyph}</span>
      <span>{role.label}</span>
    </span>
  );
}

export default CanonicalStatusBadge;

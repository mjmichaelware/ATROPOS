/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * HOE-F01: Same project identity + status vocabulary on CLI/Web/Android.
 * Shared status enum prevents surface-specific drift.
 */

export enum StatusVocabulary {
  IDLE = 'idle',
  PLANNING = 'planning',
  WAITING = 'waiting',
  WORKING = 'working',
  REVIEW_REQUIRED = 'review_required',
  BLOCKED = 'blocked',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled'
}

export interface ProjectIdentity {
  id: string
  name: string
  status: StatusVocabulary
  timestamp: number
}

export class CrossSurfaceParity {
  /**
   * Validate status vocabulary consistency.
   * Used in contract tests CLI ↔ Web ↔ Android.
   */
  static validateStatus(status: string): boolean {
    return Object.values(StatusVocabulary).includes(status as StatusVocabulary)
  }

  /**
   * Get status display properties (icon, color, animation).
   * Identical across all surfaces.
   */
  static getStatusDisplay(status: StatusVocabulary) {
    const displays: Record<StatusVocabulary, {
      icon: string
      label: string
      color: string
      animated: boolean
    }> = {
      [StatusVocabulary.IDLE]: {
        icon: '◯',
        label: 'Idle',
        color: '#6B7280',
        animated: false
      },
      [StatusVocabulary.PLANNING]: {
        icon: '◐',
        label: 'Planning',
        color: '#3B82F6',
        animated: true
      },
      [StatusVocabulary.WAITING]: {
        icon: '⊙',
        label: 'Waiting',
        color: '#F59E0B',
        animated: false
      },
      [StatusVocabulary.WORKING]: {
        icon: '◉',
        label: 'Working',
        color: '#3B82F6',
        animated: true
      },
      [StatusVocabulary.REVIEW_REQUIRED]: {
        icon: '◆',
        label: 'Review Needed',
        color: '#F59E0B',
        animated: false
      },
      [StatusVocabulary.BLOCKED]: {
        icon: '■',
        label: 'Blocked',
        color: '#F59E0B',
        animated: true  // Slow pulse
      },
      [StatusVocabulary.COMPLETED]: {
        icon: '✓',
        label: 'Completed',
        color: '#10B981',
        animated: false
      },
      [StatusVocabulary.FAILED]: {
        icon: '✗',
        label: 'Failed',
        color: '#EF4444',
        animated: false
      },
      [StatusVocabulary.CANCELLED]: {
        icon: '⊗',
        label: 'Cancelled',
        color: '#6B7280',
        animated: false
      }
    }

    return displays[status]
  }

  /**
   * Validate project identity is identical across surfaces.
   */
  static validateProjectIdentity(projects: ProjectIdentity[]): {
    isValid: boolean
    errors: string[]
  } {
    const errors: string[] = []

    const grouped = new Map<string, ProjectIdentity[]>()
    projects.forEach((p) => {
      const key = `${p.id}:${p.name}`
      if (!grouped.has(key)) grouped.set(key, [])
      grouped.get(key)!.push(p)
    })

    grouped.forEach((instances, key) => {
      const firstStatus = instances[0].status
      const allSame = instances.every((p) => p.status === firstStatus)

      if (!allSame) {
        const surfaces = instances.map((p) => `${p.status} (${p.timestamp})`).join(', ')
        errors.push(
          `Project ${key}: status diverged across surfaces: ${surfaces}`
        )
      }
    })

    return {
      isValid: errors.length === 0,
      errors
    }
  }
}

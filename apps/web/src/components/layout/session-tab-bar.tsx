'use client';

import { useState } from 'react';
import Link from 'next/link';
import { projectSections } from '@/components/navigation/routes';

// The tabs are the §2.2-2.6 project spine, taken from the shared route table
// rather than restated here. SpecGraph is deliberately absent: §1.3 and §12.2
// keep compiler subsystems under Developer Tools, never in a project's tab
// strip.
const TAB_ICONS: Record<string, string> = {
  work: '⚙️',
  conversations: '💬',
  files: '📁',
  agents: '🤖',
};

interface SessionTabBarProps {
  projectId: string;
  activeTab?: string;
}

export function SessionTabBar({ projectId, activeTab = 'work' }: SessionTabBarProps) {
  const [openTabs, setOpenTabs] = useState<string[]>([activeTab]);

  const toggleTab = (tabId: string) => {
    setOpenTabs(prev =>
      prev.includes(tabId)
        ? prev.filter(t => t !== tabId)
        : [...prev, tabId]
    );
  };

  const closeTab = (tabId: string) => {
    setOpenTabs(prev => prev.filter(t => t !== tabId));
  };

  return (
    <div className="session-tab-bar">
      <div className="tab-list" role="tablist">
        {projectSections.map(tab => (
          <div
            key={tab.id}
            className={`tab ${activeTab === tab.id ? 'active' : ''}`}
            role="tab"
            aria-selected={activeTab === tab.id}
          >
            <Link
              href={tab.build(projectId)}
              className="tab-link"
              onClick={() => toggleTab(tab.id)}
            >
              {TAB_ICONS[tab.id] && (
                <span className="tab-icon" aria-hidden="true">{TAB_ICONS[tab.id]}</span>
              )}
              <span className="tab-label">{tab.label}</span>
            </Link>
            {openTabs.includes(tab.id) && openTabs.length > 1 && (
              <button
                className="tab-close"
                onClick={() => closeTab(tab.id)}
                aria-label={`Close ${tab.label} tab`}
              >
                ✕
              </button>
            )}
          </div>
        ))}
      </div>

      <style jsx>{`
        .session-tab-bar {
          display: flex;
          align-items: center;
          background: var(--sg-surface);
          border-bottom: 1px solid var(--sg-border);
          overflow-x: auto;
          padding: 0 var(--sg-space-2);
          height: 48px;
          min-height: 48px;
        }

        .tab-list {
          display: flex;
          gap: 4px;
          align-items: center;
        }

        .tab {
          display: flex;
          align-items: center;
          height: 36px;
          padding: 0 var(--sg-space-3);
          background: transparent;
          border: 1px solid transparent;
          border-radius: var(--sg-radius-md) var(--sg-radius-md) 0 0;
          position: relative;

          &.active {
            background: var(--sg-elevated);
            border-color: var(--sg-accent);
            border-bottom-color: var(--sg-elevated);
          }
        }

        .tab-link {
          display: flex;
          align-items: center;
          gap: var(--sg-space-2);
          color: inherit;
          text-decoration: none;
          font-size: var(--sg-type-sm);
          font-weight: var(--sg-weight-medium);
          flex: 1;
        }

        .tab-icon {
          display: flex;
          align-items: center;
          font-size: 1rem;
        }

        .tab-label {
          white-space: nowrap;
        }

        .tab-close {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 20px;
          height: 20px;
          margin-left: var(--sg-space-1);
          background: transparent;
          border: none;
          cursor: pointer;
          color: var(--sg-text-secondary);
          border-radius: 3px;
          transition: all 0.2s;

          &:hover {
            background: var(--sg-border);
            color: var(--sg-text-primary);
          }
        }
      `}</style>
    </div>
  );
}

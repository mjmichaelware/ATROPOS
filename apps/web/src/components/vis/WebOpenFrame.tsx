/* SPDX-License-Identifier: AGPL-3.0-only */
'use client';

import { FC, useMemo } from 'react';
import { globalRoutes, navigationSpine, HOE_A02_SPINE_ORDER } from '@/components/navigation/routes';
import { useNavItems } from '@/components/navigation/use-nav-items';
import { useOptionalSessionState } from '@/lib/contexts/session-state-context';
import { GlobalSection } from '@/components/workspace/global-section';
import { SixAnswersPanel, SixAnswer } from '@/components/ui/six-answers-panel';
import { StatusBadge } from '@/components/ui/status-badge';
import { EngineStatusBanner } from '@/components/atropos/engine-status-banner';
import { RecoveryRibbon } from '@/components/atropos/recovery-ribbon';
import { CheckpointRail } from '@/components/checkpoint/checkpoint-rail';
import { BridgeApprovalList } from '@/components/approvals/bridge-approval-list';
import { InterruptControls } from '@/components/work-queue/interrupt-controls';
import { VerbosityControl } from '@/components/disclosure/verbosity-control';
import { useLayoutTheme } from '@/lib/contexts/layout-theme-context';
import { WorkbenchTabsProvider, useWorkbenchTabs } from '@/lib/contexts/workbench-tabs-context';
import { WorkbenchShell } from '@/components/workbench/workbench-shell';
import { FileExplorer } from '@/components/workbench/file-explorer';
import { EditorTabs } from '@/components/workbench/editor-tabs';
import { LogPanel } from '@/components/workbench/log-panel';

/**
 * F-VIS-004: Web Open Frame - Session Theme
 *
 * Session-first landing page theme:
 * - Top bar: project badge (active project or "no project")
 * - Left spine: primary navigation
 * - Right: user menu (profile, settings, auth)
 *
 * Depends on: F-WEB-002 (session-first home)
 */
export interface SessionFrameProps {
  children: React.ReactNode;
}

export const SessionOpenFrame: FC<SessionFrameProps> = ({ children }) => {
  const { session } = useOptionalSessionState();
  const { global, project, engineState, developer } = useNavItems();
  const activeProjectId = session?.session.activeProjectId ?? null;

  return (
    <div className="sg-session-frame">
      {/* Top Bar */}
      <header className="sg-session-topbar">
        <div className="sg-session-brand">
          <a href={globalRoutes.home} className="sg-session-logo">
            ATROPOS
          </a>
          {activeProjectId && (
            <span className="sg-session-project-badge">
              Project: {activeProjectId}
            </span>
          )}
        </div>

        {/* Spine Navigation */}
        <nav className="sg-session-spine" aria-label="Primary navigation">
          {navigationSpine.map((item) => (
            <a
              key={item.id}
              href={item.href}
              className={`sg-session-spine-item ${window.location.pathname === item.href ? 'active' : ''}`}
            >
              {item.label}
            </a>
          ))}
        </nav>

        {/* Right Side: User Menu */}
        <div className="sg-session-user-menu">
          <a href={globalRoutes.settings} className="sg-session-user-item">
            Settings
          </a>
        </div>
      </header>

      {/* Left Spine - Full Navigation */}
      <aside className="sg-session-sidebar" aria-label="Application sections">
        <p className="sg-session-sidebar-kicker">ATROPOS</p>
        <nav className="sg-session-nav">
          {global.map((item) => (
            <a
              key={item.id}
              href={item.href}
              className={`sg-session-nav-link ${item.active ? 'active' : ''}`}
            >
              {item.label}
            </a>
          ))}
          {project.length > 0 && (
            <>
              <p className="sg-session-sidebar-kicker">Project</p>
              <nav className="sg-session-nav">
                {project.map((item) => (
                  <a
                    key={item.id}
                    href={item.href}
                    className={`sg-session-nav-link ${item.active ? 'active' : ''}`}
                  >
                    {item.label}
                  </a>
                ))}
              </nav>
            </>
          )}
          <p className="sg-session-sidebar-kicker">System</p>
          <nav className="sg-session-nav">
            {engineState.map((item) => (
              <a
                key={item.id}
                href={item.href}
                className={`sg-session-nav-link ${item.active ? 'active' : ''}`}
              >
                {item.label}
              </a>
            ))}
          </nav>
          {developer.length > 0 && (
            <>
              <p className="sg-session-sidebar-kicker">Developer</p>
              <nav className="sg-session-nav">
                {developer.map((item) => (
                  <a
                    key={item.id}
                    href={item.href}
                    className={`sg-session-nav-link ${item.active ? 'active' : ''}`}
                  >
                    {item.label}
                  </a>
                ))}
              </nav>
            </>
          )}
        </nav>
      </aside>

      {/* Main Content */}
      <main className="sg-session-main" id="main-content" tabIndex={-1}>
        <EngineStatusBanner />
        <RecoveryRibbon />
        {children}
      </main>
    </div>
  );
};

/**
 * F-VIS-005: Web Open Frame - Workbench Theme
 *
 * Four-pane workbench arrangement:
 * - Top-left: activity/project
 * - Left: explorer (file tree)
 * - Center: editor tabs (or routed page)
 * - Bottom: streaming logs (collapsible)
 * - Right: AI rail (checkpoint, approvals, evidence)
 *
 * Depends on: F-WEB-003 (VS Code four-pane layout)
 */
export interface WorkbenchFrameProps {
  children: React.ReactNode;
  projectId?: string;
}

export const WorkbenchOpenFrame: FC<WorkbenchFrameProps> = ({ children, projectId }) => {
  const { layout } = useLayoutTheme();
  const { store, open } = useWorkbenchTabs();
  const hasOpenTabs = store.tabs.length > 0;

  if (layout === 'session') {
    return <SessionOpenFrame>{children}</SessionOpenFrame>;
  }

  return (
    <WorkbenchTabsProvider>
      <WorkbenchShell
        explorer={<FileExplorer onOpen={open} />}
        editor={
          hasOpenTabs ? (
            <EditorTabs />
          ) : (
            <div className="wb-page">{children}</div>
          )
        }
        logs={<LogPanel />}
        terminal={<TerminalComponent projectId={projectId} />}
        aiRail={
          <div className="wb-airail-inner">
            <CheckpointRail />
            <BridgeApprovalList />
            <InterruptControls />
            <VerbosityControl />
          </div>
        }
      />
    </WorkbenchTabsProvider>
  );
};

/**
 * F-VIS-006: Web Hero Center
 *
 * The center pane is the hero:
 * - Empty center = project picker (no file tabs open)
 * - File tab open = editor becomes hero
 * - No center = welcome screen
 *
 * Depends on: F-WEB-005 (editor tabs)
 */
export interface HeroCenterProps {
  children: React.ReactNode;
  hasOpenTabs: boolean;
  activeTab?: string;
  onOpenTab?: (path: string) => void;
}

export const HeroCenter: FC<HeroCenterProps> = ({ children, hasOpenTabs, activeTab, onOpenTab }) => {
  return (
    <div className="wb-hero-center" data-testid="hero-center">
      {hasOpenTabs ? (
        <div className="wb-editor-hero">
          {/* EditorTabs is the hero when tabs are open */}
          {children}
        </div>
      ) : (
        <div className="wb-hero-welcome">
          {/* Welcome / Project Picker is hero when no tabs */}
          <div className="wb-hero-content">
            <h1 className="wb-hero-title">ATROPOS</h1>
            <p className="wb-hero-tagline">Turn source documents into verified, execution-ready plans.</p>
            <div className="wb-hero-actions">
              <a href="/projects" className="wb-btn wb-btn-primary">
                Open Project
              </a>
              <a href="/projects/new" className="wb-btn wb-btn-secondary">
                New Project
              </a>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * F-VIS-007: Web Footer Composer
 *
 * Always-reachable composer at bottom of viewport:
 * - Enter sends message
 * - / expands command palette
 * - @ mentions for file/agent references
 *
 * Depends on: F-WEB-008 (streaming approval + command palette)
 */
export interface ComposerFooterProps {
  onSubmit: (text: string) => void;
  value: string;
  onValueChange: (value: string) => void;
  isOnline: boolean;
}

export const ComposerFooter: FC<ComposerFooterProps> = ({
  onSubmit,
  value,
  onValueChange,
  isOnline,
}) => {
  const [showPalette, setShowPalette] = useState(false);
  const [query, setQuery] = useState('');

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (value.trim()) {
        onSubmit(value);
        onValueChange('');
      }
    } else if (e.key === '/' && value === '') {
      e.preventDefault();
      setShowPalette(true);
    } else if (e.key === 'Escape') {
      setShowPalette(false);
    }
  };

  return (
    <footer className="wb-composer-footer" data-testid="composer-footer">
      <div className="wb-composer-input-wrapper">
        {showPalette && (
          <div className="wb-composer-palette">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search commands... (type /command)"
              className="wb-composer-palette-input"
              autoFocus
            />
            <div className="wb-composer-palette-results">
              {/* Command palette results would render here */}
            </div>
          </div>
        )}
        <textarea
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={isOnline ? "Type a message or /command..." : "Engine offline — messages will queue"}
          disabled={!isOnline}
          className="wb-composer-textarea"
          rows={1}
          spellCheck={false}
        />
      </div>
    </footer>
  );
};
'use client';

import { ReactNode } from 'react';

interface InspectorPaneProps {
  onClose: () => void;
  content?: ReactNode;
  title?: string;
}

export function InspectorPane({ onClose, content, title = 'Inspector' }: InspectorPaneProps) {
  return (
    <div className="inspector-pane">
      <div className="inspector-header">
        <h2 className="inspector-title">{title}</h2>
        <button
          className="inspector-close"
          onClick={onClose}
          aria-label="Close inspector"
        >
          ✕
        </button>
      </div>

      <div className="inspector-content">
        {content || (
          <div className="inspector-empty">
            <p>Select an item to inspect</p>
          </div>
        )}
      </div>

      <style jsx>{`
        .inspector-pane {
          display: flex;
          flex-direction: column;
          height: 100%;
          background: var(--sg-elevated);
        }

        .inspector-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: var(--sg-space-3);
          border-bottom: 1px solid var(--sg-border);
        }

        .inspector-title {
          margin: 0;
          font-size: var(--sg-type-sm);
          font-weight: var(--sg-weight-bold);
          text-transform: uppercase;
          letter-spacing: 0.08em;
          color: var(--sg-text-muted);
        }

        .inspector-close {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 28px;
          height: 28px;
          background: transparent;
          border: 1px solid var(--sg-border);
          border-radius: var(--sg-radius-sm);
          cursor: pointer;
          color: var(--sg-text-secondary);
          transition: all 0.2s;

          &:hover {
            background: var(--sg-border);
            color: var(--sg-text-primary);
          }
        }

        .inspector-content {
          flex: 1;
          overflow: auto;
          padding: var(--sg-space-3);
        }

        .inspector-empty {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 100%;
          color: var(--sg-text-muted);
          text-align: center;
        }

        .inspector-empty p {
          margin: 0;
          font-size: var(--sg-type-sm);
        }
      `}</style>
    </div>
  );
}

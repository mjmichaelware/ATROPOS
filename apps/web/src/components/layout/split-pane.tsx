'use client';

import { ReactNode, useRef, useState, useEffect } from 'react';

interface SplitPaneProps {
  left: ReactNode;
  right?: ReactNode;
  rightOpen?: boolean;
  defaultRightWidth?: number;
  minRightWidth?: number;
  maxRightWidth?: number;
}

export function SplitPane({
  left,
  right,
  rightOpen = false,
  defaultRightWidth = 320,
  minRightWidth = 250,
  maxRightWidth = 600,
}: SplitPaneProps) {
  const [rightWidth, setRightWidth] = useState(defaultRightWidth);
  const containerRef = useRef<HTMLDivElement>(null);
  const isDraggingRef = useRef(false);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDraggingRef.current || !containerRef.current) return;

      const container = containerRef.current;
      const rect = container.getBoundingClientRect();
      const newWidth = rect.right - e.clientX;

      if (newWidth >= minRightWidth && newWidth <= maxRightWidth) {
        setRightWidth(newWidth);
      }
    };

    const handleMouseUp = () => {
      isDraggingRef.current = false;
    };

    if (isDraggingRef.current) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);

      return () => {
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [minRightWidth, maxRightWidth]);

  const handleDragStart = () => {
    isDraggingRef.current = true;
  };

  return (
    <div ref={containerRef} className="split-pane">
      <div className="split-pane-left">
        {left}
      </div>

      {right && rightOpen && (
        <>
          <div
            className="split-pane-divider"
            onMouseDown={handleDragStart}
            role="separator"
            tabIndex={0}
            aria-label="Resize inspector pane"
          />
          <div className="split-pane-right" style={{ width: `${rightWidth}px` }}>
            {right}
          </div>
        </>
      )}

      <style jsx>{`
        .split-pane {
          display: flex;
          flex: 1;
          overflow: hidden;
          background: var(--sg-surface-canvas);
        }

        .split-pane-left {
          flex: 1;
          overflow: auto;
          min-width: 0;
        }

        .split-pane-divider {
          width: 1px;
          background: var(--sg-border);
          cursor: col-resize;
          user-select: none;
          transition: background-color 0.2s;

          &:hover {
            background: var(--sg-accent);
          }

          &:focus {
            outline: 2px solid var(--sg-focus);
            outline-offset: -1px;
          }
        }

        .split-pane-right {
          flex-shrink: 0;
          overflow: auto;
          border-left: 1px solid var(--sg-border);
          background: var(--sg-elevated);
        }
      `}</style>
    </div>
  );
}

"use client";

import { useId, useRef, type KeyboardEvent, type ReactNode } from "react";
import { useViewTransition } from "./view-transition";

/**
 * WAI-ARIA APG Tabs pattern with manual activation: arrow keys move focus
 * among tabs (roving tabindex) without changing the panel; Home/End jump to
 * the first/last tab; Enter/Space activates the focused tab. Manual
 * activation is used deliberately because activating a tab here can trigger
 * real data fetching — arrow-browsing must never fire that as a side effect.
 */
export function Tabs<T extends string>({
  label,
  value,
  tabs,
  onChange,
}: {
  label: string;
  value: T;
  tabs: Array<{ value: T; label: string; panel: ReactNode }>;
  onChange: (value: T) => void;
}) {
  const active = tabs.find((tab) => tab.value === value) ?? tabs[0];
  const withTransition = useViewTransition();
  const baseId = useId();
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  function focusTab(index: number) {
    const wrapped = (index + tabs.length) % tabs.length;
    tabRefs.current[wrapped]?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    switch (event.key) {
      case "ArrowRight":
        event.preventDefault();
        focusTab(index + 1);
        break;
      case "ArrowLeft":
        event.preventDefault();
        focusTab(index - 1);
        break;
      case "Home":
        event.preventDefault();
        focusTab(0);
        break;
      case "End":
        event.preventDefault();
        focusTab(tabs.length - 1);
        break;
      default:
        break;
    }
  }

  return (
    <section className="sg-tabs">
      <div role="tablist" aria-label={label} className="sg-tab-list">
        {tabs.map((tab, index) => {
          const selected = tab.value === active.value;
          const tabId = `${baseId}-tab-${tab.value}`;
          const panelId = `${baseId}-panel-${tab.value}`;
          return (
            <button
              key={tab.value}
              ref={(node) => {
                tabRefs.current[index] = node;
              }}
              id={tabId}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={panelId}
              tabIndex={selected ? 0 : -1}
              className="sg-tab sg-pressable"
              onKeyDown={(event) => handleKeyDown(event, index)}
              onClick={() => withTransition(() => onChange(tab.value))}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div id={`${baseId}-panel-${active.value}`} role="tabpanel" aria-labelledby={`${baseId}-tab-${active.value}`} tabIndex={0} className="sg-tab-panel">
        {active.panel}
      </div>
    </section>
  );
}

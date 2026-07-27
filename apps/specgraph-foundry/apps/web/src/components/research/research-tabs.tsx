"use client";

import type { ReactNode } from "react";
import { Tabs } from "@/components/ui/tabs";

export type ResearchTab = "overview" | "atoms" | "matrix" | "tasks";

export function ResearchTabs({
  value,
  onChange,
  overview,
  atoms,
  matrix,
  tasks,
}: {
  value: ResearchTab;
  onChange: (value: ResearchTab) => void;
  overview: ReactNode;
  atoms: ReactNode;
  matrix: ReactNode;
  tasks: ReactNode;
}) {
  return (
    <Tabs
      label="Research workspace views"
      value={value}
      onChange={onChange}
      tabs={[
        { value: "overview", label: "Overview", panel: overview },
        { value: "atoms", label: "Atoms and dimensions", panel: atoms },
        { value: "matrix", label: "Gap matrix", panel: matrix },
        { value: "tasks", label: "Task queue", panel: tasks },
      ]}
    />
  );
}

"use client";

import { Tabs } from "@/components/ui/tabs";
import type { ReactNode } from "react";

export type SourceTab = "library" | "upload" | "activity";

export function SourceTabs({ value, onChange, library, upload, activity }: { value: SourceTab; onChange: (tab: SourceTab) => void; library: ReactNode; upload: ReactNode; activity: ReactNode }) {
  return (
    <Tabs
      label="Source workspace"
      value={value}
      onChange={onChange}
      tabs={[
        { value: "library", label: "Library", panel: library },
        { value: "upload", label: "Upload / ingestion", panel: upload },
        { value: "activity", label: "Activity", panel: activity },
      ]}
    />
  );
}

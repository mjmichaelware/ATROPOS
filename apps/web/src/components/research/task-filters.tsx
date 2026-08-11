import { SegmentedControl } from "@/components/ui/segmented-control";

export type TaskFilter = "ALL" | "PENDING" | "CLAIMED" | "COMPLETE" | "FAILED";

export function TaskFilters({ value, onChange }: { value: TaskFilter; onChange: (value: TaskFilter) => void }) {
  return (
    <div>
      <SegmentedControl
        label="Current page task filter"
        value={value}
        onChange={onChange}
        options={[
          { value: "ALL", label: "All" },
          { value: "PENDING", label: "Pending" },
          { value: "CLAIMED", label: "Claimed" },
          { value: "COMPLETE", label: "Complete" },
          { value: "FAILED", label: "Failed" },
        ]}
      />
      <p className="sg-muted">Filter applies only to the loaded cursor page.</p>
    </div>
  );
}

import { Progress } from "@/components/ui/progress";
import type { UploadItem } from "@/lib/sources/upload-machine";

export function SourceUploadProgress({ item }: { item: UploadItem }) {
  return (
    <div className="sg-upload-progress">
      <strong>{item.filename}</strong>
      <span>{item.phase}</span>
      <Progress value={item.phase === "FINALIZING" || item.phase === "EXTRACTING" ? undefined : item.progress} label={`${item.filename} upload progress`} />
      {item.message ? <p role={item.phase === "FAILED" ? "alert" : "status"}>{item.message}</p> : null}
    </div>
  );
}

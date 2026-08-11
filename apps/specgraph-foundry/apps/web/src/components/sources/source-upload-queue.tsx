import { SourceUploadProgress } from "./source-upload-progress";
import type { UploadItem } from "@/lib/sources/upload-machine";

export function SourceUploadQueue({ items }: { items: UploadItem[] }) {
  return (
    <div className="sg-upload-queue" aria-live="polite">
      {items.map((item) => <SourceUploadProgress key={item.id} item={item} />)}
    </div>
  );
}

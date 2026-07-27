export type UploadPhase =
  | "SELECTED"
  | "VALIDATING"
  | "HASHING"
  | "INTENT_CREATING"
  | "UPLOADING"
  | "UPLOAD_COMPLETE"
  | "FINALIZE_QUEUED"
  | "FINALIZING"
  | "FINALIZED"
  | "EXTRACTION_QUEUED"
  | "EXTRACTING"
  | "COMPLETE"
  | "FAILED"
  | "CANCELLED";

const TRANSITIONS: Record<UploadPhase, UploadPhase[]> = {
  SELECTED: ["VALIDATING", "CANCELLED", "FAILED"],
  VALIDATING: ["HASHING", "FAILED", "CANCELLED"],
  HASHING: ["INTENT_CREATING", "FAILED", "CANCELLED"],
  INTENT_CREATING: ["UPLOADING", "FAILED", "CANCELLED"],
  UPLOADING: ["UPLOAD_COMPLETE", "FAILED", "CANCELLED"],
  UPLOAD_COMPLETE: ["FINALIZE_QUEUED", "FAILED", "CANCELLED"],
  FINALIZE_QUEUED: ["FINALIZING", "FAILED", "CANCELLED"],
  FINALIZING: ["FINALIZED", "FAILED", "CANCELLED"],
  FINALIZED: ["EXTRACTION_QUEUED", "COMPLETE"],
  EXTRACTION_QUEUED: ["EXTRACTING", "FAILED", "CANCELLED"],
  EXTRACTING: ["COMPLETE", "FAILED", "CANCELLED"],
  COMPLETE: [],
  FAILED: ["VALIDATING", "CANCELLED"],
  CANCELLED: [],
};

export type UploadItem = {
  id: string;
  filename: string;
  mediaType?: string;
  size: number;
  phase: UploadPhase;
  progress: number;
  message?: string;
  uploadId?: string;
  operationId?: string;
  documentId?: string;
};

export type UploadAction =
  | { type: "add"; item: UploadItem }
  | { type: "transition"; id: string; phase: UploadPhase; message?: string }
  | { type: "progress"; id: string; progress: number }
  | { type: "remove"; id: string };

export function canTransition(from: UploadPhase, to: UploadPhase) {
  return TRANSITIONS[from].includes(to);
}

export function uploadReducer(items: UploadItem[], action: UploadAction) {
  switch (action.type) {
    case "add":
      return items.some((item) => item.id === action.item.id) ? items : [...items, action.item];
    case "remove":
      return items.filter((item) => item.id !== action.id);
    case "progress":
      return items.map((item) => (item.id === action.id ? { ...item, progress: Math.max(0, Math.min(100, action.progress)) } : item));
    case "transition":
      return items.map((item) => {
        if (item.id !== action.id) {
          return item;
        }
        if (!canTransition(item.phase, action.phase)) {
          return { ...item, phase: "FAILED" as const, message: `Illegal upload transition ${item.phase} to ${action.phase}` };
        }
        return { ...item, phase: action.phase, message: action.message };
      });
  }
}

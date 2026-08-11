import { detectMediaType, SOURCE_FORMATS, type SourceMediaType } from "./formats";
import { sanitizeFilename } from "./security";

export type FileValidation = {
  ok: boolean;
  filename: string;
  mediaType?: SourceMediaType;
  reason?: string;
};

export const MAX_ACTIVE_UPLOADS = 5;

export function validateSourceFile(file: File, selected: readonly File[] = []): FileValidation {
  const filename = sanitizeFilename(file.name);
  if (file.size <= 0) {
    return { ok: false, filename, reason: "Empty files cannot be uploaded." };
  }
  const mediaType = detectMediaType(file);
  if (!mediaType) {
    return { ok: false, filename, reason: `Unsupported format. Allowed: ${SOURCE_FORMATS.join(", ")}` };
  }
  const duplicate = selected.some((candidate) => candidate.name === file.name && candidate.size === file.size && candidate.lastModified === file.lastModified);
  if (duplicate) {
    return { ok: false, filename, mediaType, reason: "This file is already in the upload queue." };
  }
  return { ok: true, filename, mediaType };
}

export const SOURCE_FORMATS = [
  "text/plain",
  "text/markdown",
  "application/json",
  "application/yaml",
  "application/x-yaml",
  "text/yaml",
  "text/html",
  "application/xhtml+xml",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/pdf",
  "text/x-python",
  "text/x-rustsrc",
  "text/x-go",
  "text/x-java-source",
  "text/x-c",
  "text/x-c++src",
] as const;

export type SourceMediaType = (typeof SOURCE_FORMATS)[number];

export const SOURCE_EXTENSIONS: Record<string, SourceMediaType> = {
  ".txt": "text/plain",
  ".md": "text/markdown",
  ".markdown": "text/markdown",
  ".json": "application/json",
  ".yaml": "application/yaml",
  ".yml": "application/yaml",
  ".html": "text/html",
  ".xhtml": "application/xhtml+xml",
  ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  ".pdf": "application/pdf",
  ".py": "text/x-python",
  ".rs": "text/x-rustsrc",
  ".go": "text/x-go",
  ".java": "text/x-java-source",
  ".c": "text/x-c",
  ".cc": "text/x-c++src",
  ".cpp": "text/x-c++src",
};

export function isSourceMediaType(value: string): value is SourceMediaType {
  return SOURCE_FORMATS.includes(value as SourceMediaType);
}

export function detectMediaType(file: Pick<File, "name" | "type">): SourceMediaType | undefined {
  if (file.type && isSourceMediaType(file.type)) {
    return file.type;
  }
  const lower = file.name.toLowerCase();
  const extension = Object.keys(SOURCE_EXTENSIONS).find((candidate) => lower.endsWith(candidate));
  return extension ? SOURCE_EXTENSIONS[extension] : undefined;
}

export function formatLabel(mediaType?: string) {
  if (!mediaType) {
    return "Unknown";
  }
  if (mediaType.includes("pdf")) {
    return "PDF";
  }
  if (mediaType.includes("wordprocessingml")) {
    return "DOCX";
  }
  if (mediaType.includes("html")) {
    return "HTML";
  }
  if (mediaType.includes("json")) {
    return "JSON";
  }
  if (mediaType.includes("yaml")) {
    return "YAML";
  }
  if (mediaType.includes("markdown")) {
    return "Markdown";
  }
  if (mediaType.includes("python")) {
    return "Python";
  }
  return mediaType.startsWith("text/") ? "Text" : mediaType;
}

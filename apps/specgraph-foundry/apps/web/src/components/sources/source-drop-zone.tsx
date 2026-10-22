"use client";

import { useRef } from "react";
import { Button } from "@/components/ui/button";

export function SourceDropZone({ onFiles }: { onFiles: (files: File[]) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <div
      className="sg-drop-zone"
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault();
        onFiles([...event.dataTransfer.files]);
      }}
    >
      <input ref={inputRef} id="source-file-input" type="file" multiple onChange={(event) => onFiles([...event.currentTarget.files ?? []])} />
      <label htmlFor="source-file-input">Drop verified authority candidates here</label>
      <p>Text, Markdown, JSON, YAML, source code, HTML, DOCX, and PDF are sent through server verification.</p>
      <Button type="button" variant="secondary" onClick={() => inputRef.current?.click()}>Choose files</Button>
    </div>
  );
}

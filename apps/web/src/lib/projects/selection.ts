const STORAGE_KEY = "specgraph.recentProjectId";

export function readRecentProjectId(storage: Pick<Storage, "getItem"> | undefined) {
  const value = storage?.getItem(STORAGE_KEY);
  return value && /^[0-9a-f-]{20,}$/i.test(value) ? value : null;
}

export function writeRecentProjectId(storage: Pick<Storage, "setItem">, projectId: string) {
  storage.setItem(STORAGE_KEY, projectId);
}

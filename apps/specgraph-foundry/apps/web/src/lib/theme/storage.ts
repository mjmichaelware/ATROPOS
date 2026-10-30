export type ThemeChoice = "dark" | "light" | "system" | "high-contrast";

const STORAGE_KEY = "specgraph.theme";
const THEMES = new Set<ThemeChoice>(["dark", "light", "system", "high-contrast"]);

export function readTheme(storage: Pick<Storage, "getItem"> | undefined): ThemeChoice {
  const value = storage?.getItem(STORAGE_KEY);
  return THEMES.has(value as ThemeChoice) ? (value as ThemeChoice) : "dark";
}

export function writeTheme(storage: Pick<Storage, "setItem">, theme: ThemeChoice) {
  if (!THEMES.has(theme)) {
    throw new Error("invalid theme");
  }
  storage.setItem(STORAGE_KEY, theme);
}

export function applyTheme(documentElement: HTMLElement, theme: ThemeChoice) {
  documentElement.dataset.theme = theme;
}

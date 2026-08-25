import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppShell } from "./app-shell";
import { LayoutThemeProvider } from "@/lib/contexts/layout-theme-context";

describe("AppShell", () => {
  it("provides skip link, header, navigation, and main landmark", async () => {
    render(
      // The shell now carries the layout toggle (F-WEB-003), which reads the
      // layout context; production always mounts the shell inside
      // AppProviders, so the test mirrors that wiring.
      <LayoutThemeProvider>
        <AppShell>
          <h1>Content</h1>
        </AppShell>
      </LayoutThemeProvider>,
    );
    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
      expect(screen.getByRole("banner")).toBeInTheDocument();
      expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");
      expect(screen.getByText("Content")).toBeInTheDocument();
    });
  });
});

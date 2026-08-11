import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppShell } from "./app-shell";

describe("AppShell", () => {
  it("provides skip link, header, navigation, and main landmark", async () => {
    render(
      <AppShell>
        <h1>Content</h1>
      </AppShell>,
    );
    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
      expect(screen.getByRole("banner")).toBeInTheDocument();
      expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");
      expect(screen.getByText("Content")).toBeInTheDocument();
    });
  });
});

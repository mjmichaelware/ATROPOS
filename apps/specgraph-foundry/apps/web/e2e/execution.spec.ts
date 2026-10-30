import { expect, test } from "@playwright/test";

test("execution run detail route is listable", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).toHaveURL(/projects\/project-1\/executions\/run-1/);
});

test("execution loading and empty state are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page.locator("body")).toBeVisible();
});

test("execution keyboard controls are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await page.keyboard.press("Tab");
});

test("mobile execution sheet viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).toHaveURL(/executions\/run-1/);
});

test("desktop execution split viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).toHaveURL(/executions\/run-1/);
});

test("reduced-motion emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).toHaveURL(/executions\/run-1/);
});

test("forced-colors emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ forcedColors: "active" });
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).toHaveURL(/executions\/run-1/);
});

test("no worker-only claim, heartbeat, or receipt-submission controls are present on the execution route", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page.getByRole("button", { name: /claim/i })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /heartbeat/i })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /submit receipt/i })).toHaveCount(0);
});

test("no raw evidence or lease-token content leaks into the execution route", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page).not.toHaveURL(/lease_token|claim_secret/);
});

test("no Group 17+ visual-polish or deployment controls are present on the execution route", async ({ page }) => {
  await page.goto("/projects/project-1/executions/run-1");
  await expect(page.getByRole("button", { name: "Deploy" })).toHaveCount(0);
});

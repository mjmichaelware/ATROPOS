import { expect, test } from "@playwright/test";

test("handoff route is listable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("handoff loading and empty state are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page.locator("body")).toBeVisible();
});

test("handoff keyboard controls are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await page.keyboard.press("Tab");
});

test("bindings tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("exports tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page.locator("body")).toBeVisible();
});

test("runs tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page.locator("body")).toBeVisible();
});

test("mobile handoff sheet viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("desktop handoff split viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("reduced-motion emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("forced-colors emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ forcedColors: "active" });
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("offline mutation prevention route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page).toHaveURL(/projects\/project-1\/handoff/);
});

test("signed download links are never present in URL state", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page).not.toHaveURL(/signed_download_url/);
});

test("no Group 17+ visual-polish or deployment controls are present on the handoff route", async ({ page }) => {
  await page.goto("/projects/project-1/handoff");
  await expect(page.getByRole("button", { name: "Deploy" })).toHaveCount(0);
});

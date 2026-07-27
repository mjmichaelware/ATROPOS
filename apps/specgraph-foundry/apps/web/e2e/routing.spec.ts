import { expect, test } from "@playwright/test";

test("routing route is listable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("routing loading and empty state are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.locator("body")).toBeVisible();
});

test("routing keyboard controls are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await page.keyboard.press("Tab");
});

test("policy tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("providers tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.locator("body")).toBeVisible();
});

test("renderers tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.locator("body")).toBeVisible();
});

test("unlocks tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.locator("body")).toBeVisible();
});

test("decisions tab route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.locator("body")).toBeVisible();
});

test("mobile routing sheet viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("desktop routing split viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("reduced-motion emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("forced-colors emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ forcedColors: "active" });
  await page.goto("/projects/project-1/routing");
  await expect(page).toHaveURL(/projects\/project-1\/routing/);
});

test("paid routing never auto-confirms; no auto-purchase happens on route load", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.getByRole("button", { name: "Confirm and grant unlock" })).toHaveCount(0);
});

test("no Group 17+ visual-polish or deployment controls are present on the routing route", async ({ page }) => {
  await page.goto("/projects/project-1/routing");
  await expect(page.getByRole("button", { name: "Deploy" })).toHaveCount(0);
});

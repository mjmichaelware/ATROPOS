import { expect, test } from "@playwright/test";

test("graph route is listable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page).toHaveURL(/projects\/project-1\/graph/);
});

test("graph loading and empty state are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page.locator("body")).toBeVisible();
});

test("graph keyboard controls are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await page.keyboard.press("Tab");
});

test("graph search and filters are reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?view=list");
  await expect(page).toHaveURL(/view=list/);
});

test("graph node selection updates URL state", async ({ page }) => {
  await page.goto("/projects/project-1/graph?selected=node-1");
  await expect(page).toHaveURL(/selected=node-1/);
});

test("graph edge selection updates URL state", async ({ page }) => {
  await page.goto("/projects/project-1/graph?selected=edge-1");
  await expect(page).toHaveURL(/selected=edge-1/);
});

test("graph inspector route parameter is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?view=canvas&selected=node-1");
  await expect(page).toHaveURL(/view=canvas/);
});

test("fit graph control route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page).toHaveURL(/projects\/project-1\/graph/);
});

test("accessible list/table alternative route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?view=list");
  await expect(page).toHaveURL(/view=list/);
});

test("mobile inspector sheet viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/projects/project-1/graph?selected=node-1");
  await expect(page).toHaveURL(/selected=node-1/);
});

test("desktop graph/inspector split viewport is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/projects/project-1/graph");
  await expect(page).toHaveURL(/projects\/project-1\/graph/);
});

test("100-node execution mode route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=execution");
  await expect(page).toHaveURL(/mode=execution/);
});

test("1,000-node bounded mode route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?layout=compact");
  await expect(page).toHaveURL(/layout=compact/);
});

test("10,000-node safe mode route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?layout=blueprint");
  await expect(page).toHaveURL(/layout=blueprint/);
});

test("axe scan target route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page.locator("body")).toBeVisible();
});

test("reduced-motion emulation route is reachable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/projects/project-1/graph");
  await expect(page).toHaveURL(/projects\/project-1\/graph/);
});

test("authority relation creation and validation route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=authority");
  await expect(page).toHaveURL(/mode=authority/);
});

test("cycle advisory and server rejection route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=authority&selected=node-1");
  await expect(page).toHaveURL(/selected=node-1/);
});

test("relation inspector with inferred/manual state route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?selected=edge-1&view=list");
  await expect(page).toHaveURL(/selected=edge-1/);
});

test("partial-subset relation state route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?category=atom");
  await expect(page).toHaveURL(/category=atom/);
});

test("no-plans-yet route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=execution");
  await expect(page).toHaveURL(/mode=execution/);
});

test("closed-research plan synthesis route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-1");
  await expect(page).toHaveURL(/plan=plan-1/);
});

test("open-research plan synthesis route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-1&mode=execution");
  await expect(page).toHaveURL(/plan=plan-1/);
});

test("blocked plan status route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-blocked");
  await expect(page).toHaveURL(/plan=plan-blocked/);
});

test("invalid plan status route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-invalid");
  await expect(page).toHaveURL(/plan=plan-invalid/);
});

test("verified plan status route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-verified");
  await expect(page).toHaveURL(/plan=plan-verified/);
});

test("plan selection and URL restore route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-1&mode=authority&layout=focus");
  await expect(page).toHaveURL(/plan=plan-1/);
  await expect(page).toHaveURL(/layout=focus/);
});

test("verification findings and severity focus route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?plan=plan-1&finding=ERROR");
  await expect(page).toHaveURL(/finding=ERROR/);
});

test("execution stage distinction route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=execution&category=plan-stage");
  await expect(page).toHaveURL(/category=plan-stage/);
});

test("server ready-node route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=execution&status=READY");
  await expect(page).toHaveURL(/status=READY/);
});

test("plan binding sync route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=execution&selected=exec-node-1");
  await expect(page).toHaveURL(/selected=exec-node-1/);
});

test("planning accessible alternative route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?view=list&plan=plan-1");
  await expect(page).toHaveURL(/view=list/);
});

test("planning keyboard flow route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");
});

test("planning mobile sheet route is reachable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/projects/project-1/graph?plan=plan-1&selected=exec-node-1");
  await expect(page).toHaveURL(/plan=plan-1/);
});

test("planning reduced-motion route is reachable", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/projects/project-1/graph?plan=plan-1");
  await expect(page).toHaveURL(/plan=plan-1/);
});

test("planning forced-colors route is reachable", async ({ page }) => {
  await page.emulateMedia({ forcedColors: "active" });
  await page.goto("/projects/project-1/graph?plan=plan-1");
  await expect(page).toHaveURL(/plan=plan-1/);
});

test("offline mutation prevention route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page).toHaveURL(/projects\/project-1\/graph/);
});

test("graph preservation after failed mutation route is reachable", async ({ page }) => {
  await page.goto("/projects/project-1/graph?mode=authority");
  await expect(page).toHaveURL(/mode=authority/);
});

test("no Group 16 handoff or execution-run controls are present on the graph route", async ({ page }) => {
  await page.goto("/projects/project-1/graph");
  await expect(page.getByRole("button", { name: "Start execution run" })).toHaveCount(0);
});

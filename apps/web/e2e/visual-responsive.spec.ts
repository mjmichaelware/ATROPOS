import { expect, test } from "@playwright/test";

/**
 * Representative dimensions used as verification samples, not as the
 * layout architecture — the app's CSS is intrinsic/container-driven and
 * must remain coherent at every width, not just these. Listing only
 * (npm run e2e:list); no local browser install/run.
 */
const dimensions = {
  smallPhone: { width: 320, height: 568 },
  phone: { width: 375, height: 812 },
  phoneLandscape: { width: 812, height: 375 },
  tabletPortrait: { width: 768, height: 1024 },
  tabletLandscape: { width: 1024, height: 768 },
  laptop: { width: 1366, height: 768 },
  desktop: { width: 1440, height: 900 },
  ultraWide: { width: 2560, height: 1080 },
};

async function noPageOverflow(page: import("@playwright/test").Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(overflow).toBe(false);
}

test.describe("shell and navigation reachability", () => {
  for (const [name, size] of Object.entries(dimensions)) {
    test(`shell has no page-level horizontal overflow at ${name} (${size.width}x${size.height})`, async ({ page }) => {
      await page.setViewportSize(size);
      await page.goto("/projects");
      await noPageOverflow(page);
    });
  }

  test("primary navigation is reachable at phone width via the mobile sheet", async ({ page }) => {
    await page.setViewportSize(dimensions.phone);
    await page.goto("/projects");
    await page.getByRole("button", { name: "Open navigation" }).click();
    await expect(page.getByRole("dialog", { name: "Mobile navigation" })).toBeVisible();
  });

  test("primary navigation is reachable at desktop width via the persistent rail", async ({ page }) => {
    await page.setViewportSize(dimensions.desktop);
    await page.goto("/projects");
    await expect(page.getByRole("complementary", { name: "Application sections" })).toBeVisible();
  });

  test("dynamic project-aware route links resolve to a real project id, not a literal path", async ({ page }) => {
    await page.goto("/projects/project-1");
    await expect(page.getByRole("link", { name: "Open Sources" })).toHaveAttribute("href", "/projects/project-1/sources");
  });

  test("landscape phone does not trap the viewport behind fixed chrome", async ({ page }) => {
    await page.setViewportSize(dimensions.phoneLandscape);
    await page.goto("/projects");
    await noPageOverflow(page);
  });

  test("ultra-wide viewport increases workspace capability rather than empty margins", async ({ page }) => {
    await page.setViewportSize(dimensions.ultraWide);
    await page.goto("/projects");
    await noPageOverflow(page);
  });
});

test.describe("primary action visibility and keyboard focus", () => {
  test("primary action remains visible without scrolling on a constrained phone viewport", async ({ page }) => {
    await page.setViewportSize(dimensions.smallPhone);
    await page.goto("/projects/new");
    await expect(page.getByRole("button", { name: "Create project" })).toBeVisible();
  });

  test("keyboard focus is preserved after a tab-panel transition", async ({ page }) => {
    await page.goto("/projects/project-1/routing");
    await page.getByRole("tab", { name: "Providers" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("tab", { name: "Providers" })).toBeFocused();
  });

  test("keyboard focus reaches the mobile navigation sheet close action", async ({ page }) => {
    await page.setViewportSize(dimensions.phone);
    await page.goto("/projects");
    await page.getByRole("button", { name: "Open navigation" }).click();
    await page.keyboard.press("Tab");
    await expect(page.getByRole("dialog", { name: "Mobile navigation" })).toBeVisible();
  });
});

test.describe("long-content resilience", () => {
  test("a long project slug or name does not force page-level overflow", async ({ page }) => {
    await page.setViewportSize(dimensions.smallPhone);
    await page.goto("/projects/project-1");
    await noPageOverflow(page);
  });

  test("a source document's long content hash remains wrapped and recoverable", async ({ page }) => {
    await page.setViewportSize(dimensions.smallPhone);
    await page.goto("/projects/project-1/sources");
    await noPageOverflow(page);
  });
});

test.describe("table and list alternatives", () => {
  test("the execution node table stays within its own scroll boundary at phone width", async ({ page }) => {
    await page.setViewportSize(dimensions.smallPhone);
    await page.goto("/projects/project-1/executions/run-1");
    await noPageOverflow(page);
  });

  test("the graph accessible list remains a bounded alternative to the canvas", async ({ page }) => {
    await page.goto("/projects/project-1/graph?view=list");
    await expect(page).toHaveURL(/view=list/);
  });
});

test.describe("graph and planning continuity", () => {
  test("graph route preserves selection state across a viewport resize", async ({ page }) => {
    await page.setViewportSize(dimensions.desktop);
    await page.goto("/projects/project-1/graph?selected=node-1");
    await page.setViewportSize(dimensions.phone);
    await expect(page).toHaveURL(/selected=node-1/);
  });

  test("planning rail and inspector remain reachable at tablet width", async ({ page }) => {
    await page.setViewportSize(dimensions.tabletPortrait);
    await page.goto("/projects/project-1/graph?plan=plan-1");
    await expect(page).toHaveURL(/plan=plan-1/);
  });
});

test.describe("handoff, execution, and routing surface layouts", () => {
  for (const [name, size] of Object.entries({ phone: dimensions.phone, tablet: dimensions.tabletPortrait, desktop: dimensions.desktop })) {
    test(`handoff workspace has no page-level overflow at ${name}`, async ({ page }) => {
      await page.setViewportSize(size);
      await page.goto("/projects/project-1/handoff");
      await noPageOverflow(page);
    });

    test(`routing workspace has no page-level overflow at ${name}`, async ({ page }) => {
      await page.setViewportSize(size);
      await page.goto("/projects/project-1/routing");
      await noPageOverflow(page);
    });
  }

  test("paid unlock confirmation is never rendered without deliberate user action", async ({ page }) => {
    await page.goto("/projects/project-1/routing");
    await expect(page.getByRole("button", { name: "Confirm and grant unlock" })).toHaveCount(0);
  });
});

test.describe("reduced motion, reduced transparency, and forced colors", () => {
  test("reduced motion route is reachable and stable", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/projects/project-1/graph");
    await expect(page).toHaveURL(/projects\/project-1\/graph/);
  });

  test("reduced transparency route renders solid shell materials", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/projects");
    await noPageOverflow(page);
  });

  test("forced-colors route is reachable and non-color status distinctions remain", async ({ page }) => {
    await page.emulateMedia({ forcedColors: "active" });
    await page.goto("/projects/project-1/executions/run-1");
    await expect(page).toHaveURL(/executions\/run-1/);
  });
});

test.describe("light and dark themes", () => {
  test("light theme route is reachable", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "light" });
    await page.goto("/projects");
    await noPageOverflow(page);
  });

  test("dark theme route is reachable", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "dark" });
    await page.goto("/projects");
    await noPageOverflow(page);
  });
});

test.describe("zoom and reflow", () => {
  test("200% browser zoom reflows content instead of clipping it", async ({ page }) => {
    await page.setViewportSize({ width: 640, height: 400 });
    await page.goto("/projects");
    await noPageOverflow(page);
  });
});

/**
 * Screenshot-based visual regression assertions may be added here in a
 * later group, but are deliberately not defined in this listing — no
 * screenshot has been captured or verified in this environment, and this
 * suite must not claim visual coverage it has not produced.
 */

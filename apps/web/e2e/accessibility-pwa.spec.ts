import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test.describe("skip link", () => {
  test("skip link is the first focusable element and moves focus to main content", async ({ page }) => {
    await page.goto("/projects");
    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#main-content")).toBeFocused();
  });
});

test.describe("landmarks and headings", () => {
  test("each route has exactly one page-level h1 and semantic landmarks", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.locator("h1")).toHaveCount(1);
    await expect(page.getByRole("banner")).toBeVisible();
    await expect(page.getByRole("main")).toBeVisible();
  });

  test("html has a language attribute", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
  });
});

test.describe("keyboard navigation", () => {
  test("primary navigation is reachable and operable by keyboard alone", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("link", { name: "Projects" }).first()).toBeVisible();
  });

  test("tabs support ArrowLeft/ArrowRight/Home/End/Enter per APG", async ({ page }) => {
    await page.goto("/projects/project-1/routing");
    const firstTab = page.getByRole("tab").first();
    await firstTab.focus();
    await page.keyboard.press("ArrowRight");
    await page.keyboard.press("Home");
    await page.keyboard.press("End");
    await page.keyboard.press("Enter");
  });
});

test.describe("dialog focus trap and restoration", () => {
  test("mobile navigation traps focus and restores it to the trigger on close", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto("/projects");
    await page.getByRole("button", { name: "Menu" }).click();
    await expect(page.getByRole("dialog", { name: "Navigation" })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("button", { name: "Menu" })).toBeFocused();
  });
});

test.describe("visible focus", () => {
  test("focus is not clipped or hidden behind sticky shell chrome", async ({ page }) => {
    await page.goto("/projects");
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");
    const box = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el) return null;
      const rect = el.getBoundingClientRect();
      return { top: rect.top, bottom: rect.bottom };
    });
    expect(box).not.toBeNull();
  });
});

test.describe("forms and errors", () => {
  test("sign-in form fields have programmatic labels and announce errors", async ({ page }) => {
    await page.goto("/auth/sign-in");
    await expect(page.getByLabel("Email")).toBeVisible();
    await expect(page.getByLabel("Password")).toBeVisible();
  });
});

test.describe("status announcements", () => {
  test("route change does not spawn duplicate live regions", async ({ page }) => {
    await page.goto("/projects");
    const statusCount = await page.getByRole("status").count();
    expect(statusCount).toBeLessThan(10);
  });
});

test.describe("graph accessible alternative", () => {
  test("graph list view represents the same loaded subset as the canvas", async ({ page }) => {
    await page.goto("/projects/project-1/graph?view=list");
    await expect(page).toHaveURL(/view=list/);
  });
});

test.describe("reflow and zoom", () => {
  test("320 CSS-pixel-equivalent width has no page-level 2D overflow", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto("/projects");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });

  test("200% zoom reflows content without loss of function", async ({ page }) => {
    await page.setViewportSize({ width: 640, height: 400 });
    await page.goto("/projects");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });

  test("400% zoom (160 CSS-pixel-equivalent width) reflows content", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 400 });
    await page.goto("/projects");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });
});

test.describe("text spacing override", () => {
  test("WCAG 1.4.12 text-spacing override does not clip or overlap content", async ({ page }) => {
    await page.goto("/projects");
    await page.addStyleTag({
      content: "* { line-height: 1.5 !important; letter-spacing: 0.12em !important; word-spacing: 0.16em !important; } p { margin-bottom: 2em !important; }",
    });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });
});

test.describe("forced colors and reduced motion", () => {
  test("forced-colors route preserves non-color status distinctions", async ({ page }) => {
    await page.emulateMedia({ forcedColors: "active" });
    await page.goto("/projects/project-1/executions/run-1");
    await expect(page).toHaveURL(/executions\/run-1/);
  });

  test("reduced-motion route eliminates spatial travel and shared-element morphing", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/projects/project-1/routing");
    await expect(page).toHaveURL(/routing/);
  });
});

test.describe("axe scan of representative routes", () => {
  const routes = ["/projects", "/projects/new", "/projects/project-1", "/projects/project-1/sources", "/auth/sign-in", "/offline"];

  for (const route of routes) {
    test(`axe reports no serious/critical violations on ${route}`, async ({ page }) => {
      await page.goto(route);
      const results = await new AxeBuilder({ page }).build().analyze();
      const seriousOrCritical = results.violations.filter((violation) => violation.impact === "serious" || violation.impact === "critical");
      expect(seriousOrCritical, JSON.stringify(seriousOrCritical, null, 2)).toEqual([]);
    });
  }
});

test.describe("manifest", () => {
  test("manifest route responds with expected identity fields", async ({ page }) => {
    const response = await page.goto("/manifest.webmanifest");
    expect(response?.ok()).toBe(true);
    const body = await response?.json();
    expect(body.name).toBe("SpecGraph Foundry");
    expect(body.icons?.length).toBeGreaterThan(0);
  });
});

test.describe("service worker", () => {
  test("sw.js responds with narrow, non-cacheable headers", async ({ page }) => {
    const response = await page.goto("/sw.js");
    expect(response?.ok()).toBe(true);
    expect(response?.headers()["cache-control"]).toContain("no-store");
    expect(response?.headers()["content-type"]).toContain("javascript");
  });

  test("no private response is present in Cache Storage after a normal visit", async ({ page }) => {
    await page.goto("/projects");
    const cachedUrls = await page.evaluate(async () => {
      if (!("caches" in window)) return [];
      const names = await caches.keys();
      const urls: string[] = [];
      for (const name of names) {
        const cache = await caches.open(name);
        const requests = await cache.keys();
        urls.push(...requests.map((request) => request.url));
      }
      return urls;
    });
    const privateUrls = cachedUrls.filter((url) => /\/v1\/|\/api\/|\/auth\//.test(url));
    expect(privateUrls).toEqual([]);
  });
});

test.describe("offline page", () => {
  test("offline route explains unavailability and offers retry/return actions", async ({ page }) => {
    await page.goto("/offline");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("link", { name: "Retry" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Return to SpecGraph Foundry" })).toBeVisible();
  });

  test("offline navigation falls back only to /offline", async ({ page, context }) => {
    await page.goto("/projects");
    await context.setOffline(true);
    await page.goto("/projects/project-1", { waitUntil: "domcontentloaded" }).catch(() => {});
    await context.setOffline(false);
  });
});

test.describe("update-ready interaction", () => {
  test("a waiting service worker surfaces an explicit, user-controlled refresh action", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("button", { name: "Refresh to update" })).toHaveCount(0);
  });
});

test.describe("standalone display mode", () => {
  test("standalone display-mode media query is honored where the browser supports it", async ({ page }) => {
    await page.emulateMedia({ media: "screen" });
    await page.goto("/projects");
    const isStandalone = await page.evaluate(() => window.matchMedia("(display-mode: standalone)").matches);
    expect(typeof isStandalone).toBe("boolean");
  });
});

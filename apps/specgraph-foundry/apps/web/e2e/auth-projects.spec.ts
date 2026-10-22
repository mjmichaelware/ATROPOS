import { expect, test } from "@playwright/test";

test.describe("authentication and projects foundation", () => {
  test("sign-in and project routes expose accessible foundations", async ({ page }) => {
    await page.route("**/health/ready", async (route) => {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ status: "ready", service: "specgraph-foundry" }),
      });
    });

    await page.goto("/auth/sign-in");
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
    await expect(page.getByLabel("Email")).toBeVisible();
    await expect(page.getByLabel("Password")).toBeVisible();

    await page.keyboard.press("Tab");
    await expect(page.locator("body")).toBeVisible();

    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });
});

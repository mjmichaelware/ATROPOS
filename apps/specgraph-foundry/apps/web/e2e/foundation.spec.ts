import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("foundation shell is keyboard and accessibility smoke testable", async ({ page }) => {
  const messages: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      messages.push(message.text());
    }
  });

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Authority-first delivery starts here." })).toBeVisible();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("button", { name: "Open navigation" })).toBeVisible();

  const results = await new AxeBuilder({ page }).include("main").analyze();
  expect(results.violations).toEqual([]);
  expect(messages).toEqual([]);
});

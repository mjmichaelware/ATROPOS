import { expect, test } from "@playwright/test";

test("research workspace routes are listable", async ({ page }) => {
  await page.goto("/projects/project-1/research");
  await expect(page).toHaveURL(/projects\/project-1\/research/);
});

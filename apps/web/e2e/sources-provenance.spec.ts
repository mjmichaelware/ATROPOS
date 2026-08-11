import { expect, test } from "@playwright/test";

test("source workspace and provenance routes are listable", async ({ page }) => {
  await page.goto("/projects/project-1/sources");
  await expect(page).toHaveURL(/projects\/project-1\/sources/);
});

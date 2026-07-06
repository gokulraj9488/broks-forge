import { test, expect } from "../fixtures/test";

test.describe("Responsive layout", () => {
  test("login renders on a mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/login");
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });

  test("the mobile drawer opens the primary navigation", async ({ page, signedInUser }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/dashboard");
    await page.getByRole("button", { name: "Open menu" }).click();
    await expect(page.getByRole("link", { name: "Organizations", exact: true })).toBeVisible();
  });
});

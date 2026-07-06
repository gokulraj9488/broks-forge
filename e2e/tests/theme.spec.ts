import { test, expect } from "../fixtures/test";

test.describe("Theme (dark / light)", () => {
  test("next-themes applies a theme class to <html>", async ({ page }) => {
    await page.goto("/login");
    const cls = (await page.locator("html").getAttribute("class")) ?? "";
    expect(cls).toMatch(/dark|light/);
  });

  test("honours the OS dark colour scheme", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "dark" });
    await page.goto("/login");
    await expect(page.locator("html")).toHaveClass(/dark/);
  });

  test("honours the OS light colour scheme", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "light" });
    await page.goto("/login");
    await expect(page.locator("html")).not.toHaveClass(/dark/);
  });
});

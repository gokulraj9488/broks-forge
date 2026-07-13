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

  test("is dark-first and does not follow an OS light preference (enableSystem is off by design)", async ({
    page,
  }) => {
    // The app deliberately sets next-themes defaultTheme="dark" enableSystem={false}
    // (see frontend/src/components/providers.tsx), so it stays dark regardless of the OS scheme;
    // users switch themes manually. This asserts that intentional dark-first contract.
    await page.emulateMedia({ colorScheme: "light" });
    await page.goto("/login");
    await expect(page.locator("html")).toHaveClass(/dark/);
  });
});

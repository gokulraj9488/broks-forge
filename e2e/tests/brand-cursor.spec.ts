import { test, expect } from "../fixtures/test";

/**
 * The branded forge-hammer cursor (components/brand/brand-cursor.tsx): mounts and hides the
 * native cursor on a normal desktop pointer session, but must stay fully inert — native cursor
 * untouched — on touch devices and under prefers-reduced-motion.
 */
test.describe("Brand cursor", () => {
  test("mounts and hides the native cursor on a desktop pointer session", async ({ page }) => {
    await page.goto("/login");
    await expect(page.locator(".brand-cursor-dot")).toBeAttached();
    await expect(page.locator("body")).toHaveClass(/brand-cursor-active/);

    const bodyCursor = await page.evaluate(() => getComputedStyle(document.body).cursor);
    expect(bodyCursor).toBe("none");
  });

  test("keeps the native cursor over a text input (typing is unaffected)", async ({ page }) => {
    await page.goto("/login");
    const emailInput = page.getByLabel("Email");
    const inputCursor = await emailInput.evaluate((el) => getComputedStyle(el).cursor);
    expect(inputCursor).not.toBe("none");
    await emailInput.fill("still-works@example.com");
    await expect(emailInput).toHaveValue("still-works@example.com");
  });

  test("stays inert under prefers-reduced-motion", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/login");
    await expect(page.locator("body")).not.toHaveClass(/brand-cursor-active/);
    const bodyCursor = await page.evaluate(() => getComputedStyle(document.body).cursor);
    expect(bodyCursor).not.toBe("none");
  });
});

test.describe("Brand cursor (touch context)", () => {
  test.use({ hasTouch: true, isMobile: true });

  test("stays inert on a touch device", async ({ page }) => {
    await page.goto("/login");
    await expect(page.locator("body")).not.toHaveClass(/brand-cursor-active/);
    const bodyCursor = await page.evaluate(() => getComputedStyle(document.body).cursor);
    expect(bodyCursor).not.toBe("none");
  });
});

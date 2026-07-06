import { test, expect } from "../fixtures/test";

test.describe("Idle session timeout", () => {
  test("the session-expired reason shows an explanatory banner on login", async ({ page }) => {
    await page.goto("/login?reason=session-expired");
    await expect(page.getByText(/session expired due to inactivity/i)).toBeVisible();
  });

  test("protected routes redirect to login when there is no session", async ({ page }) => {
    await page.goto("/settings");
    await expect(page).toHaveURL(/\/login/);
  });
});

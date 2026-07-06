import { test, expect, PASSWORD } from "../fixtures/test";
import { registerViaApi, uniqueEmail } from "../fixtures/api";

test.describe("Authentication", () => {
  test("register a new account via the UI lands on the dashboard", async ({ page }) => {
    const email = uniqueEmail();
    await page.goto("/register");
    await page.getByLabel("First name").fill("Ada");
    await page.getByLabel("Last name").fill("Lovelace");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test("log in with valid credentials", async ({ page, request }) => {
    const user = await registerViaApi(request);
    await page.goto("/login");
    await page.getByLabel("Email").fill(user.email);
    await page.getByLabel("Password").fill(user.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test("wrong credentials keep the user on the login page with an error", async ({ page, request }) => {
    const user = await registerViaApi(request);
    await page.goto("/login");
    await page.getByLabel("Email").fill(user.email);
    await page.getByLabel("Password").fill("TotallyWrong!1");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/unable to sign in|invalid|incorrect|credentials/i)).toBeVisible();
  });

  test("log out returns to the login page", async ({ page, signedInUser }) => {
    // The avatar dropdown lives in the header; it is the only visible header button on desktop.
    await page.locator("header button").last().click();
    await page.getByRole("menuitem", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test("forgot password returns a generic confirmation (no account enumeration)", async ({ page }) => {
    await page.goto("/forgot-password");
    await page.getByLabel("Email").fill(uniqueEmail());
    await page.getByRole("button", { name: /send|reset|link|email/i }).first().click();
    await expect(page.getByText(/if an account exists/i)).toBeVisible();
  });

  test("reset-password with an invalid token surfaces an error", async ({ page }) => {
    await page.goto("/reset-password?token=invalid-token-123");
    await page.getByLabel(/new password|password/i).first().fill(PASSWORD);
    await page.getByRole("button", { name: /reset|set|save|update/i }).first().click();
    await expect(page.getByText(/invalid|expired|unable/i)).toBeVisible();
  });

  test("unauthenticated dashboard access redirects to login", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });
});

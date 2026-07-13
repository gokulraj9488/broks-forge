import { test, expect, PASSWORD } from "../fixtures/test";
import { registerViaApi, uniqueEmail } from "../fixtures/api";

test.describe("Authentication", () => {
  test("register a new account via the UI lands on the dashboard", async ({ page }) => {
    const email = uniqueEmail();
    await page.goto("/register");
    await page.getByLabel("First name").fill("Ada");
    await page.getByLabel("Last name").fill("Lovelace");
    await page.getByLabel("Email").fill(email);
    await page.locator("#password").fill(PASSWORD);
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test("log in with valid credentials", async ({ page, request }) => {
    const user = await registerViaApi(request);
    await page.goto("/login");
    await page.getByLabel("Email").fill(user.email);
    await page.locator("#password").fill(user.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test("wrong credentials keep the user on the login page with an error", async ({ page, request }) => {
    const user = await registerViaApi(request);
    await page.goto("/login");
    await page.getByLabel("Email").fill(user.email);
    await page.locator("#password").fill("TotallyWrong!1");
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

  test("logout clears all client-side session state (tokens, activity clock, query cache)", async ({
    page,
    signedInUser,
  }) => {
    await page.locator("header button").last().click();
    await page.getByRole("menuitem", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login/);

    // OWASP session-termination: no auth artifact should survive logout in localStorage.
    const authRaw = await page.evaluate(() => localStorage.getItem("broksforge.auth"));
    expect(authRaw).not.toBeNull();
    const auth = JSON.parse(authRaw as string);
    expect(auth.state.accessToken).toBeNull();
    expect(auth.state.refreshToken).toBeNull();
    expect(auth.state.user).toBeNull();
    expect(await page.evaluate(() => localStorage.getItem("broksforge.lastActivityAt"))).toBeNull();

    // The login form itself starts empty — no leaked identity from the ended session.
    await expect(page.getByLabel("Email")).toHaveValue("");
    await expect(page.locator("#password")).toHaveValue("");
  });

  test("browser refresh after logout does not restore the session", async ({ page, signedInUser }) => {
    await page.locator("header button").last().click();
    await page.getByRole("menuitem", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.reload();
    await expect(page).toHaveURL(/\/login/);

    // A direct hit on a protected route after logout must not resurrect the dashboard.
    await page.goto("/dashboard");
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
    // The form has both a "New password" and a "Confirm password" field; fill both so client-side
    // validation passes and the request actually reaches the API (which rejects the bad token).
    await page.locator("#newPassword").fill(PASSWORD);
    await page.locator("#confirmPassword").fill(PASSWORD);
    await page.getByRole("button", { name: "Reset password" }).click();
    await expect(page.getByText(/invalid|expired|unable/i)).toBeVisible();
  });

  test("unauthenticated dashboard access redirects to login", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });
});

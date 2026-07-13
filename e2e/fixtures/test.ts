import { test as base, expect, Page } from "@playwright/test";
import { PASSWORD, registerViaApi, SeededUser } from "./api";

/**
 * Custom fixtures:
 *  - `signedInUser`: a fresh account registered via the API and logged into the browser via the UI
 *    (the real login flow), leaving the page on the dashboard.
 */
type Fixtures = {
  signedInUser: SeededUser;
};

export async function loginViaUi(page: Page, email: string, password: string): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  // Target the password <input> by id. getByLabel("Password") is ambiguous (it also matches the
  // "Show password" toggle button), and exact:true misses because the required marker makes the
  // accessible name "Password *". The stable id is the unambiguous locator.
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL("**/dashboard", { timeout: 15_000 });
}

export const test = base.extend<Fixtures>({
  signedInUser: async ({ page, request }, use) => {
    const user = await registerViaApi(request);
    await loginViaUi(page, user.email, user.password);
    await use(user);
  },
});

export { expect, PASSWORD };

import { test, expect } from "../fixtures/test";

/**
 * The OTP password-change wizard (ADR 0017): current password -> emailed 6-digit code -> new
 * password. The code is delivered by e-mail (console LoggingEmailService in dev), so this spec
 * verifies the flow advances from step 1 to the code-entry step; completing it requires reading the
 * dev e-mail log, which is covered by the backend/API suites.
 */
test.describe("OTP password change", () => {
  test("requesting a code advances the wizard to code entry", async ({ page, signedInUser }) => {
    await page.goto("/settings");
    await page.getByLabel(/current password/i).first().fill(signedInUser.password);
    await page.getByRole("button", { name: /send|code|generate|continue/i }).first().click();
    await expect(page.getByText(/code|6.digit|verify/i).first()).toBeVisible();
  });
});

import { test, expect } from "../fixtures/test";

test.describe("404 page", () => {
  test("an unknown route renders the custom not-found page", async ({ page }) => {
    await page.goto("/this-route-does-not-exist-9f8a7b6c");
    await expect(
      page.getByText(/404|not found|page.*(doesn't|does not) exist|can't find/i).first(),
    ).toBeVisible();
  });

  test("the 404 page offers a way back", async ({ page }) => {
    await page.goto("/nope-nope-nope");
    await expect(page.getByRole("link", { name: /home|dashboard|back/i }).first()).toBeVisible();
  });
});

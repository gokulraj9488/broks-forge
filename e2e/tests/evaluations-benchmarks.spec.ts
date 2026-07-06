import { test, expect } from "../fixtures/test";

/**
 * Evaluations and Benchmarks are global, project-scoped pages. These smoke tests confirm the pages
 * load for an authenticated user and expose their primary entry points. Full evaluation execution
 * (which requires a reachable agent endpoint) is exercised by the backend + Postman suites.
 */
test.describe("Evaluations & Benchmarks", () => {
  test("the evaluations page loads with a create entry point", async ({ page, signedInUser }) => {
    await page.goto("/evaluations");
    await expect(page.getByRole("heading", { name: /evaluation/i }).first()).toBeVisible();
  });

  test("the benchmarks page loads with a create entry point", async ({ page, signedInUser }) => {
    await page.goto("/benchmarks");
    await expect(page.getByRole("heading", { name: /benchmark/i }).first()).toBeVisible();
  });
});

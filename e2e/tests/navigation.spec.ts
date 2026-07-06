import { test, expect } from "../fixtures/test";

const SECTIONS: Array<[string, RegExp]> = [
  ["Organizations", /\/organizations/],
  ["Projects", /\/projects/],
  ["Agents", /\/agents/],
  ["Datasets", /\/datasets/],
  ["Prompts", /\/prompts/],
  ["Evaluations", /\/evaluations/],
  ["Benchmarks", /\/benchmarks/],
  ["Analytics", /\/analytics/],
  ["Settings", /\/settings/],
];

test.describe("Sidebar navigation", () => {
  test("navigates to every primary section", async ({ page, signedInUser }) => {
    for (const [label, urlPattern] of SECTIONS) {
      await page.getByRole("link", { name: label, exact: true }).first().click();
      await expect(page).toHaveURL(urlPattern);
    }
  });
});

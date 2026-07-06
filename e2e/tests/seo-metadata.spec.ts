import { test, expect } from "../fixtures/test";

test.describe("SEO & production metadata", () => {
  test("pages expose a branded title and meta description", async ({ page }) => {
    await page.goto("/login");
    await expect(page).toHaveTitle(/Brok's Forge/);
    await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", /.+/);
  });

  test("Open Graph and Twitter card tags are present", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator('meta[property="og:title"]')).toHaveCount(1);
    await expect(page.locator('meta[name="twitter:card"]')).toHaveCount(1);
  });

  test("robots.txt is served", async ({ request }) => {
    const res = await request.get("/robots.txt");
    expect(res.ok()).toBeTruthy();
    expect(await res.text()).toMatch(/user-agent/i);
  });

  test("sitemap.xml is served", async ({ request }) => {
    const res = await request.get("/sitemap.xml");
    expect(res.ok()).toBeTruthy();
    expect(await res.text()).toContain("<urlset");
  });

  test("web app manifest is served", async ({ request }) => {
    const res = await request.get("/manifest.webmanifest");
    expect(res.ok()).toBeTruthy();
  });
});

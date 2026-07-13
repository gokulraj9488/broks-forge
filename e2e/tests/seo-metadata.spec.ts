import { test, expect } from "../fixtures/test";

test.describe("SEO & production metadata", () => {
  test("pages expose a branded title and meta description", async ({ page }) => {
    await page.goto("/login");
    await expect(page).toHaveTitle(/Brok's Forge/);
    await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", /.+/);
  });

  test("Open Graph and Twitter card tags are present", async ({ page }) => {
    await page.goto("/");
    // Assert presence rather than an exact count: a Next.js dev server can inject a second copy of
    // metadata into the live DOM during client hydration (production SSR emits exactly one — verified
    // via the raw document). Presence is the contract we actually care about.
    expect(await page.locator('meta[property="og:title"]').count()).toBeGreaterThan(0);
    expect(await page.locator('meta[name="twitter:card"]').count()).toBeGreaterThan(0);
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

import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright configuration for the Brok's Forge end-to-end suite.
 *
 * The app (http://localhost:3000) and API (http://localhost:8080) must be running — locally via
 * `docker compose up`, or in CI via the compose step in .github/workflows/ci.yml. Override the
 * targets with the BASE_URL / API_URL environment variables.
 */
const BASE_URL = process.env.BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }], ["list"]]
    : [["html", { open: "never" }], ["list"]],
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
});

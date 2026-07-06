import { test, expect, loginViaUi } from "../fixtures/test";
import { API_URL, seedProjectScope } from "../fixtures/api";

test.describe("Datasets & Prompts", () => {
  test("a created dataset appears in the datasets list", async ({ page, request }) => {
    const { user, org, project } = await seedProjectScope(request);
    const name = `Golden Set ${Date.now()}`;
    const res = await request.post(
      `${API_URL}/api/v1/organizations/${org.id}/projects/${project.id}/datasets`,
      { headers: { Authorization: `Bearer ${user.token}` }, data: { name } },
    );
    expect(res.status()).toBe(201);

    await loginViaUi(page, user.email, user.password);
    await page.goto("/datasets");
    await expect(page.getByText(name)).toBeVisible();
  });

  test("a created prompt appears in the prompts list", async ({ page, request }) => {
    const { user, org, project } = await seedProjectScope(request);
    const name = `System Prompt ${Date.now()}`;
    const res = await request.post(
      `${API_URL}/api/v1/organizations/${org.id}/projects/${project.id}/prompts`,
      { headers: { Authorization: `Bearer ${user.token}` }, data: { name } },
    );
    expect(res.status()).toBe(201);

    await loginViaUi(page, user.email, user.password);
    await page.goto("/prompts");
    await expect(page.getByText(name)).toBeVisible();
  });
});

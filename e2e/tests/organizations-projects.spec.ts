import { test, expect, loginViaUi } from "../fixtures/test";
import { registerViaApi, apiCreateOrganization, apiCreateProject } from "../fixtures/api";

test.describe("Organizations & Projects", () => {
  test("a created organization appears in the organizations list", async ({ page, request }) => {
    const user = await registerViaApi(request);
    const name = `Acme ${Date.now()}`;
    await apiCreateOrganization(request, user.token, name);

    await loginViaUi(page, user.email, user.password);
    await page.goto("/organizations");
    await expect(page.getByText(name)).toBeVisible();
  });

  test("a created project appears under its organization", async ({ page, request }) => {
    const user = await registerViaApi(request);
    const org = await apiCreateOrganization(request, user.token, `Org ${Date.now()}`);
    const projectName = `Platform ${Date.now()}`;
    await apiCreateProject(request, user.token, org.id, projectName);

    await loginViaUi(page, user.email, user.password);
    await page.goto(`/organizations/${org.id}`);
    await expect(page.getByText(projectName)).toBeVisible();
  });
});

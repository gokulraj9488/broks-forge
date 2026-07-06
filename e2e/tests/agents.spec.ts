import { test, expect, loginViaUi } from "../fixtures/test";
import { apiRegisterAgent, seedProjectScope } from "../fixtures/api";

test.describe("Agents", () => {
  test("a registered agent appears in the agents list", async ({ page, request }) => {
    const { user, org, project } = await seedProjectScope(request);
    const name = `Seeded Agent ${Date.now()}`;
    await apiRegisterAgent(request, user.token, org.id, project.id, name);

    await loginViaUi(page, user.email, user.password);
    await page.goto("/agents");
    await expect(page.getByText(name)).toBeVisible();
  });

  test("register an agent through the dialog", async ({ page, request }) => {
    const { user, org, project } = await seedProjectScope(request);
    await loginViaUi(page, user.email, user.password);
    await page.goto(`/organizations/${org.id}/projects/${project.id}`);

    // The project page may present resources under tabs; open the Agents tab if present.
    const agentsTab = page.getByRole("tab", { name: "Agents" });
    if (await agentsTab.count()) await agentsTab.first().click();

    await page.getByRole("button", { name: "Register agent" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Name").fill(`UI Agent ${Date.now()}`);
    await dialog.getByLabel("Endpoint URL").fill("https://api.example.com/agent");
    await dialog.getByRole("button", { name: "Register agent" }).click();

    await expect(page).toHaveURL(/agents\/[0-9a-fA-F-]{36}/);
  });

  test("an agent requiring auth shows a 'setup required' state until credentialed", async ({ page, request }) => {
    const { user, org, project } = await seedProjectScope(request);
    const name = `Auth Agent ${Date.now()}`;
    await apiRegisterAgent(request, user.token, org.id, project.id, name, "API_KEY");

    await loginViaUi(page, user.email, user.password);
    await page.goto("/agents");
    await expect(page.getByText(name)).toBeVisible();
    await expect(page.getByText(/setup required/i).first()).toBeVisible();
  });
});

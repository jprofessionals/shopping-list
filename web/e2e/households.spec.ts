import { test, expect } from '@playwright/test';
import { getApiUrl } from './e2e-utils';

const API_URL = getApiUrl();

test.describe('Households', () => {
  let authToken: string;
  let testUserEmail: string;

  test.beforeEach(async ({ page }) => {
    // Create a unique test user for each test
    testUserEmail = `household-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testUserEmail,
        password: 'password123',
        displayName: 'Household Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();
    authToken = token;

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();

    // Verify logged in
    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible();

    // Navigate to households page
    await page.getByRole('link', { name: 'Households', exact: true }).click();
  });

  test('should create a new household', async ({ page }) => {
    const householdName = `Test Household ${Date.now()}`;

    // Click create household button (use exact text to avoid matching "Create your first household")
    await page.getByRole('button', { name: 'Create Household' }).click();

    // Fill the modal form
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByLabel(/name/i).fill(householdName);
    await dialog.getByRole('button', { name: 'Create' }).click();

    // Modal should close and household should appear
    await expect(dialog).not.toBeVisible();
    await expect(page.getByText(householdName)).toBeVisible();
  });

  test('should view household details', async ({ page }) => {
    const householdName = `Detail Household ${Date.now()}`;

    // Create household via API
    await page.request.post(`${API_URL}/households`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: householdName },
    });

    // Reload and navigate to households page
    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();

    // Click on the household
    await page.getByText(householdName).click();

    // Should see household detail view with members section
    await expect(page.getByRole('heading', { name: /members/i })).toBeVisible();
    // Check for user in the members list (use listitem to avoid matching header)
    await expect(page.getByRole('listitem').getByText('Household Test User')).toBeVisible();
  });

  test('should delete a household as owner', async ({ page }) => {
    const householdName = `Delete Household ${Date.now()}`;

    // Create household via API
    await page.request.post(`${API_URL}/households`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: householdName },
    });

    // Reload and navigate to households page, then to household detail
    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(householdName).click();

    // Wait for detail view to load
    await expect(page.getByRole('heading', { name: /members/i })).toBeVisible();

    // Handle the browser confirm dialog
    page.on('dialog', (dialog) => dialog.accept());

    // Click delete button (as owner)
    await page.getByRole('button', { name: 'Delete Household' }).click();

    // Should navigate back and household should not be in the list
    // Wait for the "Back to households" button to disappear (indicating we're back on list view)
    await expect(page.getByText('Back to households')).not.toBeVisible({ timeout: 5000 });

    // Household name should no longer appear in the list
    await expect(page.getByRole('button', { name: householdName })).not.toBeVisible();
  });
});

import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080';

test.describe('Shopping Lists', () => {
  let authToken: string;

  test.beforeEach(async ({ page }) => {
    // Create a unique test user for each test
    const testUserEmail = `list-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testUserEmail,
        password: 'password123',
        displayName: 'List Test User',
      },
    });
    const { token } = await registerResponse.json();
    authToken = token;

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();

    // Wait for app to authenticate and navigate to Shopping Lists tab
    await expect(page.getByRole('link', { name: /^lists$/i })).toBeVisible({
      timeout: 10000,
    });
    await page.getByRole('link', { name: /^lists$/i }).click();
  });

  test('should create a personal shopping list', async ({ page }) => {
    const listName = `Personal List ${Date.now()}`;

    // Click create list button (use exact text to avoid matching "Create your first list")
    await page.getByRole('button', { name: 'Create List' }).click();

    // Fill the modal form
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByLabel(/name/i).fill(listName);
    // Ensure personal list is selected (should be default)
    await dialog.getByRole('button', { name: 'Create' }).click();

    // Modal should close and list should appear
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
    // List should appear in the Personal Lists section
    await expect(page.getByText(listName)).toBeVisible({ timeout: 10000 });
  });

  test('should create a household shopping list', async ({ page }) => {
    // First create a household
    const householdName = `List Test Household ${Date.now()}`;
    await page.request.post(`${API_URL}/households`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: householdName },
    });

    // Reload to get updated household list - wait for app to re-authenticate
    await page.reload();
    await expect(page.getByRole('link', { name: /^lists$/i })).toBeVisible({
      timeout: 10000,
    });
    await page.getByRole('link', { name: /^lists$/i }).click();

    const listName = `Household List ${Date.now()}`;

    // Click create list button (use exact text to avoid matching "Create your first list")
    await page.getByRole('button', { name: 'Create List' }).click();

    // Fill the modal form and select household
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByLabel(/name/i).fill(listName);

    // Select the household from dropdown (the household name is visible as option text)
    const householdSelect = dialog.getByRole('combobox');
    if (await householdSelect.isVisible()) {
      // Use partial text match for the option
      await householdSelect.selectOption({ label: householdName });
    }

    await dialog.getByRole('button', { name: 'Create' }).click();

    // Modal should close and list should appear under household
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await expect(page.getByText(listName)).toBeVisible();
  });

  test.describe('List Items', () => {
    const listName = `Item Test List`;

    test.beforeEach(async ({ page }) => {
      // Create a list via API
      const listResponse = await page.request.post(`${API_URL}/lists`, {
        headers: { Authorization: `Bearer ${authToken}` },
        data: {
          name: `${listName} ${Date.now()}`,
          isPersonal: true,
        },
      });
      await listResponse.json();

      // Navigate to the list - wait for app to re-authenticate after reload
      await page.reload();
      await expect(page.getByRole('link', { name: /^lists$/i })).toBeVisible({
        timeout: 10000,
      });
      await page.getByRole('link', { name: /^lists$/i }).click();
      await page.getByText(new RegExp(listName)).click();
    });

    test('should add an item to the list', async ({ page }) => {
      const itemName = 'Organic Bananas';

      // Find the add item input and click Add button
      await page.getByPlaceholder('Item name').fill(itemName);
      await page.getByRole('button', { name: 'Add', exact: true }).click();

      // Item should appear in the list
      await expect(page.getByText(itemName)).toBeVisible({ timeout: 5000 });
    });

    test('should check and uncheck an item', async ({ page }) => {
      const itemName = 'Fresh Avocados';

      // Add an item first
      await page.getByPlaceholder('Item name').fill(itemName);
      await page.getByRole('button', { name: 'Add', exact: true }).click();

      // Wait for the item to appear
      await expect(page.getByText(itemName)).toBeVisible();

      // Find the item's checkbox and click it (use click instead of check to handle async API)
      const itemRow = page.locator('li').filter({ hasText: itemName });
      const checkbox = itemRow.getByRole('checkbox');

      // Click to check
      await checkbox.click();
      await expect(checkbox).toBeChecked({ timeout: 5000 });

      // Click to uncheck
      await checkbox.click();
      await expect(checkbox).not.toBeChecked({ timeout: 5000 });
    });

    test('should delete an item', async ({ page }) => {
      const itemName = 'Whole Wheat Bread';

      // Add an item first
      await page.getByPlaceholder('Item name').fill(itemName);
      await page.getByRole('button', { name: 'Add', exact: true }).click();

      // Wait for item to appear
      await expect(page.getByText(itemName)).toBeVisible();

      // Find and click the delete button for this item (uses aria-label="Delete")
      const itemRow = page.locator('li').filter({ hasText: itemName });
      const deleteButton = itemRow.getByRole('button', { name: 'Delete' });
      await deleteButton.click();

      // Item should be removed
      await expect(page.getByText(itemName)).not.toBeVisible({ timeout: 5000 });
    });
  });
});

import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Pin/Unpin Lists', () => {
  let authToken: string;
  let listName: string;

  test.beforeEach(async ({ page }) => {
    const testEmail = `pin-test-${Date.now()}@example.com`;
    listName = `Pin Test List ${Date.now()}`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testEmail,
        password: 'password123',
        displayName: 'Pin Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();
    authToken = token;

    // Create a list via API
    await page.request.post(`${API_URL}/lists`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: listName, isPersonal: true },
    });

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();

    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
      timeout: 10000,
    });
  });

  test('should pin and unpin a list', async ({ page }) => {
    // Navigate to lists page
    await page.getByRole('link', { name: 'Lists', exact: true }).click();

    // Click on the list to open it
    await page.getByText(listName).click({ timeout: 10000 });

    // Find and click the pin toggle button
    const pinToggle = page.getByTestId('pin-toggle');
    await expect(pinToggle).toBeVisible({ timeout: 10000 });

    // Initially should show "Pin" as aria-label
    await expect(pinToggle).toHaveAttribute('aria-label', 'Pin');

    // Pin the list
    await pinToggle.click();

    // Should now show "Unpin" as aria-label
    await expect(pinToggle).toHaveAttribute('aria-label', 'Unpin', { timeout: 5000 });

    // Unpin the list
    await pinToggle.click();

    // Should revert to "Pin" as aria-label
    await expect(pinToggle).toHaveAttribute('aria-label', 'Pin', { timeout: 5000 });
  });
});

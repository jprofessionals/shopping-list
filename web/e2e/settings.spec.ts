import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Settings Page', () => {
  let authToken: string;
  let testDisplayName: string;
  let testEmail: string;

  test.beforeEach(async ({ page }) => {
    testDisplayName = `Settings User ${Date.now()}`;
    testEmail = `settings-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testEmail,
        password: 'password123',
        displayName: testDisplayName,
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();
    authToken = token;

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();

    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
      timeout: 10000,
    });

    // Navigate to settings page
    await page.getByRole('link', { name: 'Profile' }).first().click();
  });

  test('should display settings page', async ({ page }) => {
    // Verify settings page loads
    await expect(page.getByTestId('settings-page')).toBeVisible({ timeout: 10000 });

    // Verify page heading
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();

    // Verify user info is displayed
    await expect(page.getByTestId('user-display-name')).toHaveText(testDisplayName);
    await expect(page.getByTestId('user-email')).toHaveText(testEmail);

    // Verify key sections are visible
    await expect(page.getByTestId('account-section')).toBeVisible();
    await expect(page.getByTestId('preferences-section')).toBeVisible();
    await expect(page.getByTestId('pinned-lists-section')).toBeVisible();
    await expect(page.getByTestId('keyboard-shortcuts-section')).toBeVisible();
  });

  test('should toggle smart parsing preference', async ({ page }) => {
    // Wait for preferences to load
    const toggle = page.getByTestId('smart-parsing-toggle');
    await expect(toggle).toBeVisible({ timeout: 10000 });

    // Smart parsing is on by default
    await expect(toggle).toHaveAttribute('aria-checked', 'true');

    // Toggle it off
    await toggle.click();

    // Wait for save success
    await expect(page.getByTestId('save-success')).toBeVisible({ timeout: 5000 });
    await expect(toggle).toHaveAttribute('aria-checked', 'false');

    // Reload the page and verify it persists
    await page.reload();
    await page.getByRole('link', { name: 'Profile' }).first().click();
    await expect(page.getByTestId('smart-parsing-toggle')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('smart-parsing-toggle')).toHaveAttribute('aria-checked', 'false');
  });
});

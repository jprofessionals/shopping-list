import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Connection Status', () => {
  test.beforeEach(async ({ page }) => {
    // Create a unique test user
    const testUserEmail = `conn-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testUserEmail,
        password: 'password123',
        displayName: 'Connection Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), token);
    await page.reload();

    // Wait for the app to authenticate
    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
      timeout: 10000,
    });
  });

  test('should display connection status indicator in header', async ({ page }) => {
    // The connection status indicator should be present
    const connectionStatus = page.getByTestId('connection-status');
    await expect(connectionStatus).toBeVisible();

    // Should have an aria-label for accessibility
    await expect(connectionStatus).toHaveAttribute('aria-label', /.+/);

    // Should show a status dot
    await expect(page.getByTestId('connection-dot')).toBeVisible();
  });

  test('should show green connected dot when WebSocket connects', async ({ page }) => {
    // Wait for WebSocket to connect (green dot)
    const dot = page.getByTestId('connection-dot');
    await expect(dot).toHaveClass(/bg-green-500/, { timeout: 15000 });

    // When connected, no status text label should be shown
    await expect(page.getByTestId('connection-text')).not.toBeVisible();

    // No spinner should be shown when connected
    await expect(page.getByTestId('connection-spinner')).not.toBeVisible();
  });

  test('should maintain connected status across page navigation', async ({ page }) => {
    // Verify connected
    const dot = page.getByTestId('connection-dot');
    await expect(dot).toHaveClass(/bg-green-500/, { timeout: 15000 });

    // Navigate to lists page
    await page.getByRole('link', { name: /^lists$/i }).click();
    await expect(dot).toHaveClass(/bg-green-500/);

    // Navigate to households page
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await expect(dot).toHaveClass(/bg-green-500/);
  });
});

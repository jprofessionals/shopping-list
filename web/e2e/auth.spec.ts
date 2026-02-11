import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Authentication', () => {
  test.beforeEach(async ({ page }) => {
    // Clear any existing auth state
    await page.goto('/');
    await page.evaluate(() => localStorage.clear());
  });

  test.describe('Local Authentication', () => {
    test('should register a new user', async ({ page }) => {
      const uniqueEmail = `test-${Date.now()}@example.com`;

      await page.goto('/');
      await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

      // Click "Create one" link to switch to register mode
      await page.getByRole('button', { name: /create one/i }).click();

      // Should now show "Create your account"
      await expect(page.getByRole('heading', { name: /create your account/i })).toBeVisible();

      // Fill registration form
      await page.getByLabel(/email/i).fill(uniqueEmail);
      await page.getByLabel(/display name/i).fill('Test User');
      await page.getByLabel(/password/i).fill('password123');

      // Submit
      await page.getByRole('button', { name: /create account/i }).click();

      // Should be redirected to main app
      await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
        timeout: 10000,
      });
      await expect(page.getByTestId('welcome-message')).toContainText('Test User');
    });

    test('should login with existing user', async ({ page }) => {
      const uniqueEmail = `login-test-${Date.now()}@example.com`;

      // First register a user via API
      const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
        data: {
          email: uniqueEmail,
          password: 'password123',
          displayName: 'Login Test User',
        },
      });
      expect(registerResponse.ok()).toBeTruthy();

      // Now test login
      await page.goto('/');
      await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();

      await page.getByLabel(/email/i).fill(uniqueEmail);
      await page.getByLabel(/password/i).fill('password123');
      await page.getByRole('button', { name: /sign in/i }).click();

      // Should be redirected to main app
      await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
        timeout: 10000,
      });
      await expect(page.getByTestId('welcome-message')).toContainText('Login Test User');
    });

    test('should show error for invalid credentials', async ({ page }) => {
      await page.goto('/');

      await page.getByLabel(/email/i).fill('nonexistent@example.com');
      await page.getByLabel(/password/i).fill('wrongpassword');
      await page.getByRole('button', { name: /sign in/i }).click();

      // Should show error message (displayed in the error div)
      await expect(page.locator('.bg-red-50')).toBeVisible({ timeout: 5000 });
    });

    test('should logout successfully', async ({ page }) => {
      const uniqueEmail = `logout-test-${Date.now()}@example.com`;

      // Register and login via API
      const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
        data: {
          email: uniqueEmail,
          password: 'password123',
          displayName: 'Logout Test User',
        },
      });
      expect(registerResponse.ok()).toBeTruthy();
      const { token } = await registerResponse.json();

      // Set token and navigate
      await page.goto('/');
      await page.evaluate((t) => localStorage.setItem('token', t), token);
      await page.reload();

      // Verify logged in
      await expect(page.getByTestId('welcome-message')).toContainText('Logout Test User', {
        timeout: 10000,
      });

      // Click logout
      await page.getByRole('button', { name: /sign out/i }).click();

      // Should return to login page
      await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible({
        timeout: 10000,
      });
    });
  });

  test.describe('Google OAuth', () => {
    test('should handle first-time Google OAuth login', async ({ page }) => {
      // For E2E testing of Google OAuth, we verify the button exists and initiates auth
      // The actual OAuth flow requires real Google credentials which we can't test E2E
      await page.goto('/');

      // Check if Google button is visible (indicates Google auth is enabled)
      const googleButton = page.getByRole('button', { name: /google/i });
      const isGoogleEnabled = await googleButton.isVisible({ timeout: 5000 }).catch(() => false);

      if (!isGoogleEnabled) {
        // Google auth is disabled in config - skip this test
        test.skip();
        return;
      }

      // Verify the button text
      await expect(googleButton).toHaveText(/continue with google/i);

      // We can't fully test the OAuth flow without mocking Google's servers,
      // so we just verify the button initiates a redirect to the backend
      // Set up listener for navigation
      const [request] = await Promise.all([
        page.waitForRequest((req) => req.url().includes('/auth/google')),
        googleButton.click(),
      ]);

      // Verify the request was made to our backend's Google auth endpoint
      expect(request.url()).toContain('/auth/google');
    });
  });
});

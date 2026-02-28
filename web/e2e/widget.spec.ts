import { test, expect } from '@playwright/test';
import { execSync } from 'child_process';
import { existsSync } from 'fs';
import { createSharedListViaInternalApi } from './e2e-utils';

function getFrontendBaseUrl(): string {
  const port = process.env.E2E_FRONTEND_PORT || '5173';
  return `http://localhost:${port}`;
}

test.describe('Shopping List Widget', () => {
  test.beforeAll(() => {
    if (!existsSync('dist-widget/widget.js')) {
      execSync('npm run build:widget', { stdio: 'inherit' });
    }
  });

  test('renders list title and items from shared token', async ({ page }) => {
    // Create a list with items via internal API (avoids rate limiting)
    const { shareToken } = await createSharedListViaInternalApi(page.request, {
      items: [
        { name: 'Apples', quantity: 3, unit: 'pcs' },
        { name: 'Milk', quantity: 1, unit: 'L' },
      ],
    });

    const baseUrl = getFrontendBaseUrl();

    // Navigate to frontend to get same-origin for API proxy
    await page.goto('/');

    // Replace page with widget, keeping the origin intact
    await page.evaluate(
      ({ token, apiUrl }) => {
        document.documentElement.innerHTML = `
          <head></head>
          <body>
            <shopping-list-widget token="${token}" api-url="${apiUrl}"></shopping-list-widget>
          </body>`;
      },
      { token: shareToken, apiUrl: baseUrl }
    );

    // Inject the built widget script (upgrades the custom element)
    await page.addScriptTag({ path: 'dist-widget/widget.js' });

    // Wait for widget to load data and render
    await expect(page.getByText('Shared List')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Apples')).toBeVisible();
    await expect(page.getByText('Milk')).toBeVisible();
  });

  test('can check and uncheck an item', async ({ page }) => {
    const { shareToken } = await createSharedListViaInternalApi(page.request, {
      items: [{ name: 'Bananas', quantity: 2, unit: 'pcs' }],
    });

    const baseUrl = getFrontendBaseUrl();
    await page.goto('/');
    await page.evaluate(
      ({ token, apiUrl }) => {
        document.documentElement.innerHTML = `
          <head></head>
          <body>
            <shopping-list-widget token="${token}" api-url="${apiUrl}"></shopping-list-widget>
          </body>`;
      },
      { token: shareToken, apiUrl: baseUrl }
    );
    await page.addScriptTag({ path: 'dist-widget/widget.js' });

    // Wait for item to render
    const item = page.locator('li').filter({ hasText: 'Bananas' });
    await expect(item).toBeVisible({ timeout: 10000 });

    // Item should start unchecked
    await expect(item.locator('input[type="checkbox"]')).not.toBeChecked();

    // Click to check
    await item.click();
    await expect(item.locator('input[type="checkbox"]')).toBeChecked({ timeout: 5000 });

    // Click again to uncheck
    await item.click();
    await expect(item.locator('input[type="checkbox"]')).not.toBeChecked({ timeout: 5000 });
  });

  test('can add a new item', async ({ page }) => {
    const { shareToken } = await createSharedListViaInternalApi(page.request, {
      items: [{ name: 'Eggs', quantity: 12 }],
    });

    const baseUrl = getFrontendBaseUrl();
    await page.goto('/');
    await page.evaluate(
      ({ token, apiUrl }) => {
        document.documentElement.innerHTML = `
          <head></head>
          <body>
            <shopping-list-widget token="${token}" api-url="${apiUrl}"></shopping-list-widget>
          </body>`;
      },
      { token: shareToken, apiUrl: baseUrl }
    );
    await page.addScriptTag({ path: 'dist-widget/widget.js' });

    // Wait for widget to render
    await expect(page.getByText('Eggs')).toBeVisible({ timeout: 10000 });

    // Add a new item
    await page.getByPlaceholder('Add item...').fill('Bread');
    await page.getByRole('button', { name: 'Add' }).click();

    // Verify new item appears
    await expect(page.getByText('Bread')).toBeVisible({ timeout: 5000 });
  });

  test('shows error for invalid token', async ({ page }) => {
    const baseUrl = getFrontendBaseUrl();
    await page.goto('/');
    await page.evaluate(
      ({ token, apiUrl }) => {
        document.documentElement.innerHTML = `
          <head></head>
          <body>
            <shopping-list-widget token="${token}" api-url="${apiUrl}"></shopping-list-widget>
          </body>`;
      },
      { token: 'invalid-token-does-not-exist', apiUrl: baseUrl }
    );
    await page.addScriptTag({ path: 'dist-widget/widget.js' });

    await expect(page.getByText('Shopping list not found')).toBeVisible({ timeout: 10000 });
  });
});

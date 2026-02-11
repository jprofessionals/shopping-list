import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('List Sharing', () => {
  let authToken: string;
  let listId: string;

  test.beforeEach(async ({ page }) => {
    // Create a unique test user
    const testUserEmail = `share-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testUserEmail,
        password: 'password123',
        displayName: 'Share Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();
    authToken = token;

    // Create a list via API
    const listResponse = await page.request.post(`${API_URL}/lists`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        name: `Shareable List ${Date.now()}`,
        isPersonal: true,
      },
    });
    const list = await listResponse.json();
    listId = list.id;

    // Add some items to the list
    await page.request.post(`${API_URL}/lists/${listId}/items`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        name: 'Milk',
        quantity: 1,
        unit: 'L',
      },
    });
    await page.request.post(`${API_URL}/lists/${listId}/items`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        name: 'Bread',
        quantity: 2,
        unit: 'pcs',
      },
    });

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();
  });

  test('should create a link share', async ({ page }) => {
    // Navigate to Shopping Lists tab
    await page.getByRole('link', { name: /^lists$/i }).click();

    // Wait for lists to load and click on the shareable list
    await expect(page.getByText(/shareable list/i)).toBeVisible({ timeout: 10000 });
    await page.getByText(/shareable list/i).click();

    // Wait for detail view and click share button
    await expect(page.getByRole('button', { name: 'Share' })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: 'Share' }).click();

    // Share modal should open
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    // Click on "Share Link" tab
    await dialog.getByRole('button', { name: 'Share Link' }).click();

    // Click create link button
    await dialog.getByRole('button', { name: 'Create Link' }).click();

    // Should show a share link containing "/shared/"
    await expect(dialog.locator('input[readonly]')).toHaveValue(/\/shared\//);
  });

  test('should access a list via shared link', async ({ page, context }) => {
    // Create a link share via API
    const shareResponse = await page.request.post(`${API_URL}/lists/${listId}/shares`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        type: 'LINK',
        permission: 'READ',
        expirationDays: 7,
      },
    });
    const share = await shareResponse.json();
    const shareToken = share.linkToken;

    // Open a new page (simulating a different user/guest)
    const guestPage = await context.newPage();

    // Navigate to the shared link
    await guestPage.goto(`/shared/${shareToken}`);

    // Should see the list name (in h2) and items with increased timeout
    await expect(guestPage.getByRole('heading', { level: 2 })).toContainText(/shareable list/i, {
      timeout: 10000,
    });
    await expect(guestPage.getByText('Milk')).toBeVisible({ timeout: 5000 });
    await expect(guestPage.getByText('Bread')).toBeVisible({ timeout: 5000 });

    await guestPage.close();
  });

  test('should show error for expired link', async ({ page, context }) => {
    // Create an already-expired link share via API (negative days)
    const shareResponse = await page.request.post(`${API_URL}/lists/${listId}/shares`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        type: 'LINK',
        permission: 'READ',
        expirationDays: -1, // Already expired
      },
    });
    const share = await shareResponse.json();
    const shareToken = share.linkToken;

    // Open a new page
    const guestPage = await context.newPage();

    // Try to access the expired link
    await guestPage.goto(`/shared/${shareToken}`);

    // Should show expired message (component shows "Link Expired")
    await expect(guestPage.getByText(/link expired/i)).toBeVisible();

    await guestPage.close();
  });

  test('should show error for invalid share token', async ({ context }) => {
    // Open a new page
    const guestPage = await context.newPage();

    // Try to access an invalid token
    await guestPage.goto('/shared/invalid-token-12345');

    // Should show not found error
    await expect(guestPage.getByText(/not found|invalid|error/i)).toBeVisible();

    await guestPage.close();
  });
});

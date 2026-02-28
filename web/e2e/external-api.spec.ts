import { test, expect } from '@playwright/test';
import { getApiUrl, createSharedListViaInternalApi } from './e2e-utils';

const API_URL = getApiUrl();

test.describe('External API - List Creation', () => {
  test('POST /api/external/lists with title only returns shareToken, listId, widgetUrl', async ({
    page,
  }) => {
    const response = await page.request.post(`${API_URL}/external/lists`, {
      data: { title: `External List ${Date.now()}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.shareToken).toBeTruthy();
    expect(body.listId).toBeTruthy();
    expect(body.widgetUrl).toBeTruthy();
  });

  test('POST /api/external/lists with title, email, and items populates items', async ({
    page,
  }) => {
    const ts = Date.now();
    const response = await page.request.post(`${API_URL}/external/lists`, {
      data: {
        title: `Party Supplies ${ts}`,
        email: `party-${ts}@example.com`,
        items: [
          { name: 'Chips', quantity: 3, unit: 'bags' },
          { name: 'Salsa', quantity: 1 },
        ],
      },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.shareToken).toBeTruthy();

    // Verify items via shared access
    const sharedResponse = await page.request.get(`${API_URL}/shared/${body.shareToken}`);
    expect(sharedResponse.ok()).toBeTruthy();
    const sharedBody = await sharedResponse.json();
    expect(sharedBody.items).toHaveLength(2);
    expect(sharedBody.items[0].name).toBe('Chips');
    expect(sharedBody.items[0].quantity).toBe(3);
    expect(sharedBody.items[1].name).toBe('Salsa');
  });

  test('POST /api/external/lists with missing title returns 400', async ({ page }) => {
    const response = await page.request.post(`${API_URL}/external/lists`, {
      data: {},
    });

    expect(response.status()).toBe(400);
  });
});

test.describe('External API - Shared Access for Externally-Created Lists', () => {
  let shareToken: string;

  test.beforeEach(async ({ page }) => {
    // Create list via internal API to avoid rate limiting
    const result = await createSharedListViaInternalApi(page.request, {
      items: [
        { name: 'Milk', quantity: 1, unit: 'L' },
        { name: 'Bread', quantity: 2, unit: 'pcs' },
      ],
    });
    shareToken = result.shareToken;
  });

  test('GET /api/shared/{token} returns list name and items', async ({ page }) => {
    const response = await page.request.get(`${API_URL}/shared/${shareToken}`);

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.name).toContain('Shared List');
    expect(body.items).toHaveLength(2);
    expect(body.items[0].name).toBe('Milk');
    expect(body.items[1].name).toBe('Bread');
  });

  test('POST /api/shared/{token}/items adds an item (WRITE permission)', async ({ page }) => {
    const response = await page.request.post(`${API_URL}/shared/${shareToken}/items`, {
      data: { name: 'Butter', quantity: 1 },
    });

    expect(response.status()).toBe(201);
    const item = await response.json();
    expect(item.name).toBe('Butter');
    expect(item.id).toBeTruthy();

    // Verify item appears in list
    const listResponse = await page.request.get(`${API_URL}/shared/${shareToken}`);
    const listBody = await listResponse.json();
    expect(listBody.items).toHaveLength(3);
  });

  test('POST /api/shared/{token}/items/{id}/check toggles check', async ({ page }) => {
    // Get items to find an ID
    const listResponse = await page.request.get(`${API_URL}/shared/${shareToken}`);
    const listBody = await listResponse.json();
    const itemId = listBody.items[0].id;
    expect(listBody.items[0].isChecked).toBe(false);

    // Toggle check
    const checkResponse = await page.request.post(
      `${API_URL}/shared/${shareToken}/items/${itemId}/check`
    );
    expect(checkResponse.ok()).toBeTruthy();
    const checkedItem = await checkResponse.json();
    expect(checkedItem.isChecked).toBe(true);

    // Toggle back
    const uncheckResponse = await page.request.post(
      `${API_URL}/shared/${shareToken}/items/${itemId}/check`
    );
    expect(uncheckResponse.ok()).toBeTruthy();
    const uncheckedItem = await uncheckResponse.json();
    expect(uncheckedItem.isChecked).toBe(false);
  });

  test('GET /api/shared/{invalidToken} returns 404', async ({ page }) => {
    const response = await page.request.get(`${API_URL}/shared/nonexistent-token-${Date.now()}`);

    expect(response.status()).toBe(404);
  });
});

test.describe('External API - Email Claim on Registration', () => {
  test('externally-created list is claimed when user registers with matching email', async ({
    page,
  }) => {
    const ts = Date.now();
    const email = `claim-test-${ts}@example.com`;

    // 1. Create external list with email
    const createResponse = await page.request.post(`${API_URL}/external/lists`, {
      data: {
        title: `Claimable List ${ts}`,
        email,
        items: [{ name: 'Test Item', quantity: 1 }],
      },
    });
    expect(createResponse.status()).toBe(201);

    // 2. Register a new account with the same email
    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email,
        password: 'password123',
        displayName: 'Claim Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token: authToken } = await registerResponse.json();

    // 3. Fetch user's lists - the externally-created list should appear as owned
    const listsResponse = await page.request.get(`${API_URL}/lists`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    expect(listsResponse.ok()).toBeTruthy();
    const lists = await listsResponse.json();
    const claimedList = lists.find((l: { name: string }) => l.name === `Claimable List ${ts}`);
    expect(claimedList).toBeTruthy();
  });
});

test.describe('External API - Rate Limiting', () => {
  test('rate limiter returns 429 after exceeding limit', async ({ page }) => {
    test.setTimeout(120_000);

    // Wait for any prior rate limit window to expire
    await page.waitForTimeout(61_000);

    const results: number[] = [];

    for (let i = 0; i < 12; i++) {
      const response = await page.request.post(`${API_URL}/external/lists`, {
        data: { title: `Rate Limit Test ${Date.now()}-${i}` },
      });
      results.push(response.status());
    }

    // First 10 should succeed
    const successes = results.slice(0, 10);
    for (const status of successes) {
      expect(status).toBe(201);
    }

    // Requests beyond the limit should be rate limited
    expect(results.slice(10)).toContain(429);
  });
});

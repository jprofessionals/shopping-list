import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Recurring Items', () => {
  let authToken: string;
  let householdId: string;

  test.beforeEach(async ({ page }) => {
    const testUserEmail = `recurring-test-${Date.now()}@example.com`;

    // Register user
    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testUserEmail,
        password: 'password123',
        displayName: 'Recurring Test User',
      },
    });
    const { token } = await registerResponse.json();
    authToken = token;

    // Create household
    const householdResponse = await page.request.post(`${API_URL}/households`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: `Test Household ${Date.now()}` },
    });
    const household = await householdResponse.json();
    householdId = household.id;

    // Set token and navigate to household detail
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();
    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(household.name).click();

    // Wait for household detail to load
    await expect(page.getByRole('heading', { name: /members/i })).toBeVisible();
  });

  test('should display recurring items section', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /recurring items/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /add item/i })).toBeVisible();
  });

  test('should create a recurring item', async ({ page }) => {
    await page.getByRole('button', { name: /add item/i }).click();

    // Fill form
    await page.getByLabel(/name/i).fill('Milk');
    await page.getByLabel(/qty/i).fill('2');
    await page.getByLabel(/unit/i).fill('liters');
    await page.getByLabel(/frequency/i).selectOption('WEEKLY');

    await page.getByRole('button', { name: /create/i }).click();

    // Item should appear in list
    await expect(page.getByText('Milk')).toBeVisible();
    await expect(page.getByText(/weekly/i)).toBeVisible();
  });

  test('should create recurring item via API and display it', async ({ page }) => {
    // Create via API
    await page.request.post(`${API_URL}/households/${householdId}/recurring-items`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Bread',
        quantity: 1,
        frequency: 'DAILY',
      },
    });

    // Reload to see the item
    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(/test household/i).click();
    await expect(page.getByRole('heading', { name: /members/i })).toBeVisible();

    await expect(page.getByText('Bread')).toBeVisible();
  });

  test('should pause and resume a recurring item', async ({ page }) => {
    // Create item via API
    await page.request.post(`${API_URL}/households/${householdId}/recurring-items`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Eggs',
        quantity: 12,
        frequency: 'WEEKLY',
      },
    });

    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(/test household/i).click();
    await expect(page.getByText('Eggs')).toBeVisible();

    // Pause the item
    await page.getByTitle(/pause/i).click();
    await expect(page.getByRole('dialog', { name: /pause/i })).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: /^pause$/i }).click();

    // Should show paused badge
    await expect(page.getByText(/paused/i)).toBeVisible();

    // Resume the item
    await page.getByTitle(/resume/i).click();

    // Paused badge should disappear
    await expect(page.getByText(/paused/i)).not.toBeVisible({ timeout: 5000 });
  });

  test('should delete a recurring item', async ({ page }) => {
    // Create item via API
    await page.request.post(`${API_URL}/households/${householdId}/recurring-items`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Butter',
        quantity: 1,
        frequency: 'MONTHLY',
      },
    });

    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(/test household/i).click();
    await expect(page.getByText('Butter')).toBeVisible();

    // Delete - click delete button to open confirmation dialog
    await page.getByTitle(/delete/i).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: /delete/i }).click();

    // Item should be gone
    await expect(page.getByText('Butter')).not.toBeVisible({ timeout: 5000 });
  });

  test('recurring items API CRUD works correctly', async ({ page }) => {
    // Create
    const createResponse = await page.request.post(
      `${API_URL}/households/${householdId}/recurring-items`,
      {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          name: 'API Test Item',
          quantity: 3,
          unit: 'kg',
          frequency: 'BIWEEKLY',
        },
      }
    );
    expect(createResponse.status()).toBe(201);
    const created = await createResponse.json();
    expect(created.name).toBe('API Test Item');
    expect(created.frequency).toBe('BIWEEKLY');
    expect(created.isActive).toBe(true);

    // List
    const listResponse = await page.request.get(
      `${API_URL}/households/${householdId}/recurring-items`,
      {
        headers: { Authorization: `Bearer ${authToken}` },
      }
    );
    expect(listResponse.status()).toBe(200);
    const items = await listResponse.json();
    expect(items.length).toBeGreaterThanOrEqual(1);

    // Update
    const updateResponse = await page.request.patch(
      `${API_URL}/households/${householdId}/recurring-items/${created.id}`,
      {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          name: 'Updated API Item',
          quantity: 5,
          unit: 'kg',
          frequency: 'MONTHLY',
        },
      }
    );
    expect(updateResponse.status()).toBe(200);
    const updated = await updateResponse.json();
    expect(updated.name).toBe('Updated API Item');
    expect(updated.frequency).toBe('MONTHLY');

    // Pause
    const pauseResponse = await page.request.post(
      `${API_URL}/households/${householdId}/recurring-items/${created.id}/pause`,
      {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: { until: '2026-03-01' },
      }
    );
    expect(pauseResponse.status()).toBe(200);
    const paused = await pauseResponse.json();
    expect(paused.isActive).toBe(false);
    expect(paused.pausedUntil).toBe('2026-03-01');

    // Resume
    const resumeResponse = await page.request.post(
      `${API_URL}/households/${householdId}/recurring-items/${created.id}/resume`,
      {
        headers: { Authorization: `Bearer ${authToken}` },
      }
    );
    expect(resumeResponse.status()).toBe(200);
    const resumed = await resumeResponse.json();
    expect(resumed.isActive).toBe(true);
    expect(resumed.pausedUntil).toBeNull();

    // Delete
    const deleteResponse = await page.request.delete(
      `${API_URL}/households/${householdId}/recurring-items/${created.id}`,
      {
        headers: { Authorization: `Bearer ${authToken}` },
      }
    );
    expect(deleteResponse.status()).toBe(204);
  });

  test('should edit a recurring item via UI', async ({ page }) => {
    // Create item via API
    await page.request.post(`${API_URL}/households/${householdId}/recurring-items`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Cheese',
        quantity: 1,
        unit: 'kg',
        frequency: 'WEEKLY',
      },
    });

    await page.reload();
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(/test household/i).click();
    await expect(page.getByText('Cheese')).toBeVisible();

    // Click edit button
    await page.getByTitle(/edit/i).click();

    // Form should be populated
    const nameInput = page.getByLabel(/name/i);
    await expect(nameInput).toHaveValue('Cheese');

    // Update the name
    await nameInput.fill('Gouda Cheese');
    await page.getByLabel(/frequency/i).selectOption('MONTHLY');
    await page.getByRole('button', { name: /save|update/i }).click();

    // Updated item should appear
    await expect(page.getByText('Gouda Cheese')).toBeVisible();
    await expect(page.getByText(/monthly/i)).toBeVisible();
  });

  test('regular list items have null recurringItemId in response', async ({ page }) => {
    // Create a shopping list
    const listResponse = await page.request.post(`${API_URL}/lists`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Regular Items Test',
        householdId,
        isPersonal: false,
      },
    });
    const list = await listResponse.json();

    // Add a regular list item (not from recurring)
    await page.request.post(`${API_URL}/lists/${list.id}/items`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Bananas',
        quantity: 6,
      },
    });

    // Fetch list detail and verify recurringItemId is null
    const listDetailResponse = await page.request.get(`${API_URL}/lists/${list.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    const listDetail = await listDetailResponse.json();
    const item = listDetail.items.find((i: { name: string }) => i.name === 'Bananas');
    expect(item).toBeDefined();
    expect(item.recurringItemId ?? null).toBeNull();
  });
});

import { test, expect } from '@playwright/test';

const API_URL = 'http://localhost:8080/api';

test.describe('Comments on Lists', () => {
  let listName: string;

  test.beforeEach(async ({ page }) => {
    const testEmail = `comment-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testEmail,
        password: 'password123',
        displayName: 'Comment Test User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token } = await registerResponse.json();

    // Create a list via API
    listName = `Comment List ${Date.now()}`;
    await page.request.post(`${API_URL}/lists`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: listName, isPersonal: true },
    });

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), token);
    await page.reload();

    // Wait for app to load
    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
      timeout: 10000,
    });

    // Navigate to the list
    await page.getByRole('link', { name: 'Lists', exact: true }).click();
    await page.getByText(listName).click({ timeout: 10000 });

    // Wait for the comments section to be ready
    await expect(page.getByRole('heading', { name: 'Comments' })).toBeVisible({ timeout: 10000 });
  });

  test('should display empty comments section on a list', async ({ page }) => {
    // Verify empty state message
    await expect(page.getByText('No comments yet')).toBeVisible();
  });

  test('should add a comment to a list', async ({ page }) => {
    // Type a comment and send
    const commentText = `Test comment ${Date.now()}`;
    await page.getByPlaceholder('Add a comment...').fill(commentText);
    await page.getByRole('button', { name: 'Send' }).click();

    // Verify comment text appears (may briefly show both optimistic and server copy)
    await expect(page.getByText(commentText).first()).toBeVisible({ timeout: 10000 });
  });

  test('should edit own comment on a list', async ({ page }) => {
    // Add a comment via UI first
    const originalText = `Original comment ${Date.now()}`;
    await page.getByPlaceholder('Add a comment...').fill(originalText);
    await page.getByRole('button', { name: 'Send' }).click();

    // Wait for the comment to appear
    await expect(page.getByText(originalText)).toBeVisible({ timeout: 10000 });

    // Click Edit button
    await page.getByRole('button', { name: 'Edit' }).click();

    // Modify the text in the edit input
    const updatedText = `Edited comment ${Date.now()}`;
    const saveButton = page.getByRole('button', { name: 'Save' });
    await expect(saveButton).toBeVisible({ timeout: 5000 });
    const editInput = saveButton.locator('..').locator('input[type="text"]');
    await editInput.fill(updatedText);

    // Click Save and wait for the API call to complete
    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/comments/') && resp.request().method() === 'PATCH'
      ),
      saveButton.click(),
    ]);

    // Verify updated text and edited label
    await expect(page.getByText(updatedText)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('(edited)')).toBeVisible();
  });

  test('should delete own comment on a list', async ({ page }) => {
    // Add a comment via UI first
    const commentText = `Delete me comment ${Date.now()}`;
    await page.getByPlaceholder('Add a comment...').fill(commentText);
    await page.getByRole('button', { name: 'Send' }).click();

    // Wait for the comment to appear
    await expect(page.getByText(commentText)).toBeVisible({ timeout: 10000 });

    // Handle the confirm dialog and click Delete
    page.on('dialog', (dialog) => dialog.accept());

    // Click Delete on the comment (scoped to the comment element) and wait for the API call
    const commentEl = page.locator('[data-testid^="comment-"]', { hasText: commentText });
    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/comments/') && resp.request().method() === 'DELETE'
      ),
      commentEl.getByRole('button', { name: 'Delete' }).click(),
    ]);

    // Verify comment is removed
    await expect(page.getByText(commentText)).not.toBeVisible({ timeout: 10000 });
  });
});

test.describe('Comments on Households', () => {
  test('should display comments on a household', async ({ page }) => {
    const testEmail = `hh-comment-test-${Date.now()}@example.com`;

    const registerResponse = await page.request.post(`${API_URL}/auth/register`, {
      data: {
        email: testEmail,
        password: 'password123',
        displayName: 'HH Comment User',
      },
    });
    expect(registerResponse.ok(), `Register failed: ${registerResponse.status()}`).toBeTruthy();
    const { token: authToken } = await registerResponse.json();

    // Create a household via API
    const householdName = `Comment Household ${Date.now()}`;
    await page.request.post(`${API_URL}/households`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { name: householdName },
    });

    // Set token and navigate
    await page.goto('/');
    await page.evaluate((t) => localStorage.setItem('token', t), authToken);
    await page.reload();

    await expect(page.getByRole('link', { name: /shopping list/i })).toBeVisible({
      timeout: 10000,
    });

    // Navigate to the household
    await page.getByRole('link', { name: 'Households', exact: true }).click();
    await page.getByText(householdName).click({ timeout: 10000 });

    // Verify comments section is visible
    await expect(page.getByRole('heading', { name: 'Comments' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('No comments yet')).toBeVisible();

    // Add a comment
    const commentText = `Household comment ${Date.now()}`;
    await page.getByPlaceholder('Add a comment...').fill(commentText);
    await page.getByRole('button', { name: 'Send' }).click();

    // Verify comment appears
    await expect(page.getByText(commentText)).toBeVisible({ timeout: 10000 });
  });
});

import { test, expect, APIRequestContext } from '@playwright/test';
import WebSocket from 'ws';
import { getApiUrl, getApi2Url, getWsUrl } from './e2e-utils';

const BACKEND_1 = getApiUrl();
const BACKEND_2 = getApi2Url();

async function registerUser(
  request: APIRequestContext,
  apiUrl: string,
  email: string,
  displayName: string
) {
  const response = await request.post(`${apiUrl}/auth/register`, {
    data: {
      email,
      password: 'password123',
      displayName,
    },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test.describe('Cross-pod coordination via Valkey', () => {
  test('cross-pod WebSocket broadcasting: event on backend-1 reaches WebSocket on backend-2', async ({
    request,
  }) => {
    const timestamp = Date.now();

    // Register user A via backend-1
    const userA = await registerUser(
      request,
      BACKEND_1,
      `cross-ws-a-${timestamp}@example.com`,
      'User A'
    );

    // Register user B via backend-2
    const userB = await registerUser(
      request,
      BACKEND_2,
      `cross-ws-b-${timestamp}@example.com`,
      'User B'
    );

    // Create household via backend-1 (user A)
    const householdRes = await request.post(`${BACKEND_1}/households`, {
      headers: { Authorization: `Bearer ${userA.token}` },
      data: { name: `Cross-pod Household ${timestamp}` },
    });
    expect(householdRes.ok()).toBeTruthy();
    const household = await householdRes.json();

    // Add user B to household via backend-1
    const addMemberRes = await request.post(`${BACKEND_1}/households/${household.id}/members`, {
      headers: { Authorization: `Bearer ${userA.token}` },
      data: { email: `cross-ws-b-${timestamp}@example.com`, role: 'MEMBER' },
    });
    expect(addMemberRes.ok()).toBeTruthy();

    // Create shopping list in the household via backend-1
    const listRes = await request.post(`${BACKEND_1}/lists`, {
      headers: { Authorization: `Bearer ${userA.token}` },
      data: {
        name: `Cross-pod List ${timestamp}`,
        householdId: household.id,
        isPersonal: false,
      },
    });
    expect(listRes.ok()).toBeTruthy();
    const list = await listRes.json();

    // Connect WebSocket for user B to backend-2
    const wsB = new WebSocket(`${getWsUrl(2)}?token=${userB.token}`);

    const itemAddedPromise = new Promise<Record<string, unknown>>((resolve, reject) => {
      const timeout = setTimeout(
        () => reject(new Error('Timed out waiting for item:added event')),
        10000
      );
      wsB.on('message', (data: WebSocket.Data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'item:added') {
          clearTimeout(timeout);
          resolve(event);
        }
      });
    });

    // Wait for WebSocket to connect and subscriptions to be set up
    await new Promise<void>((resolve, reject) => {
      wsB.on('open', () => resolve());
      wsB.on('error', reject);
    });

    // Wait a moment for auto-subscription to complete
    await new Promise((resolve) => setTimeout(resolve, 1000));

    // User A adds an item via REST to backend-1
    const addItemRes = await request.post(`${BACKEND_1}/lists/${list.id}/items`, {
      headers: { Authorization: `Bearer ${userA.token}` },
      data: { name: 'Cross-pod test item', quantity: 1 },
    });
    expect(addItemRes.ok()).toBeTruthy();

    // Assert user B receives item:added event on backend-2's WebSocket
    const event = await itemAddedPromise;
    expect(event.type).toBe('item:added');
    expect(event.listId).toBe(list.id);
    expect((event.item as Record<string, unknown>).name).toBe('Cross-pod test item');

    wsB.close();
  });

  test('cross-pod token revocation: logout on backend-1 blocks access on backend-2', async ({
    request,
  }) => {
    const timestamp = Date.now();

    // Register user via backend-1
    const user = await registerUser(
      request,
      BACKEND_1,
      `cross-revoke-${timestamp}@example.com`,
      'Revoke Test User'
    );

    // Verify token works on backend-2
    const meRes = await request.get(`${BACKEND_2}/auth/me`, {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(meRes.ok()).toBeTruthy();

    // Logout via backend-1 (blacklists token in Valkey)
    const logoutRes = await request.post(`${BACKEND_1}/auth/logout`, {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(logoutRes.ok()).toBeTruthy();

    // Call /auth/me with same token against backend-2
    const meAfterLogout = await request.get(`${BACKEND_2}/auth/me`, {
      headers: { Authorization: `Bearer ${user.token}` },
    });
    expect(meAfterLogout.status()).toBe(401);
  });
});

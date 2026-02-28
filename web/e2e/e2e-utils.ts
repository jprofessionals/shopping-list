export function getApiUrl(): string {
  const port = process.env.E2E_BACKEND_PORT || '8080';
  return `http://localhost:${port}/api`;
}

export function getApi2Url(): string {
  const port = process.env.E2E_BACKEND_2_PORT || '8081';
  return `http://localhost:${port}/api`;
}

export function getWsUrl(backend: 1 | 2 = 1): string {
  const port =
    backend === 1
      ? process.env.E2E_BACKEND_PORT || '8080'
      : process.env.E2E_BACKEND_2_PORT || '8081';
  return `ws://localhost:${port}/api/ws`;
}

/**
 * Creates a shared list via the internal authenticated API (register + create list + share).
 * Use this instead of the external API to avoid rate limiting in tests.
 */
export async function createSharedListViaInternalApi(
  request: {
    post: (url: string, opts?: object) => Promise<{ json: () => Promise<Record<string, string>> }>;
  },
  options: { items?: { name: string; quantity: number; unit?: string }[] } = {}
) {
  const apiUrl = getApiUrl();
  const ts = Date.now();

  // Register a temporary user
  const regRes = await request.post(`${apiUrl}/auth/register`, {
    data: {
      email: `helper-${ts}@example.com`,
      password: 'password123',
      displayName: 'Helper User',
    },
  });
  const { token: authToken } = await regRes.json();

  // Create a list
  const listRes = await request.post(`${apiUrl}/lists`, {
    headers: { Authorization: `Bearer ${authToken}` },
    data: { name: `Shared List ${ts}`, isPersonal: true },
  });
  const list = await listRes.json();

  // Add items if requested
  for (const item of options.items ?? []) {
    await request.post(`${apiUrl}/lists/${list.id}/items`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: item,
    });
  }

  // Create a WRITE share
  const shareRes = await request.post(`${apiUrl}/lists/${list.id}/shares`, {
    headers: { Authorization: `Bearer ${authToken}` },
    data: { type: 'LINK', permission: 'WRITE', expirationHours: 168 },
  });
  const share = await shareRes.json();

  return { shareToken: share.linkToken, listId: list.id, authToken };
}

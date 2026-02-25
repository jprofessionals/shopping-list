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

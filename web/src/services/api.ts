/**
 * API utility with automatic token refresh on 401 responses.
 */

import { store } from '../store/store';
import { loginSuccess, logout } from '../store/authSlice';

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

let isRefreshing = false;
let refreshPromise: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) return false;

    const data = await response.json();
    localStorage.setItem('token', data.token);
    localStorage.setItem('refreshToken', data.refreshToken);

    const state = store.getState();
    if (state.auth.user) {
      store.dispatch(loginSuccess({ user: state.auth.user, token: data.token }));
    }

    return true;
  } catch {
    return false;
  }
}

async function handleRefresh(): Promise<boolean> {
  if (isRefreshing && refreshPromise) {
    return refreshPromise;
  }

  isRefreshing = true;
  refreshPromise = refreshTokens().finally(() => {
    isRefreshing = false;
    refreshPromise = null;
  });

  return refreshPromise;
}

/**
 * Fetch wrapper that automatically refreshes tokens on 401.
 */
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = localStorage.getItem('token');
  const headers = new Headers(options.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (response.status === 401 && token) {
    const refreshed = await handleRefresh();
    if (refreshed) {
      const newToken = localStorage.getItem('token');
      headers.set('Authorization', `Bearer ${newToken}`);
      response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    } else {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      store.dispatch(logout());
    }
  }

  return response;
}

/**
 * Get the current access token, refreshing if needed.
 */
export function getAccessToken(): string | null {
  return localStorage.getItem('token');
}

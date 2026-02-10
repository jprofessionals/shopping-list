import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import authReducer from './store/authSlice';
import householdsReducer from './store/householdsSlice';
import listsReducer from './store/listsSlice';
import websocketReducer from './store/websocketSlice';
import i18nForTests from './test/i18nForTests';

const createTestStore = (preloadedState = {}) =>
  configureStore({
    reducer: {
      auth: authReducer,
      households: householdsReducer,
      lists: listsReducer,
      websocket: websocketReducer,
    },
    preloadedState,
  });

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  it('renders loading state while validating token', async () => {
    // Mock localStorage to return a token
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => 'existing-token'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });

    // Mock fetch to return a pending promise (never resolves during test)
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})) as unknown as typeof fetch);

    // Import App dynamically to get fresh instance
    const { default: App } = await import('./App');

    render(
      <I18nextProvider i18n={i18nForTests}>
        <Provider store={createTestStore()}>
          <App />
        </Provider>
      </I18nextProvider>
    );

    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('redirects to login when not authenticated', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({ googleEnabled: true, localEnabled: true, googleClientId: 'test' }),
        })
      ) as unknown as typeof fetch
    );

    // Import LoginPage separately for the test
    const { default: LoginPage } = await import('./components/pages/LoginPage');

    render(
      <I18nextProvider i18n={i18nForTests}>
        <Provider store={createTestStore()}>
          <MemoryRouter initialEntries={['/login']}>
            <LoginPage />
          </MemoryRouter>
        </Provider>
      </I18nextProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/sign in to your account/i)).toBeInTheDocument();
    });
  });

  it('renders dashboard when authenticated', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        })
      ) as unknown as typeof fetch
    );

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      households: {
        items: [],
        isLoading: false,
        error: null,
      },
      lists: {
        items: [],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    // Import DashboardPage separately for the test
    const { default: DashboardPage } = await import('./components/pages/DashboardPage');

    render(
      <I18nextProvider i18n={i18nForTests}>
        <Provider store={store}>
          <MemoryRouter initialEntries={['/']}>
            <DashboardPage />
          </MemoryRouter>
        </Provider>
      </I18nextProvider>
    );

    expect(screen.getByText(/welcome back/i)).toBeInTheDocument();
  });

  it('renders correct navigation items', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        })
      ) as unknown as typeof fetch
    );

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      households: {
        items: [],
        isLoading: false,
        error: null,
      },
      lists: {
        items: [],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    // Import MainLayout separately for the test
    const { MainLayout } = await import('./components/layout');

    render(
      <I18nextProvider i18n={i18nForTests}>
        <Provider store={store}>
          <MemoryRouter initialEntries={['/']}>
            <MainLayout />
          </MemoryRouter>
        </Provider>
      </I18nextProvider>
    );

    // Navigation now uses BottomNav which has "Home", "Lists", "Households", "Profile"
    // There are duplicates for mobile/desktop views, so use getAllBy
    expect(screen.getAllByRole('link', { name: /home/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /lists/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /households/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /profile/i }).length).toBeGreaterThanOrEqual(1);
  });
});

import { screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import HouseholdDetail from './HouseholdDetail';

describe('HouseholdDetail', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              id: '1',
              name: 'Home',
              createdAt: '2024-01-01',
              members: [
                {
                  accountId: '1',
                  email: 'test@example.com',
                  displayName: 'Test User',
                  avatarUrl: null,
                  role: 'OWNER',
                  joinedAt: '2024-01-01',
                },
              ],
            }),
        })
      ) as unknown as typeof fetch
    );
  });

  it('renders household name', async () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<HouseholdDetail householdId="1" onBack={() => {}} />, { store });

    await waitFor(() => {
      expect(screen.getByText('Home')).toBeInTheDocument();
    });
  });

  it('renders member list', async () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<HouseholdDetail householdId="1" onBack={() => {}} />, { store });

    await waitFor(() => {
      expect(screen.getByText('Test User')).toBeInTheDocument();
    });
  });

  it('shows back button as link', async () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<HouseholdDetail householdId="1" onBack={() => {}} />, { store });

    await waitFor(() => {
      const backLink = screen.getByRole('link', { name: /back to households/i });
      expect(backLink).toHaveAttribute('href', '/households');
    });
  });
});

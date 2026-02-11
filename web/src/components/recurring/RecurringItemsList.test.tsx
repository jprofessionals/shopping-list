import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import RecurringItemsList from './RecurringItemsList';

const mockItems = [
  {
    id: '1',
    name: 'Milk',
    quantity: 2,
    unit: 'liters',
    frequency: 'WEEKLY',
    lastPurchased: '2026-02-10',
    isActive: true,
    pausedUntil: null,
    createdBy: { id: 'u1', displayName: 'Test User' },
  },
  {
    id: '2',
    name: 'Bread',
    quantity: 1,
    unit: null,
    frequency: 'DAILY',
    lastPurchased: null,
    isActive: false,
    pausedUntil: '2026-03-01',
    createdBy: { id: 'u1', displayName: 'Test User' },
  },
];

describe('RecurringItemsList', () => {
  const authState = {
    user: { id: 'u1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
    token: 'test-token',
    isAuthenticated: true,
    isLoading: false,
    error: null,
  };

  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockItems),
        })
      ) as unknown as typeof fetch
    );
  });

  it('renders recurring items list', async () => {
    const store = createTestStore({ auth: authState });
    render(<RecurringItemsList householdId="h1" />, { store });

    await waitFor(() => {
      expect(screen.getByText('Milk')).toBeInTheDocument();
      expect(screen.getByText('Bread')).toBeInTheDocument();
    });
  });

  it('shows paused badge for inactive items', async () => {
    const store = createTestStore({ auth: authState });
    render(<RecurringItemsList householdId="h1" />, { store });

    await waitFor(() => {
      expect(screen.getByText(/paused until/i)).toBeInTheDocument();
    });
  });

  it('shows add item button', async () => {
    const store = createTestStore({ auth: authState });
    render(<RecurringItemsList householdId="h1" />, { store });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /add item/i })).toBeInTheDocument();
    });
  });

  it('shows empty state when no items', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve([]),
        })
      ) as unknown as typeof fetch
    );

    const store = createTestStore({ auth: authState });
    render(<RecurringItemsList householdId="h1" />, { store });

    await waitFor(() => {
      expect(screen.getByText(/no recurring items/i)).toBeInTheDocument();
    });
  });

  it('opens create form when add button clicked', async () => {
    const store = createTestStore({ auth: authState });
    render(<RecurringItemsList householdId="h1" />, { store });

    await waitFor(() => {
      expect(screen.getByText('Milk')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /add item/i }));

    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/frequency/i)).toBeInTheDocument();
  });
});

describe('recurringItemsSlice', () => {
  it('has correct initial state', async () => {
    const store = createTestStore();
    const state = store.getState().recurringItems;
    expect(state.items).toEqual([]);
    expect(state.isLoading).toBe(false);
    expect(state.error).toBeNull();
  });
});

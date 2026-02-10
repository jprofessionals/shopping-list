import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import CreateListModal from './CreateListModal';
import householdsReducer from '../../store/householdsSlice';
import authReducer from '../../store/authSlice';
import listsReducer from '../../store/listsSlice';

const createTestStore = (
  households: Array<{
    id: string;
    name: string;
    createdAt: string;
    memberCount: number;
    isOwner: boolean;
  }> = []
) =>
  configureStore({
    reducer: { households: householdsReducer, auth: authReducer, lists: listsReducer },
    preloadedState: {
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      households: {
        items: households,
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
    },
  });

describe('CreateListModal', () => {
  it('renders form fields', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateListModal onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByLabelText(/list name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/household/i)).toBeInTheDocument();
  });

  it('calls onClose when cancel clicked', () => {
    const onClose = vi.fn();
    render(
      <Provider store={createTestStore()}>
        <CreateListModal onClose={onClose} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows private checkbox only when household is selected', () => {
    const households = [
      { id: 'h1', name: 'Family', createdAt: '2024-01-01', memberCount: 3, isOwner: true },
    ];
    render(
      <Provider store={createTestStore(households)}>
        <CreateListModal onClose={() => {}} />
      </Provider>
    );

    // Initially no private checkbox (default is personal/no household)
    expect(screen.queryByLabelText(/private/i)).not.toBeInTheDocument();

    // Select a household
    fireEvent.change(screen.getByLabelText(/household/i), { target: { value: 'h1' } });

    // Now private checkbox should be visible
    expect(screen.getByLabelText(/private/i)).toBeInTheDocument();
  });

  it('displays households in the dropdown', () => {
    const households = [
      { id: 'h1', name: 'Family', createdAt: '2024-01-01', memberCount: 3, isOwner: true },
      { id: 'h2', name: 'Roommates', createdAt: '2024-01-02', memberCount: 2, isOwner: false },
    ];
    render(
      <Provider store={createTestStore(households)}>
        <CreateListModal onClose={() => {}} />
      </Provider>
    );

    const select = screen.getByLabelText(/household/i) as HTMLSelectElement;
    expect(select).toBeInTheDocument();

    // Check for default option
    expect(screen.getByText(/personal \(no household\)/i)).toBeInTheDocument();

    // Check for household options
    expect(screen.getByRole('option', { name: 'Family' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Roommates' })).toBeInTheDocument();
  });
});

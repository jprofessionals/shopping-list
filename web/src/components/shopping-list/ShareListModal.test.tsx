import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import ShareListModal from './ShareListModal';
import authReducer from '../../store/authSlice';
import listsReducer from '../../store/listsSlice';
import householdsReducer from '../../store/householdsSlice';

const createTestStore = () =>
  configureStore({
    reducer: { auth: authReducer, lists: listsReducer, households: householdsReducer },
    preloadedState: {
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
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
      households: {
        items: [],
        isLoading: false,
        error: null,
      },
    },
  });

describe('ShareListModal', () => {
  it('renders share tabs', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /share with user/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /share link/i })).toBeInTheDocument();
  });

  it('shows expiry preset buttons for link share', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    // Click link tab
    fireEvent.click(screen.getByRole('button', { name: /share link/i }));

    // Expect "Expires in" label text and preset buttons
    expect(screen.getByText(/expires in/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1h' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '24h' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '7d' })).toBeInTheDocument();
  });

  it('shows email input for user share tab by default', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  });

  it('shows permission dropdown', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByLabelText(/permission/i)).toBeInTheDocument();
  });

  it('calls onClose when cancel clicked', () => {
    const onClose = vi.fn();
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={onClose} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows Create Link button on link tab', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /share link/i }));
    expect(screen.getByRole('button', { name: /create link/i })).toBeInTheDocument();
  });

  it('shows Share button on user tab', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByRole('button', { name: /^share$/i })).toBeInTheDocument();
  });

  it('has default expiration of 24h selected', () => {
    render(
      <Provider store={createTestStore()}>
        <ShareListModal listId="list-1" onClose={() => {}} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /share link/i }));
    const selectedButton = screen.getByRole('button', { name: '24h' });
    expect(selectedButton.className).toContain('bg-indigo-600');
  });
});

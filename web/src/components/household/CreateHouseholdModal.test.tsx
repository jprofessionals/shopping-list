import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi } from 'vitest';
import CreateHouseholdModal from './CreateHouseholdModal';
import householdsReducer from '../../store/householdsSlice';
import authReducer from '../../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { households: householdsReducer, auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    },
  });

describe('CreateHouseholdModal', () => {
  it('renders form when open', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={true} onClose={() => {}} />
      </Provider>
    );

    expect(screen.getByLabelText(/household name/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument();
  });

  it('does not render when closed', () => {
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={false} onClose={() => {}} />
      </Provider>
    );

    expect(screen.queryByLabelText(/household name/i)).not.toBeInTheDocument();
  });

  it('calls onClose when cancel clicked', () => {
    const onClose = vi.fn();
    render(
      <Provider store={createTestStore()}>
        <CreateHouseholdModal isOpen={true} onClose={onClose} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});

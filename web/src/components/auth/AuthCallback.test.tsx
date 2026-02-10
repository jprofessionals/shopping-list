import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AuthCallback from './AuthCallback';
import authReducer from '../../store/authSlice';

const createTestStore = () =>
  configureStore({
    reducer: { auth: authReducer },
  });

describe('AuthCallback', () => {
  beforeEach(() => {
    vi.stubGlobal('location', {
      search: '?token=test-token',
      href: '',
    });
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }))
    );
  });

  it('shows loading state while processing token', () => {
    render(
      <Provider store={createTestStore()}>
        <AuthCallback />
      </Provider>
    );

    expect(screen.getByText(/completing sign in/i)).toBeInTheDocument();
  });
});

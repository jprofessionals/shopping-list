import { describe, it, expect } from 'vitest';
import authReducer, {
  loginStart,
  loginSuccess,
  loginFailure,
  logout,
  type AuthState,
} from './authSlice';

describe('authSlice', () => {
  const initialState: AuthState = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
  };

  it('should handle initial state', () => {
    expect(authReducer(undefined, { type: 'unknown' })).toEqual(initialState);
  });

  it('should handle loginStart', () => {
    const state = authReducer(initialState, loginStart());
    expect(state.isLoading).toBe(true);
    expect(state.error).toBe(null);
  });

  it('should handle loginSuccess', () => {
    const user = { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null };
    const state = authReducer(initialState, loginSuccess({ user, token: 'test-token' }));
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).toEqual(user);
    expect(state.token).toBe('test-token');
    expect(state.isLoading).toBe(false);
  });

  it('should handle loginFailure', () => {
    const state = authReducer(initialState, loginFailure('Invalid credentials'));
    expect(state.isLoading).toBe(false);
    expect(state.error).toBe('Invalid credentials');
    expect(state.isAuthenticated).toBe(false);
  });

  it('should handle logout', () => {
    const loggedInState: AuthState = {
      user: { id: '1', email: 'test@example.com', displayName: 'Test', avatarUrl: null },
      token: 'token',
      isAuthenticated: true,
      isLoading: false,
      error: null,
    };
    const state = authReducer(loggedInState, logout());
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBe(null);
    expect(state.token).toBe(null);
  });
});

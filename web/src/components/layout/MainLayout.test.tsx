import { screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import MainLayout from './MainLayout';

describe('MainLayout', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => 'test-token'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  it('renders header with app title as link', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    // Use exact match to distinguish from navigation items
    const titleLink = screen.getByRole('link', { name: 'Shopping List' });
    expect(titleLink).toHaveAttribute('href', '/');
  });

  it('renders bottom navigation with key links', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    // BottomNav provides these links (duplicated for mobile/desktop)
    expect(screen.getAllByRole('link', { name: /home/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /lists/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /households/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('link', { name: /profile/i }).length).toBeGreaterThanOrEqual(1);
  });

  it('displays user name', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'John Doe', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('renders sign out button', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
  });

  it('clears localStorage when signing out', () => {
    const mockRemoveItem = vi.fn();
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => 'test-token'),
      setItem: vi.fn(),
      removeItem: mockRemoveItem,
    });

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    fireEvent.click(screen.getByRole('button', { name: /sign out/i }));

    expect(mockRemoveItem).toHaveBeenCalledWith('token');
  });

  it('renders correct href for navigation links', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    // Get first occurrence of each link (there are duplicates for mobile/desktop)
    const homeLinks = screen.getAllByRole('link', { name: /home/i });
    const listsLinks = screen.getAllByRole('link', { name: /lists/i });
    const householdsLinks = screen.getAllByRole('link', { name: /households/i });
    const profileLinks = screen.getAllByRole('link', { name: /profile/i });

    expect(homeLinks[0]).toHaveAttribute('href', '/');
    expect(listsLinks[0]).toHaveAttribute('href', '/lists');
    expect(householdsLinks[0]).toHaveAttribute('href', '/households');
    expect(profileLinks[0]).toHaveAttribute('href', '/settings');
  });

  it('renders connection status indicator', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      websocket: {
        connectionState: 'connected',
        lastEventTimestamp: null,
      },
    });

    render(<MainLayout />, { store });

    // ConnectionStatus renders a status element with testid
    expect(screen.getByTestId('connection-status')).toBeInTheDocument();
  });

  it('includes BottomNav component', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<MainLayout />, { store });

    // BottomNav includes add button
    expect(screen.getAllByRole('button', { name: /add new item/i }).length).toBeGreaterThanOrEqual(
      1
    );
  });
});

import { screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import DashboardPage from './DashboardPage';
import { type ShoppingList } from '../../store/listsSlice';

// Mock fetch globally
const mockFetch = vi.fn();

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch);
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders welcome message with user name', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'John Doe', avatarUrl: null },
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

    render(<DashboardPage />, { store });

    expect(screen.getByTestId('welcome-message')).toHaveTextContent(/welcome back, john doe/i);
  });

  it('renders fallback user name when displayName is not available', () => {
    const store = createTestStore({
      auth: {
        user: null,
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    expect(screen.getByTestId('welcome-message')).toHaveTextContent(/welcome back, user/i);
  });

  it('fetches lists on mount', async () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/lists', {
        headers: { Authorization: 'Bearer test-token' },
      });
    });
  });

  it('does not fetch lists when no token is available', () => {
    // Clear any previous calls before this specific test
    mockFetch.mockClear();

    const store = createTestStore({
      auth: {
        user: null,
        token: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('displays loading state while fetching lists', async () => {
    // Make fetch never resolve to keep loading state
    mockFetch.mockImplementation(() => new Promise(() => {}));

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    // Wait for loading state to be set
    await waitFor(() => {
      expect(screen.getByTestId('loading-indicator')).toBeInTheDocument();
    });
    expect(screen.getByText(/loading your lists/i)).toBeInTheDocument();
  });

  it('displays error message when fetch fails', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
    });

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    // Wait for the fetch to fail and error to be set
    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText(/failed to load lists/i)).toBeInTheDocument();
  });

  it('renders PinnedListsRow section', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    expect(screen.getByText('Pinned Lists')).toBeInTheDocument();
    // Empty state message from PinnedListsRow
    expect(screen.getByTestId('pinned-lists-empty')).toBeInTheDocument();
  });

  it('renders NeedsAttentionList section', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    expect(screen.getByText('Needs Attention')).toBeInTheDocument();
    // Empty state message from NeedsAttentionList
    expect(screen.getByTestId('needs-attention-empty')).toBeInTheDocument();
  });

  it('displays pinned lists in PinnedListsRow', () => {
    // Note: This list is pinned but has 0 unchecked items, so it won't appear in NeedsAttentionList
    const pinnedList: ShoppingList = {
      id: 'list-1',
      name: 'Weekly Groceries',
      householdId: null,
      isPersonal: true,
      createdAt: '2026-02-01T10:00:00Z',
      isOwner: true,
      isPinned: true,
      itemCount: 5,
      uncheckedCount: 0, // No unchecked items, so it won't appear in NeedsAttentionList
    };

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      lists: {
        items: [pinnedList],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    expect(screen.getByTestId('pinned-lists-row')).toBeInTheDocument();
    expect(screen.getByTestId('pinned-list-name')).toHaveTextContent('Weekly Groceries');
  });

  it('displays lists with unchecked items in NeedsAttentionList', () => {
    // Note: This list is NOT pinned, so it only appears in NeedsAttentionList
    const listNeedingAttention: ShoppingList = {
      id: 'list-2',
      name: 'Party Supplies',
      householdId: 'h1',
      isPersonal: false,
      createdAt: '2026-02-01T10:00:00Z',
      updatedAt: '2026-02-05T10:00:00Z',
      isOwner: true,
      itemCount: 10,
      uncheckedCount: 7,
      isPinned: false, // Explicitly not pinned
      previewItems: ['Balloons', 'Plates', 'Napkins'],
    };

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      households: {
        items: [
          { id: 'h1', name: 'Family', createdAt: '2026-01-01', memberCount: 3, isOwner: true },
        ],
        isLoading: false,
        error: null,
      },
      lists: {
        items: [listNeedingAttention],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    expect(screen.getByTestId('needs-attention-list')).toBeInTheDocument();
    expect(screen.getByText('Party Supplies')).toBeInTheDocument();
    expect(screen.getByText('Family')).toBeInTheDocument();
  });

  it('renders navigation links', () => {
    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    expect(screen.getByRole('link', { name: /view all lists/i })).toHaveAttribute('href', '/lists');
    expect(screen.getByRole('link', { name: /manage households/i })).toHaveAttribute(
      'href',
      '/households'
    );
  });

  it('updates lists in store after successful fetch', async () => {
    const mockLists: ShoppingList[] = [
      {
        id: 'list-1',
        name: 'Fetched List',
        householdId: null,
        isPersonal: true,
        createdAt: '2026-02-01T10:00:00Z',
        isOwner: true,
        itemCount: 3,
        uncheckedCount: 1,
      },
    ];

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockLists),
    });

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    await waitFor(() => {
      expect(store.getState().lists.items).toHaveLength(1);
      expect(store.getState().lists.items[0].name).toBe('Fetched List');
    });
  });

  it('sets error in store when fetch fails', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
    });

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    await waitFor(() => {
      expect(store.getState().lists.error).toBe('Failed to load lists');
    });
  });

  it('handles network error gracefully', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'));

    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
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
    });

    render(<DashboardPage />, { store });

    await waitFor(() => {
      expect(store.getState().lists.error).toBe('Failed to load lists');
    });

    consoleSpy.mockRestore();
  });

  it('passes household names to child components', () => {
    // This list is pinned AND has unchecked items, so it appears in both sections
    // We test that household name is passed correctly
    const pinnedList: ShoppingList = {
      id: 'list-1',
      name: 'Family List',
      householdId: 'h1',
      isPersonal: false,
      createdAt: '2026-02-01T10:00:00Z',
      isOwner: true,
      isPinned: true,
      itemCount: 5,
      uncheckedCount: 3,
    };

    const store = createTestStore({
      auth: {
        user: { id: '1', email: 'test@example.com', displayName: 'Test User', avatarUrl: null },
        token: 'test-token',
        isAuthenticated: true,
        isLoading: false,
        error: null,
      },
      households: {
        items: [
          { id: 'h1', name: 'The Smiths', createdAt: '2026-01-01', memberCount: 4, isOwner: true },
        ],
        isLoading: false,
        error: null,
      },
      lists: {
        items: [pinnedList],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    render(<DashboardPage />, { store });

    // Household name should appear in both pinned and needs-attention sections
    // Use getAllByText since it appears twice (once in each section)
    const householdNameElements = screen.getAllByText('The Smiths');
    expect(householdNameElements.length).toBeGreaterThanOrEqual(1);
  });
});

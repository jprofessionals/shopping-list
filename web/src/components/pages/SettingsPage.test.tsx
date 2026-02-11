import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import SettingsPage from './SettingsPage';

// Mock fetch
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock localStorage
const mockLocalStorage = {
  store: {} as Record<string, string>,
  getItem: vi.fn((key: string) => mockLocalStorage.store[key] || null),
  setItem: vi.fn((key: string, value: string) => {
    mockLocalStorage.store[key] = value;
  }),
  removeItem: vi.fn((key: string) => {
    delete mockLocalStorage.store[key];
  }),
  clear: vi.fn(() => {
    mockLocalStorage.store = {};
  }),
};

describe('SettingsPage', () => {
  const mockPreferences = {
    smartParsingEnabled: true,
    defaultQuantity: 1,
    theme: 'system',
  };

  const mockLists = [
    {
      id: 'list-1',
      name: 'Grocery List',
      householdId: null,
      isPersonal: true,
      createdAt: '2024-01-01T00:00:00Z',
      isOwner: true,
      isPinned: true,
    },
    {
      id: 'list-2',
      name: 'Work Supplies',
      householdId: null,
      isPersonal: true,
      createdAt: '2024-01-02T00:00:00Z',
      isOwner: true,
      isPinned: false,
    },
  ];

  const createAuthState = () => ({
    auth: {
      user: {
        id: '1',
        email: 'john.doe@example.com',
        displayName: 'John Doe',
        avatarUrl: null,
      },
      token: 'test-token',
      isAuthenticated: true,
      isLoading: false,
      error: null,
    },
  });

  // Default state with lists pre-populated to avoid fetch
  const createDefaultState = () => ({
    ...createAuthState(),
    lists: {
      items: mockLists,
      currentListId: null,
      currentListItems: [],
      isLoading: false,
      error: null,
    },
  });

  beforeEach(() => {
    mockFetch.mockReset();
    mockNavigate.mockReset();
    mockLocalStorage.store = {};
    vi.stubGlobal('localStorage', mockLocalStorage);
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('renders settings title', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    expect(screen.getByText('Settings')).toBeInTheDocument();
  });

  it('displays user profile information', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    expect(screen.getByTestId('user-display-name')).toHaveTextContent('John Doe');
    expect(screen.getByTestId('user-email')).toHaveTextContent('john.doe@example.com');
  });

  it('renders account section with sign out button', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    expect(screen.getByTestId('account-section')).toBeInTheDocument();
    expect(screen.getByTestId('sign-out-button')).toBeInTheDocument();
  });

  it('signs out and navigates to login when sign out is clicked', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    mockLocalStorage.store['token'] = 'test-token';
    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    const signOutButton = screen.getByTestId('sign-out-button');
    await userEvent.click(signOutButton);

    expect(mockLocalStorage.removeItem).toHaveBeenCalledWith('token');
    expect(mockNavigate).toHaveBeenCalledWith('/login');
    expect(store.getState().auth.isAuthenticated).toBe(false);
  });

  it('fetches and displays preferences', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('smart-parsing-toggle')).toBeInTheDocument();
    });

    expect(screen.getByTestId('smart-parsing-toggle')).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByTestId('default-quantity-select')).toHaveValue('1');
    expect(screen.getByTestId('theme-select')).toHaveValue('system');
  });

  it('shows loading spinner while fetching preferences', async () => {
    let resolvePreferences: (value: unknown) => void;
    mockFetch.mockReturnValue(
      new Promise((resolve) => {
        resolvePreferences = resolve;
      })
    );

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();

    // Resolve the fetch
    resolvePreferences!({
      ok: true,
      json: async () => mockPreferences,
    });

    await waitFor(() => {
      expect(screen.queryByTestId('loading-spinner')).not.toBeInTheDocument();
    });
  });

  it('shows error when preferences fetch fails', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByText('Failed to load preferences')).toBeInTheDocument();
    });
  });

  it('updates smart parsing preference when toggled', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...mockPreferences, smartParsingEnabled: false }),
      });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('smart-parsing-toggle')).toBeInTheDocument();
    });

    const toggle = screen.getByTestId('smart-parsing-toggle');
    await userEvent.click(toggle);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/preferences', {
        method: 'PATCH',
        headers: {
          Authorization: 'Bearer test-token',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ smartParsingEnabled: false }),
      });
    });
  });

  it('updates default quantity when changed', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...mockPreferences, defaultQuantity: 2 }),
      });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('default-quantity-select')).toBeInTheDocument();
    });

    const select = screen.getByTestId('default-quantity-select');
    fireEvent.change(select, { target: { value: '2' } });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/preferences', {
        method: 'PATCH',
        headers: {
          Authorization: 'Bearer test-token',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ defaultQuantity: 2 }),
      });
    });
  });

  it('updates theme when changed', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...mockPreferences, theme: 'dark' }),
      });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('theme-select')).toBeInTheDocument();
    });

    const select = screen.getByTestId('theme-select');
    fireEvent.change(select, { target: { value: 'dark' } });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/preferences', {
        method: 'PATCH',
        headers: {
          Authorization: 'Bearer test-token',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ theme: 'dark' }),
      });
    });
  });

  it('shows success message after saving preference', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...mockPreferences, theme: 'dark' }),
      });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('theme-select')).toBeInTheDocument();
    });

    const select = screen.getByTestId('theme-select');
    fireEvent.change(select, { target: { value: 'dark' } });

    await waitFor(() => {
      expect(screen.getByTestId('save-success')).toBeInTheDocument();
    });
  });

  it('displays pinned lists section', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());

    render(<SettingsPage />, { store });

    expect(screen.getByTestId('pinned-lists-section')).toBeInTheDocument();
    expect(screen.getByText('Pinned Lists')).toBeInTheDocument();
    expect(screen.getByTestId('pinned-lists')).toBeInTheDocument();
    expect(screen.getByText('Grocery List')).toBeInTheDocument();
    expect(screen.queryByText('Work Supplies')).not.toBeInTheDocument(); // Not pinned
  });

  it('shows empty state when no pinned lists', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore({
      ...createAuthState(),
      lists: {
        items: [{ ...mockLists[0], isPinned: false }, mockLists[1]],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    render(<SettingsPage />, { store });

    expect(screen.getByTestId('no-pinned-lists')).toBeInTheDocument();
    expect(screen.getByText(/You haven't pinned any lists yet/)).toBeInTheDocument();
  });

  it('unpins a list when unpin button is clicked', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ pinned: false }),
      });

    const store = createTestStore(createDefaultState());

    render(<SettingsPage />, { store });

    const unpinButton = screen.getByTestId('unpin-button');
    await userEvent.click(unpinButton);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/lists/list-1/pin', {
        method: 'DELETE',
        headers: { Authorization: 'Bearer test-token' },
      });
    });

    // Check that the list was updated in state
    expect(
      store.getState().lists.items.find((l: { id: string }) => l.id === 'list-1')?.isPinned
    ).toBe(false);
  });

  it('renders keyboard shortcuts section', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    expect(screen.getByTestId('keyboard-shortcuts-section')).toBeInTheDocument();
    expect(screen.getByText('Keyboard Shortcuts')).toBeInTheDocument();
    expect(screen.getByTestId('shortcuts-table')).toBeInTheDocument();
  });

  it('displays all keyboard shortcuts in the table', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    // Check for some expected shortcuts
    expect(screen.getByText('Focus quick-add input')).toBeInTheDocument();
    expect(screen.getByText('Add item (when input focused)')).toBeInTheDocument();
    expect(screen.getByText('Close modal / clear input')).toBeInTheDocument();
    expect(screen.getByText('Go to Home / Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Go to Lists')).toBeInTheDocument();
    expect(screen.getByText('Go to Settings')).toBeInTheDocument();
    expect(screen.getByText('Show keyboard shortcuts')).toBeInTheDocument();
  });

  it('fetches lists if not already loaded', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockLists,
      });

    const store = createTestStore({
      ...createAuthState(),
      lists: {
        items: [],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
    });

    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('http://localhost:8080/api/lists', {
        headers: { Authorization: 'Bearer test-token' },
      });
    });
  });

  it('does not fetch lists if already loaded', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => mockPreferences,
    });

    const store = createTestStore(createDefaultState());

    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('smart-parsing-toggle')).toBeInTheDocument();
    });

    // Verify only preferences endpoint was called
    const listsFetchCalls = mockFetch.mock.calls.filter(
      (call) => call[0] === 'http://localhost:8080/api/lists'
    );
    expect(listsFetchCalls.length).toBe(0);
  });

  it('handles preference save error gracefully', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockPreferences,
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 500,
      });

    const store = createTestStore(createDefaultState());
    render(<SettingsPage />, { store });

    await waitFor(() => {
      expect(screen.getByTestId('theme-select')).toBeInTheDocument();
    });

    const select = screen.getByTestId('theme-select');
    fireEvent.change(select, { target: { value: 'dark' } });

    await waitFor(() => {
      expect(screen.getByText('Failed to save preference')).toBeInTheDocument();
    });
  });
});

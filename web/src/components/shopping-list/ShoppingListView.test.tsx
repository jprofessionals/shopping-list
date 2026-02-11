import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import ShoppingListView from './ShoppingListView';

const getDefaultStore = () =>
  createTestStore({
    auth: {
      user: {
        id: 'user-1',
        email: 'test@example.com',
        displayName: 'Test User',
        avatarUrl: null,
      },
      token: 'test-token',
      isAuthenticated: true,
      isLoading: false,
      error: null,
    },
    lists: {
      items: [
        {
          id: 'list-1',
          name: 'Weekly Groceries',
          householdId: null,
          isPersonal: true,
          createdAt: '2024-01-01',
          isOwner: true,
        },
      ],
      currentListId: 'list-1',
      currentListItems: [
        {
          id: 'item-1',
          name: 'Milk',
          quantity: 2,
          unit: 'liters',
          isChecked: false,
          checkedByName: null,
          createdAt: '2024-01-01',
        },
        {
          id: 'item-2',
          name: 'Bread',
          quantity: 1,
          unit: null,
          isChecked: true,
          checkedByName: 'Test User',
          createdAt: '2024-01-02',
        },
      ],
      isLoading: false,
      error: null,
    },
    households: {
      items: [],
      isLoading: false,
      error: null,
    },
  });

describe('ShoppingListView', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }))
    );
  });

  it('renders list name and items', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    expect(screen.getByText('Weekly Groceries')).toBeInTheDocument();
    expect(screen.getByText('Milk')).toBeInTheDocument();
  });

  it('shows checked items with strikethrough', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    const breadItem = screen.getByText('Bread');
    expect(breadItem).toHaveClass('line-through');
  });

  it('renders back button as link', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    const backLink = screen.getByRole('link', { name: /back/i });
    expect(backLink).toHaveAttribute('href', '/lists');
  });

  it('calls onBack when back button is clicked', () => {
    const onBack = vi.fn();
    render(<ShoppingListView listId="list-1" onBack={onBack} />, { store: getDefaultStore() });

    fireEvent.click(screen.getByRole('link', { name: /back/i }));
    expect(onBack).toHaveBeenCalled();
  });

  it('displays quantity and unit for items', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    expect(screen.getByText(/2 liters/)).toBeInTheDocument();
  });

  it('shows unchecked items first, then checked items section', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    expect(screen.getByText('Checked Items')).toBeInTheDocument();
    const uncheckedItem = screen.getByText('Milk');
    const checkedSection = screen.getByText('Checked Items');
    const checkedItem = screen.getByText('Bread');

    // Verify ordering via DOM position
    expect(uncheckedItem.compareDocumentPosition(checkedSection)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
    expect(checkedSection.compareDocumentPosition(checkedItem)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
  });

  it('shows add item form with name, quantity, unit inputs and add button', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    expect(screen.getByPlaceholderText(/item name/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/qty/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/unit/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^add$/i })).toBeInTheDocument();
  });

  it('renders delete buttons for items', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    expect(deleteButtons.length).toBeGreaterThan(0);
  });

  it('renders checkboxes for items', () => {
    render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes).toHaveLength(2);
    expect(checkboxes[0]).not.toBeChecked(); // Milk (unchecked)
    expect(checkboxes[1]).toBeChecked(); // Bread (checked)
  });

  describe('Clear checked functionality', () => {
    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('shows clear checked button when there are checked items', () => {
      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

      expect(screen.getByTestId('clear-checked-button')).toBeInTheDocument();
      expect(screen.getByText('Clear checked')).toBeInTheDocument();
    });

    it('does not show clear checked button when there are no checked items', () => {
      const store = createTestStore({
        auth: {
          user: {
            id: 'user-1',
            email: 'test@example.com',
            displayName: 'Test User',
            avatarUrl: null,
          },
          token: 'test-token',
          isAuthenticated: true,
          isLoading: false,
          error: null,
        },
        lists: {
          items: [
            {
              id: 'list-1',
              name: 'Weekly Groceries',
              householdId: null,
              isPersonal: true,
              createdAt: '2024-01-01',
              isOwner: true,
            },
          ],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Milk',
              quantity: 2,
              unit: 'liters',
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01',
            },
          ],
          isLoading: false,
          error: null,
        },
        households: { items: [], isLoading: false, error: null },
      });

      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store });

      expect(screen.queryByTestId('clear-checked-button')).not.toBeInTheDocument();
    });

    it('calls DELETE endpoint when clear checked is clicked', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ deletedItemIds: ['item-2'] }),
      });
      vi.stubGlobal('fetch', mockFetch);

      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

      fireEvent.click(screen.getByTestId('clear-checked-button'));

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/lists/list-1/items/checked',
          expect.objectContaining({ method: 'DELETE' })
        );
      });
    });

    it('shows toast with undo option after clearing checked items', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ deletedItemIds: ['item-2'] }),
      });
      vi.stubGlobal('fetch', mockFetch);

      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

      fireEvent.click(screen.getByTestId('clear-checked-button'));

      await waitFor(() => {
        expect(screen.getByTestId('toast-message')).toHaveTextContent('Cleared 1 checked item');
      });

      expect(screen.getByTestId('toast-undo-button')).toBeInTheDocument();
    });

    it('removes checked items from list after clearing', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ deletedItemIds: ['item-2'] }),
      });
      vi.stubGlobal('fetch', mockFetch);

      const store = getDefaultStore();
      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store });

      expect(screen.getByText('Bread')).toBeInTheDocument();

      fireEvent.click(screen.getByTestId('clear-checked-button'));

      await waitFor(() => {
        expect(screen.queryByText('Bread')).not.toBeInTheDocument();
      });
    });

    it('restores items when undo is clicked', async () => {
      const mockFetch = vi.fn().mockImplementation((url, options) => {
        if (options?.method === 'DELETE') {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ deletedItemIds: ['item-2'] }),
          });
        }
        // Bulk create endpoint for restore
        if (options?.method === 'POST' && url.includes('/bulk')) {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve([
                {
                  id: 'item-3',
                  name: 'Bread',
                  quantity: 1,
                  unit: null,
                  isChecked: false,
                  checkedByName: null,
                  createdAt: '2024-01-03',
                },
              ]),
          });
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
      });
      vi.stubGlobal('fetch', mockFetch);

      const store = getDefaultStore();
      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store });

      // Clear checked items
      fireEvent.click(screen.getByTestId('clear-checked-button'));

      // Wait for toast to appear
      await waitFor(() => {
        expect(screen.getByTestId('toast-undo-button')).toBeInTheDocument();
      });

      // Click undo
      fireEvent.click(screen.getByTestId('toast-undo-button'));

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/lists/list-1/items/bulk',
          expect.objectContaining({ method: 'POST' })
        );
      });
    });

    it('shows clearing state while operation is in progress', async () => {
      let resolveDelete: (value: Response) => void;
      const deletePromise = new Promise<Response>((resolve) => {
        resolveDelete = resolve;
      });
      const mockFetch = vi.fn().mockReturnValue(deletePromise);
      vi.stubGlobal('fetch', mockFetch);

      render(<ShoppingListView listId="list-1" onBack={() => {}} />, { store: getDefaultStore() });

      fireEvent.click(screen.getByTestId('clear-checked-button'));

      expect(screen.getByText('Clearing...')).toBeInTheDocument();

      // Resolve the promise
      resolveDelete!({
        ok: true,
        json: () => Promise.resolve({ deletedItemIds: ['item-2'] }),
      } as Response);

      await waitFor(() => {
        expect(screen.queryByText('Clearing...')).not.toBeInTheDocument();
      });
    });
  });
});

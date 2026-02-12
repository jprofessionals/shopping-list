import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import SharedListView from './SharedListView';

interface MockItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
}

// Mock ShoppingListView to avoid Redux/router dependencies
vi.mock('../shopping-list/ShoppingListView', () => ({
  default: (props: Record<string, unknown>) => (
    <div
      data-testid="shopping-list-view"
      data-permission={props.permission}
      data-share-token={props.shareToken}
      data-list-id={props.listId}
      data-shared-list-name={props.sharedListName}
    >
      {(props.sharedItems as MockItem[])?.map((item) => (
        <div key={item.id} data-testid={`item-${item.id}`}>
          <span>{item.name}</span>
          <span>
            {item.quantity} {item.unit}
          </span>
        </div>
      ))}
    </div>
  ),
}));

const mockFetch = vi.fn();

describe('SharedListView', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    mockFetch.mockReset();
  });

  it('shows loading state initially', () => {
    mockFetch.mockImplementation(() => new Promise(() => {})); // Never resolves

    render(<SharedListView token="test-token" />);
    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
  });

  it('shows expired message for 410 response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 410,
    });

    render(<SharedListView token="expired-token" />);

    await waitFor(() => {
      expect(screen.getByText(/link expired/i)).toBeInTheDocument();
    });
  });

  it('shows not found message for 404 response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 404,
    });

    render(<SharedListView token="invalid-token" />);

    await waitFor(() => {
      expect(screen.getByText(/not found/i)).toBeInTheDocument();
    });
  });

  it('shows error message for other error responses', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
    });

    render(<SharedListView token="error-token" />);

    await waitFor(() => {
      expect(screen.getByText(/an error occurred/i)).toBeInTheDocument();
    });
  });

  it('shows error message on network failure', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'));

    render(<SharedListView token="error-token" />);

    await waitFor(() => {
      expect(screen.getByText(/an error occurred/i)).toBeInTheDocument();
    });
  });

  it('shows list name and items on successful fetch', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Grocery List',
      permission: 'CHECK',
      items: [
        { id: 'item-1', name: 'Milk', quantity: 2, unit: 'liters', isChecked: false },
        { id: 'item-2', name: 'Bread', quantity: 1, unit: null, isChecked: true },
      ],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByTestId('shopping-list-view')).toHaveAttribute(
        'data-shared-list-name',
        'Grocery List'
      );
    });

    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();
  });

  it('shows permission level badge', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'READ',
      items: [],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByText(/view/i)).toBeInTheDocument();
    });
  });

  it('renders ShoppingListView with correct props on success', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'CHECK',
      items: [{ id: 'item-1', name: 'Milk', quantity: 1, unit: null, isChecked: false }],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByTestId('shopping-list-view')).toBeInTheDocument();
    });

    const listView = screen.getByTestId('shopping-list-view');
    expect(listView).toHaveAttribute('data-permission', 'CHECK');
    expect(listView).toHaveAttribute('data-share-token', 'valid-token');
    expect(listView).toHaveAttribute('data-list-id', 'list-1');
    expect(listView).toHaveAttribute('data-shared-list-name', 'Test List');
  });

  it('passes items to ShoppingListView', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'WRITE',
      items: [
        { id: 'item-1', name: 'Milk', quantity: 1, unit: null, isChecked: false },
        { id: 'item-2', name: 'Bread', quantity: 2, unit: 'loaves', isChecked: false },
      ],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByTestId('item-item-1')).toBeInTheDocument();
    });
    expect(screen.getByTestId('item-item-2')).toBeInTheDocument();
  });

  it('displays quantity and unit for items', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'READ',
      items: [{ id: 'item-1', name: 'Milk', quantity: 2, unit: 'liters', isChecked: false }],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByText(/2 liters/)).toBeInTheDocument();
    });
  });
});

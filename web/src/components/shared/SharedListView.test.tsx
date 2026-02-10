import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import SharedListView from './SharedListView';

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
      expect(screen.getByText('Grocery List')).toBeInTheDocument();
    });

    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();
  });

  it('shows permission level', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'VIEW',
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

  it('renders checkboxes for items when permission is CHECK', async () => {
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
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });
    expect(screen.getByRole('checkbox')).not.toBeDisabled();
  });

  it('renders checkboxes for items when permission is WRITE', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'WRITE',
      items: [{ id: 'item-1', name: 'Milk', quantity: 1, unit: null, isChecked: false }],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });
    expect(screen.getByRole('checkbox')).not.toBeDisabled();
  });

  it('disables checkboxes when permission is VIEW', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'VIEW',
      items: [{ id: 'item-1', name: 'Milk', quantity: 1, unit: null, isChecked: false }],
    };

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockList),
    });

    render(<SharedListView token="valid-token" />);

    await waitFor(() => {
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });
    expect(screen.getByRole('checkbox')).toBeDisabled();
  });

  it('displays quantity and unit for items', async () => {
    const mockList = {
      id: 'list-1',
      name: 'Test List',
      permission: 'VIEW',
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

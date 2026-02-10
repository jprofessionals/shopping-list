import { screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import ShoppingListsPage from './ShoppingListsPage';

describe('ShoppingListsPage', () => {
  it('renders empty state when no lists', () => {
    render(<ShoppingListsPage onCreateClick={() => {}} />);

    expect(screen.getByText(/no shopping lists/i)).toBeInTheDocument();
  });

  it('renders lists when available', () => {
    const store = createTestStore({
      lists: {
        items: [
          {
            id: '1',
            name: 'Grocery List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
        ],
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
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    expect(screen.getByText('Grocery List')).toBeInTheDocument();
  });

  it('shows loading spinner when loading', () => {
    const store = createTestStore({
      lists: {
        items: [],
        currentListId: null,
        currentListItems: [],
        isLoading: true,
        error: null,
      },
      households: {
        items: [],
        isLoading: false,
        error: null,
      },
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
  });

  it('shows Private badge for personal lists', () => {
    const store = createTestStore({
      lists: {
        items: [
          {
            id: '1',
            name: 'My Private List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
        ],
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
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    expect(screen.getByText('Private')).toBeInTheDocument();
  });

  it('groups lists by household', () => {
    const store = createTestStore({
      lists: {
        items: [
          {
            id: '1',
            name: 'Personal List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
          {
            id: '2',
            name: 'Family Groceries',
            householdId: 'h1',
            isPersonal: false,
            createdAt: '2024-01-02',
            isOwner: true,
          },
        ],
        currentListId: null,
        currentListItems: [],
        isLoading: false,
        error: null,
      },
      households: {
        items: [
          { id: 'h1', name: 'Family', createdAt: '2024-01-01', memberCount: 3, isOwner: true },
        ],
        isLoading: false,
        error: null,
      },
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    expect(screen.getByText('Personal Lists')).toBeInTheDocument();
    // Section heading for the household group
    expect(screen.getByRole('heading', { name: 'Family' })).toBeInTheDocument();
    expect(screen.getByText('Personal List')).toBeInTheDocument();
    expect(screen.getByText('Family Groceries')).toBeInTheDocument();
  });

  it('renders list cards as links to list detail', () => {
    const store = createTestStore({
      lists: {
        items: [
          {
            id: 'list-123',
            name: 'Test List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
        ],
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
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    const link = screen.getByRole('link', { name: /test list/i });
    expect(link).toHaveAttribute('href', '/lists/list-123');
  });

  it('calls onCreateClick when clicking create button', () => {
    const onCreateClick = vi.fn();
    const store = createTestStore({
      lists: {
        items: [
          {
            id: '1',
            name: 'Existing List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
        ],
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
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={onCreateClick} />, { store });

    fireEvent.click(screen.getByRole('button', { name: /^create list$/i }));
    expect(onCreateClick).toHaveBeenCalled();
  });

  it('shows create first list button in empty state', () => {
    const onCreateClick = vi.fn();

    render(<ShoppingListsPage onCreateClick={onCreateClick} />);

    fireEvent.click(screen.getByRole('button', { name: /create your first list/i }));
    expect(onCreateClick).toHaveBeenCalled();
  });

  it('renders list items as links with correct href', () => {
    const store = createTestStore({
      lists: {
        items: [
          {
            id: 'test-list-id',
            name: 'Test List',
            householdId: null,
            isPersonal: true,
            createdAt: '2024-01-01',
            isOwner: true,
          },
        ],
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
      auth: { user: null, token: null, isAuthenticated: false, isLoading: false, error: null },
    });

    render(<ShoppingListsPage onCreateClick={() => {}} />, { store });

    const link = screen.getByRole('link', { name: /test list/i });
    expect(link).toHaveAttribute('href', '/lists/test-list-id');
  });
});

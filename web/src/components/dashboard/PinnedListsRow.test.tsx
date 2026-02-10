import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { render } from '../../test/testUtils';
import PinnedListsRow from './PinnedListsRow';
import { type ShoppingList } from '../../store/listsSlice';

describe('PinnedListsRow', () => {
  const createList = (overrides: Partial<ShoppingList> = {}): ShoppingList => ({
    id: 'list-1',
    name: 'Test List',
    householdId: null,
    isPersonal: true,
    createdAt: '2026-02-01T10:00:00Z',
    isOwner: true,
    itemCount: 5,
    uncheckedCount: 3,
    isPinned: false,
    ...overrides,
  });

  describe('empty state', () => {
    it('shows empty message when no lists are provided', () => {
      render(<PinnedListsRow lists={[]} />);

      expect(screen.getByTestId('pinned-lists-empty')).toBeInTheDocument();
      expect(screen.getByText(/No pinned lists/)).toBeInTheDocument();
    });

    it('shows empty message when no lists are pinned', () => {
      const lists = [
        createList({ id: '1', isPinned: false }),
        createList({ id: '2', isPinned: false }),
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-lists-empty')).toBeInTheDocument();
    });
  });

  describe('with pinned lists', () => {
    it('renders pinned lists row container', () => {
      const lists = [createList({ id: '1', isPinned: true })];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-lists-row')).toBeInTheDocument();
    });

    it('only shows pinned lists, filtering out non-pinned', () => {
      const lists = [
        createList({ id: '1', name: 'Pinned List', isPinned: true }),
        createList({ id: '2', name: 'Not Pinned', isPinned: false }),
        createList({ id: '3', name: 'Another Pinned', isPinned: true }),
      ];

      render(<PinnedListsRow lists={lists} />);

      const cards = screen.getAllByTestId('pinned-list-card');
      expect(cards).toHaveLength(2);

      expect(screen.getByText('Pinned List')).toBeInTheDocument();
      expect(screen.getByText('Another Pinned')).toBeInTheDocument();
      expect(screen.queryByText('Not Pinned')).not.toBeInTheDocument();
    });

    it('renders compact cards with list name', () => {
      const lists = [createList({ id: '1', name: 'Weekly Groceries', isPinned: true })];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-list-name')).toHaveTextContent('Weekly Groceries');
    });

    it('links to list detail page', () => {
      const lists = [createList({ id: 'abc-123', isPinned: true })];

      render(<PinnedListsRow lists={lists} />);

      const link = screen.getByRole('link');
      expect(link).toHaveAttribute('href', '/lists/abc-123');
    });

    it('shows pin indicator on each card', () => {
      const lists = [createList({ id: '1', isPinned: true })];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('compact-pin-indicator')).toBeInTheDocument();
    });
  });

  describe('status display', () => {
    it('shows unchecked count when items remain', () => {
      const lists = [
        createList({
          id: '1',
          isPinned: true,
          itemCount: 10,
          uncheckedCount: 4,
        }),
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-list-status')).toHaveTextContent('4 remaining');
    });

    it('shows "All done!" when all items are checked', () => {
      const lists = [
        createList({
          id: '1',
          isPinned: true,
          itemCount: 5,
          uncheckedCount: 0,
        }),
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-list-status')).toHaveTextContent('All done!');
    });

    it('shows "Empty" for lists with no items', () => {
      const lists = [
        createList({
          id: '1',
          isPinned: true,
          itemCount: 0,
          uncheckedCount: 0,
        }),
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-list-status')).toHaveTextContent('Empty');
    });
  });

  describe('ownership display', () => {
    it('shows "Private" for personal lists', () => {
      const lists = [createList({ id: '1', isPinned: true, isPersonal: true })];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByText('Private')).toBeInTheDocument();
    });

    it('shows household name for household lists', () => {
      const lists = [
        createList({
          id: '1',
          isPinned: true,
          isPersonal: false,
          householdId: 'h1',
        }),
      ];
      const householdNames = { h1: 'Family Home' };

      render(<PinnedListsRow lists={lists} householdNames={householdNames} />);

      expect(screen.getByText('Family Home')).toBeInTheDocument();
    });

    it('shows "Household" as fallback when household name not provided', () => {
      const lists = [
        createList({
          id: '1',
          isPinned: true,
          isPersonal: false,
          householdId: 'h1',
        }),
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByText('Household')).toBeInTheDocument();
    });
  });

  describe('multiple pinned lists', () => {
    it('renders all pinned lists in order', () => {
      const lists = [
        createList({ id: '1', name: 'First', isPinned: true }),
        createList({ id: '2', name: 'Second', isPinned: true }),
        createList({ id: '3', name: 'Third', isPinned: true }),
      ];

      render(<PinnedListsRow lists={lists} />);

      const cards = screen.getAllByTestId('pinned-list-card');
      expect(cards).toHaveLength(3);

      const names = screen.getAllByTestId('pinned-list-name');
      expect(names[0]).toHaveTextContent('First');
      expect(names[1]).toHaveTextContent('Second');
      expect(names[2]).toHaveTextContent('Third');
    });
  });

  describe('handles undefined optional fields', () => {
    it('handles list without itemCount and uncheckedCount', () => {
      const lists: ShoppingList[] = [
        {
          id: '1',
          name: 'Minimal List',
          householdId: null,
          isPersonal: true,
          createdAt: '2026-02-01T10:00:00Z',
          isOwner: true,
          isPinned: true,
        },
      ];

      render(<PinnedListsRow lists={lists} />);

      expect(screen.getByTestId('pinned-list-status')).toHaveTextContent('Empty');
    });
  });
});

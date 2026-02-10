import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { render } from '../../test/testUtils';
import NeedsAttentionList from './NeedsAttentionList';
import { type ShoppingList } from '../../store/listsSlice';

describe('NeedsAttentionList', () => {
  const createList = (overrides: Partial<ShoppingList> = {}): ShoppingList => ({
    id: 'list-1',
    name: 'Test List',
    householdId: null,
    isPersonal: true,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-01T10:00:00Z',
    isOwner: true,
    itemCount: 5,
    uncheckedCount: 3,
    isPinned: false,
    ...overrides,
  });

  describe('empty state', () => {
    it('shows "All caught up!" when no lists are provided', () => {
      render(<NeedsAttentionList lists={[]} />);

      expect(screen.getByTestId('needs-attention-empty')).toBeInTheDocument();
      expect(screen.getByText('All caught up!')).toBeInTheDocument();
      expect(screen.getByText('No lists need your attention right now.')).toBeInTheDocument();
    });

    it('shows empty state when all lists have uncheckedCount of 0', () => {
      const lists = [
        createList({ id: '1', uncheckedCount: 0 }),
        createList({ id: '2', uncheckedCount: 0 }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      expect(screen.getByTestId('needs-attention-empty')).toBeInTheDocument();
    });

    it('shows empty state when lists have undefined uncheckedCount', () => {
      const lists: ShoppingList[] = [
        {
          id: '1',
          name: 'Minimal List',
          householdId: null,
          isPersonal: true,
          createdAt: '2026-02-01T10:00:00Z',
          isOwner: true,
        },
      ];

      render(<NeedsAttentionList lists={lists} />);

      expect(screen.getByTestId('needs-attention-empty')).toBeInTheDocument();
    });
  });

  describe('with lists needing attention', () => {
    it('renders list container', () => {
      const lists = [createList({ id: '1', uncheckedCount: 2 })];

      render(<NeedsAttentionList lists={lists} />);

      expect(screen.getByTestId('needs-attention-list')).toBeInTheDocument();
    });

    it('only shows lists with unchecked items', () => {
      const lists = [
        createList({ id: '1', name: 'Needs Attention', uncheckedCount: 3 }),
        createList({ id: '2', name: 'All Done', uncheckedCount: 0 }),
        createList({ id: '3', name: 'Also Needs Attention', uncheckedCount: 1 }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      const cards = screen.getAllByTestId('list-card');
      expect(cards).toHaveLength(2);

      expect(screen.getByText('Needs Attention')).toBeInTheDocument();
      expect(screen.getByText('Also Needs Attention')).toBeInTheDocument();
      expect(screen.queryByText('All Done')).not.toBeInTheDocument();
    });

    it('uses ListCard component for each list', () => {
      const lists = [createList({ id: '1', name: 'Test List', uncheckedCount: 2 })];

      render(<NeedsAttentionList lists={lists} />);

      // ListCard renders as a link to the list
      const link = screen.getByRole('link');
      expect(link).toHaveAttribute('href', '/lists/1');
    });
  });

  describe('sorting by updatedAt', () => {
    it('sorts lists by updatedAt descending (most recent first)', () => {
      const lists = [
        createList({
          id: '1',
          name: 'Oldest',
          uncheckedCount: 1,
          updatedAt: '2026-02-01T10:00:00Z',
        }),
        createList({
          id: '2',
          name: 'Newest',
          uncheckedCount: 2,
          updatedAt: '2026-02-05T10:00:00Z',
        }),
        createList({
          id: '3',
          name: 'Middle',
          uncheckedCount: 1,
          updatedAt: '2026-02-03T10:00:00Z',
        }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      const listNames = screen.getAllByRole('heading', { level: 3 });
      expect(listNames[0]).toHaveTextContent('Newest');
      expect(listNames[1]).toHaveTextContent('Middle');
      expect(listNames[2]).toHaveTextContent('Oldest');
    });

    it('handles lists without updatedAt by treating them as oldest', () => {
      const lists = [
        createList({ id: '1', name: 'No Update Date', uncheckedCount: 1, updatedAt: undefined }),
        createList({
          id: '2',
          name: 'Has Update Date',
          uncheckedCount: 2,
          updatedAt: '2026-02-05T10:00:00Z',
        }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      const listNames = screen.getAllByRole('heading', { level: 3 });
      expect(listNames[0]).toHaveTextContent('Has Update Date');
      expect(listNames[1]).toHaveTextContent('No Update Date');
    });
  });

  describe('limit prop', () => {
    it('limits display to 5 lists by default', () => {
      const lists = Array.from({ length: 10 }, (_, i) =>
        createList({
          id: `${i + 1}`,
          name: `List ${i + 1}`,
          uncheckedCount: 1,
          updatedAt: `2026-02-0${Math.min(i + 1, 9)}T10:00:00Z`,
        })
      );

      render(<NeedsAttentionList lists={lists} />);

      const cards = screen.getAllByTestId('list-card');
      expect(cards).toHaveLength(5);
    });

    it('respects custom limit prop', () => {
      const lists = Array.from({ length: 10 }, (_, i) =>
        createList({
          id: `${i + 1}`,
          name: `List ${i + 1}`,
          uncheckedCount: 1,
        })
      );

      render(<NeedsAttentionList lists={lists} limit={3} />);

      const cards = screen.getAllByTestId('list-card');
      expect(cards).toHaveLength(3);
    });

    it('shows all lists when fewer than limit', () => {
      const lists = [
        createList({ id: '1', uncheckedCount: 1 }),
        createList({ id: '2', uncheckedCount: 2 }),
      ];

      render(<NeedsAttentionList lists={lists} limit={10} />);

      const cards = screen.getAllByTestId('list-card');
      expect(cards).toHaveLength(2);
    });

    it('shows most recently updated lists when limited', () => {
      const lists = [
        createList({
          id: '1',
          name: 'Old List',
          uncheckedCount: 1,
          updatedAt: '2026-02-01T10:00:00Z',
        }),
        createList({
          id: '2',
          name: 'Recent List',
          uncheckedCount: 1,
          updatedAt: '2026-02-05T10:00:00Z',
        }),
        createList({
          id: '3',
          name: 'Middle List',
          uncheckedCount: 1,
          updatedAt: '2026-02-03T10:00:00Z',
        }),
      ];

      render(<NeedsAttentionList lists={lists} limit={2} />);

      const cards = screen.getAllByTestId('list-card');
      expect(cards).toHaveLength(2);

      expect(screen.getByText('Recent List')).toBeInTheDocument();
      expect(screen.getByText('Middle List')).toBeInTheDocument();
      expect(screen.queryByText('Old List')).not.toBeInTheDocument();
    });
  });

  describe('household names', () => {
    it('passes household name to ListCard for household lists', () => {
      const lists = [
        createList({
          id: '1',
          name: 'Family List',
          isPersonal: false,
          householdId: 'h1',
          uncheckedCount: 2,
        }),
      ];
      const householdNames = { h1: 'Family Home' };

      render(<NeedsAttentionList lists={lists} householdNames={householdNames} />);

      expect(screen.getByText('Family Home')).toBeInTheDocument();
    });

    it('shows "Household" when household name not provided', () => {
      const lists = [
        createList({
          id: '1',
          name: 'Work List',
          isPersonal: false,
          householdId: 'h2',
          uncheckedCount: 1,
        }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      expect(screen.getByText('Household')).toBeInTheDocument();
    });

    it('shows "Private" for personal lists', () => {
      const lists = [
        createList({
          id: '1',
          name: 'My List',
          isPersonal: true,
          uncheckedCount: 1,
        }),
      ];

      render(<NeedsAttentionList lists={lists} />);

      expect(screen.getByText('Private')).toBeInTheDocument();
    });
  });

  describe('mixed lists', () => {
    it('handles mix of personal and household lists correctly', () => {
      const lists = [
        createList({ id: '1', name: 'Personal List', isPersonal: true, uncheckedCount: 2 }),
        createList({
          id: '2',
          name: 'Household List',
          isPersonal: false,
          householdId: 'h1',
          uncheckedCount: 3,
        }),
      ];
      const householdNames = { h1: 'Family' };

      render(<NeedsAttentionList lists={lists} householdNames={householdNames} />);

      expect(screen.getByText('Private')).toBeInTheDocument();
      expect(screen.getByText('Family')).toBeInTheDocument();
    });
  });
});

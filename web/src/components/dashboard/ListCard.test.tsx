import { screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render } from '../../test/testUtils';
import ListCard from './ListCard';
import { type ShoppingList } from '../../store/listsSlice';

describe('ListCard', () => {
  const baseList: ShoppingList = {
    id: 'list-123',
    name: 'Weekly Groceries',
    householdId: null,
    isPersonal: true,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-05T10:30:00Z',
    isOwner: true,
    itemCount: 8,
    uncheckedCount: 3,
    previewItems: ['Milk', 'Bread', 'Eggs'],
    lastActivity: {
      type: 'item:checked',
      actorName: 'Lars',
      itemName: 'Butter',
      timestamp: '2026-02-05T10:30:00Z',
    },
    isPinned: false,
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-02-05T11:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders list name as a link to the detail page', () => {
    render(<ListCard list={baseList} />);

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/lists/list-123');
    expect(screen.getByText('Weekly Groceries')).toBeInTheDocument();
  });

  it('shows Private badge for personal lists', () => {
    render(<ListCard list={baseList} />);

    expect(screen.getByText('Private')).toBeInTheDocument();
  });

  it('shows household name badge for household lists', () => {
    const householdList: ShoppingList = {
      ...baseList,
      isPersonal: false,
      householdId: 'h1',
    };

    render(<ListCard list={householdList} householdName="Family" />);

    expect(screen.getByText('Family')).toBeInTheDocument();
    expect(screen.queryByText('Private')).not.toBeInTheDocument();
  });

  it('shows "Household" as fallback when no household name provided', () => {
    const householdList: ShoppingList = {
      ...baseList,
      isPersonal: false,
      householdId: 'h1',
    };

    render(<ListCard list={householdList} />);

    expect(screen.getByText('Household')).toBeInTheDocument();
  });

  it('displays remaining items count status', () => {
    render(<ListCard list={baseList} />);

    expect(screen.getByTestId('status-text')).toHaveTextContent('3 remaining');
  });

  it('displays "All done!" when all items are checked', () => {
    const completedList: ShoppingList = {
      ...baseList,
      itemCount: 5,
      uncheckedCount: 0,
    };

    render(<ListCard list={completedList} />);

    expect(screen.getByTestId('status-text')).toHaveTextContent('All done!');
  });

  it('displays "Empty list" when no items exist', () => {
    const emptyList: ShoppingList = {
      ...baseList,
      itemCount: 0,
      uncheckedCount: 0,
      previewItems: [],
    };

    render(<ListCard list={emptyList} />);

    expect(screen.getByTestId('status-text')).toHaveTextContent('Empty list');
  });

  it('renders preview items', () => {
    render(<ListCard list={baseList} />);

    const previewItems = screen.getByTestId('preview-items');
    expect(previewItems).toBeInTheDocument();
    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();
    expect(screen.getByText('Eggs')).toBeInTheDocument();
  });

  it('limits preview items to 3', () => {
    const listWithManyItems: ShoppingList = {
      ...baseList,
      previewItems: ['Milk', 'Bread', 'Eggs', 'Butter', 'Cheese'],
    };

    render(<ListCard list={listWithManyItems} />);

    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();
    expect(screen.getByText('Eggs')).toBeInTheDocument();
    expect(screen.queryByText('Butter')).not.toBeInTheDocument();
    expect(screen.queryByText('Cheese')).not.toBeInTheDocument();
  });

  it('does not render preview section when no items', () => {
    const emptyList: ShoppingList = {
      ...baseList,
      previewItems: [],
    };

    render(<ListCard list={emptyList} />);

    expect(screen.queryByTestId('preview-items')).not.toBeInTheDocument();
  });

  it('renders last activity with relative time', () => {
    render(<ListCard list={baseList} />);

    const activity = screen.getByTestId('last-activity');
    expect(activity).toHaveTextContent('Lars checked off Butter');
    expect(activity).toHaveTextContent('30m ago');
  });

  it('formats activity for item:added', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: {
        type: 'item:added',
        actorName: 'Anna',
        itemName: 'Apples',
        timestamp: '2026-02-05T10:30:00Z',
      },
    };

    render(<ListCard list={list} />);

    expect(screen.getByTestId('last-activity')).toHaveTextContent('Anna added Apples');
  });

  it('formats activity for item:unchecked', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: {
        type: 'item:unchecked',
        actorName: 'Erik',
        itemName: 'Milk',
        timestamp: '2026-02-05T10:30:00Z',
      },
    };

    render(<ListCard list={list} />);

    expect(screen.getByTestId('last-activity')).toHaveTextContent('Erik unchecked Milk');
  });

  it('formats activity for item:removed', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: {
        type: 'item:removed',
        actorName: 'Maria',
        itemName: 'Bread',
        timestamp: '2026-02-05T10:30:00Z',
      },
    };

    render(<ListCard list={list} />);

    expect(screen.getByTestId('last-activity')).toHaveTextContent('Maria removed Bread');
  });

  it('formats activity for list:created', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: {
        type: 'list:created',
        actorName: 'Lars',
        timestamp: '2026-02-05T10:30:00Z',
      },
    };

    render(<ListCard list={list} />);

    expect(screen.getByTestId('last-activity')).toHaveTextContent('Lars created this list');
  });

  it('formats activity for list:updated', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: {
        type: 'list:updated',
        actorName: 'Lars',
        timestamp: '2026-02-05T10:30:00Z',
      },
    };

    render(<ListCard list={list} />);

    expect(screen.getByTestId('last-activity')).toHaveTextContent('Lars updated this list');
  });

  it('does not render activity section when no lastActivity', () => {
    const list: ShoppingList = {
      ...baseList,
      lastActivity: undefined,
    };

    render(<ListCard list={list} />);

    expect(screen.queryByTestId('last-activity')).not.toBeInTheDocument();
  });

  it('shows pin indicator when list is pinned', () => {
    const pinnedList: ShoppingList = {
      ...baseList,
      isPinned: true,
    };

    render(<ListCard list={pinnedList} />);

    expect(screen.getByTestId('pin-indicator')).toBeInTheDocument();
  });

  it('does not show pin indicator when list is not pinned', () => {
    render(<ListCard list={baseList} />);

    expect(screen.queryByTestId('pin-indicator')).not.toBeInTheDocument();
  });

  describe('relative time formatting', () => {
    it('shows "just now" for very recent timestamps', () => {
      const list: ShoppingList = {
        ...baseList,
        lastActivity: {
          type: 'item:checked',
          actorName: 'Lars',
          itemName: 'Milk',
          timestamp: '2026-02-05T10:59:45Z', // 15 seconds ago
        },
      };

      render(<ListCard list={list} />);

      expect(screen.getByTestId('last-activity')).toHaveTextContent('just now');
    });

    it('shows hours for timestamps within the day', () => {
      const list: ShoppingList = {
        ...baseList,
        lastActivity: {
          type: 'item:checked',
          actorName: 'Lars',
          itemName: 'Milk',
          timestamp: '2026-02-05T08:00:00Z', // 3 hours ago
        },
      };

      render(<ListCard list={list} />);

      expect(screen.getByTestId('last-activity')).toHaveTextContent('3h ago');
    });

    it('shows "yesterday" for timestamps from yesterday', () => {
      const list: ShoppingList = {
        ...baseList,
        lastActivity: {
          type: 'item:checked',
          actorName: 'Lars',
          itemName: 'Milk',
          timestamp: '2026-02-04T10:00:00Z', // 1 day ago
        },
      };

      render(<ListCard list={list} />);

      expect(screen.getByTestId('last-activity')).toHaveTextContent('yesterday');
    });

    it('shows days for timestamps within a week', () => {
      const list: ShoppingList = {
        ...baseList,
        lastActivity: {
          type: 'item:checked',
          actorName: 'Lars',
          itemName: 'Milk',
          timestamp: '2026-02-02T10:00:00Z', // 3 days ago
        },
      };

      render(<ListCard list={list} />);

      expect(screen.getByTestId('last-activity')).toHaveTextContent('3d ago');
    });
  });

  it('handles missing optional fields gracefully', () => {
    const minimalList: ShoppingList = {
      id: 'list-456',
      name: 'Simple List',
      householdId: null,
      isPersonal: true,
      createdAt: '2026-02-01T10:00:00Z',
      isOwner: true,
    };

    render(<ListCard list={minimalList} />);

    expect(screen.getByText('Simple List')).toBeInTheDocument();
    expect(screen.getByTestId('status-text')).toHaveTextContent('Empty list');
    expect(screen.queryByTestId('preview-items')).not.toBeInTheDocument();
    expect(screen.queryByTestId('last-activity')).not.toBeInTheDocument();
    expect(screen.queryByTestId('pin-indicator')).not.toBeInTheDocument();
  });
});

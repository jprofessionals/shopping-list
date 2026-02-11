import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import ListItemRow from './ListItemRow';
import { type ListItem } from '../../store/listsSlice';

const createMockItem = (overrides: Partial<ListItem> = {}): ListItem => ({
  id: 'item-1',
  name: 'Milk',
  quantity: 2,
  unit: 'liters',
  isChecked: false,
  checkedByName: null,
  createdAt: '2024-01-01',
  ...overrides,
});

const defaultProps = {
  item: createMockItem(),
  onToggleCheck: vi.fn(),
  onDelete: vi.fn(),
  onQuantityChange: vi.fn(),
};

describe('ListItemRow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders item name, quantity, and unit', () => {
    render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

    expect(screen.getByText('Milk')).toBeInTheDocument();
    expect(screen.getByTestId('quantity-display')).toHaveTextContent('2 liters');
  });

  it('renders checkbox with correct checked state', () => {
    render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
  });

  it('renders checked item with strikethrough', () => {
    render(<ListItemRow {...defaultProps} item={createMockItem({ isChecked: true })} />, {
      store: createTestStore(),
    });

    expect(screen.getByText('Milk')).toHaveClass('line-through');
  });

  it('calls onToggleCheck when checkbox is clicked', () => {
    const onToggleCheck = vi.fn();
    render(<ListItemRow {...defaultProps} onToggleCheck={onToggleCheck} />, {
      store: createTestStore(),
    });

    fireEvent.click(screen.getByRole('checkbox'));
    expect(onToggleCheck).toHaveBeenCalledTimes(1);
  });

  it('calls onDelete when delete button is clicked', () => {
    const onDelete = vi.fn();
    render(<ListItemRow {...defaultProps} onDelete={onDelete} />, {
      store: createTestStore(),
    });

    fireEvent.click(screen.getByRole('button', { name: /delete/i }));
    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('does not render delete button when disabled', () => {
    render(<ListItemRow {...defaultProps} disabled />, { store: createTestStore() });

    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
  });

  describe('Recurring badge', () => {
    it('shows recurring badge when item has recurringItemId', () => {
      render(
        <ListItemRow {...defaultProps} item={createMockItem({ recurringItemId: 'rec-1' })} />,
        { store: createTestStore() }
      );

      expect(screen.getByTestId('recurring-badge')).toBeInTheDocument();
    });

    it('does not show recurring badge for regular items', () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      expect(screen.queryByTestId('recurring-badge')).not.toBeInTheDocument();
    });
  });

  describe('Quantity Stepper', () => {
    it('stepper is hidden by default', () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      const stepper = screen.getByTestId('quantity-stepper');
      expect(stepper).toHaveClass('max-w-0', 'opacity-0');
    });

    it('shows stepper when quantity is clicked', async () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      fireEvent.click(screen.getByTestId('quantity-display'));

      await waitFor(() => {
        const stepper = screen.getByTestId('quantity-stepper');
        expect(stepper).toHaveClass('max-w-24', 'opacity-100');
      });
    });

    it('shows stepper on mouse enter', async () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      const row = screen.getByRole('listitem');
      fireEvent.mouseEnter(row);

      await waitFor(() => {
        expect(screen.getByTestId('quantity-stepper')).toHaveClass('max-w-24', 'opacity-100');
      });
    });

    it('hides stepper on mouse leave', async () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      const row = screen.getByRole('listitem');

      // Open stepper via hover
      fireEvent.mouseEnter(row);
      await waitFor(() => {
        expect(screen.getByTestId('quantity-stepper')).toHaveClass('opacity-100');
      });

      // Leave row
      fireEvent.mouseLeave(row);

      await waitFor(() => {
        expect(screen.getByTestId('quantity-stepper')).toHaveClass('opacity-0');
      });
    });

    it('calls onQuantityChange with incremented value when + is clicked', async () => {
      const onQuantityChange = vi.fn().mockResolvedValue(undefined);
      render(<ListItemRow {...defaultProps} onQuantityChange={onQuantityChange} />, {
        store: createTestStore(),
      });

      // Open stepper
      fireEvent.click(screen.getByTestId('quantity-display'));

      // Click increment
      fireEvent.click(screen.getByTestId('quantity-increment'));

      await waitFor(() => {
        expect(onQuantityChange).toHaveBeenCalledWith(3); // 2 + 1
      });
    });

    it('calls onQuantityChange with decremented value when - is clicked', async () => {
      const onQuantityChange = vi.fn().mockResolvedValue(undefined);
      render(<ListItemRow {...defaultProps} onQuantityChange={onQuantityChange} />, {
        store: createTestStore(),
      });

      // Open stepper
      fireEvent.click(screen.getByTestId('quantity-display'));

      // Click decrement
      fireEvent.click(screen.getByTestId('quantity-decrement'));

      await waitFor(() => {
        expect(onQuantityChange).toHaveBeenCalledWith(1); // 2 - 1
      });
    });

    it('disables decrement button when quantity is 1', async () => {
      render(<ListItemRow {...defaultProps} item={createMockItem({ quantity: 1 })} />, {
        store: createTestStore(),
      });

      // Open stepper
      fireEvent.click(screen.getByTestId('quantity-display'));

      await waitFor(() => {
        expect(screen.getByTestId('quantity-decrement')).toBeDisabled();
      });
    });

    it('does not call onQuantityChange when decrement is clicked at quantity 1', async () => {
      const onQuantityChange = vi.fn();
      render(
        <ListItemRow
          {...defaultProps}
          item={createMockItem({ quantity: 1 })}
          onQuantityChange={onQuantityChange}
        />,
        { store: createTestStore() }
      );

      // Open stepper
      fireEvent.click(screen.getByTestId('quantity-display'));

      // Click decrement
      fireEvent.click(screen.getByTestId('quantity-decrement'));

      expect(onQuantityChange).not.toHaveBeenCalled();
    });

    it('does not show stepper when disabled', () => {
      render(<ListItemRow {...defaultProps} disabled />, { store: createTestStore() });

      fireEvent.click(screen.getByTestId('quantity-display'));

      expect(screen.getByTestId('quantity-stepper')).toHaveClass('opacity-0');
    });

    it('does not show stepper when onQuantityChange is not provided', () => {
      render(<ListItemRow {...defaultProps} onQuantityChange={undefined} />, {
        store: createTestStore(),
      });

      fireEvent.click(screen.getByTestId('quantity-display'));

      expect(screen.getByTestId('quantity-stepper')).toHaveClass('opacity-0');
    });

    it('sets aria-expanded correctly on quantity button', async () => {
      render(<ListItemRow {...defaultProps} />, { store: createTestStore() });

      const quantityButton = screen.getByTestId('quantity-display');
      expect(quantityButton).toHaveAttribute('aria-expanded', 'false');

      fireEvent.click(quantityButton);

      await waitFor(() => {
        expect(quantityButton).toHaveAttribute('aria-expanded', 'true');
      });
    });

    it('renders item without unit correctly', () => {
      render(<ListItemRow {...defaultProps} item={createMockItem({ unit: null })} />, {
        store: createTestStore(),
      });

      expect(screen.getByTestId('quantity-display')).toHaveTextContent('2');
    });

    it('handles multiple rapid clicks gracefully', async () => {
      let resolveFirst: () => void;
      const firstPromise = new Promise<void>((resolve) => {
        resolveFirst = resolve;
      });
      const onQuantityChange = vi
        .fn()
        .mockReturnValueOnce(firstPromise)
        .mockResolvedValue(undefined);

      render(<ListItemRow {...defaultProps} onQuantityChange={onQuantityChange} />, {
        store: createTestStore(),
      });

      // Open stepper
      fireEvent.click(screen.getByTestId('quantity-display'));

      // Click increment twice quickly
      fireEvent.click(screen.getByTestId('quantity-increment'));
      fireEvent.click(screen.getByTestId('quantity-increment'));

      // Only one call should be made while updating
      expect(onQuantityChange).toHaveBeenCalledTimes(1);

      // Resolve first promise
      await act(async () => {
        resolveFirst!();
      });

      // Now another click should work
      fireEvent.click(screen.getByTestId('quantity-increment'));

      await waitFor(() => {
        expect(onQuantityChange).toHaveBeenCalledTimes(2);
      });
    });
  });
});

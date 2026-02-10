import { render, screen, act, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import ToastProvider from './UndoToast';
import { useToast } from './useToast';

// Test component that exposes toast controls
function TestComponent() {
  const { showToast, dismissAll } = useToast();

  return (
    <div>
      <button onClick={() => showToast({ message: 'Test toast' })} data-testid="show-basic-toast">
        Show Basic Toast
      </button>
      <button
        onClick={() =>
          showToast({
            message: 'Toast with undo',
            onUndo: () => console.log('undo'),
          })
        }
        data-testid="show-undo-toast"
      >
        Show Undo Toast
      </button>
      <button
        onClick={() => showToast({ message: 'Quick toast', duration: 100 })}
        data-testid="show-quick-toast"
      >
        Show Quick Toast
      </button>
      <button onClick={dismissAll} data-testid="dismiss-all">
        Dismiss All
      </button>
    </div>
  );
}

// Wrapper component for testing
function TestWrapper({ maxToasts }: { maxToasts?: number }) {
  return (
    <ToastProvider maxToasts={maxToasts}>
      <TestComponent />
    </ToastProvider>
  );
}

describe('UndoToast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('ToastProvider', () => {
    it('renders children', () => {
      render(
        <ToastProvider>
          <div data-testid="child">Child content</div>
        </ToastProvider>
      );

      expect(screen.getByTestId('child')).toHaveTextContent('Child content');
    });

    it('initially shows no toasts', () => {
      render(<TestWrapper />);

      expect(screen.queryByTestId('toast-container')).not.toBeInTheDocument();
    });
  });

  describe('useToast hook', () => {
    it('throws error when used outside provider', () => {
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        render(<TestComponent />);
      }).toThrow('useToast must be used within a ToastProvider');

      consoleError.mockRestore();
    });
  });

  describe('showToast', () => {
    it('shows a toast with message', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.getByTestId('toast-container')).toBeInTheDocument();
      expect(screen.getByTestId('toast-message')).toHaveTextContent('Test toast');
    });

    it('shows undo button when onUndo is provided', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-undo-toast'));

      expect(screen.getByTestId('toast-undo-button')).toBeInTheDocument();
    });

    it('does not show undo button when onUndo is not provided', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.queryByTestId('toast-undo-button')).not.toBeInTheDocument();
    });

    it('stacks multiple toasts', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));
      fireEvent.click(screen.getByTestId('show-undo-toast'));

      const toasts = screen.getAllByTestId('toast-item');
      expect(toasts).toHaveLength(2);
    });

    it('limits toasts to maxToasts', () => {
      render(<TestWrapper maxToasts={2} />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));
      fireEvent.click(screen.getByTestId('show-basic-toast'));
      fireEvent.click(screen.getByTestId('show-basic-toast'));

      const toasts = screen.getAllByTestId('toast-item');
      expect(toasts).toHaveLength(2);
    });
  });

  describe('auto-dismiss', () => {
    it('auto-dismisses after default duration (5 seconds)', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));
      expect(screen.getByTestId('toast-item')).toBeInTheDocument();

      // Fast forward 5 seconds + exit animation (150ms)
      act(() => {
        vi.advanceTimersByTime(5150);
      });

      expect(screen.queryByTestId('toast-item')).not.toBeInTheDocument();
    });

    it('auto-dismisses after custom duration', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-quick-toast'));
      expect(screen.getByTestId('toast-item')).toBeInTheDocument();

      // Fast forward 100ms (custom duration) + exit animation (150ms)
      act(() => {
        vi.advanceTimersByTime(250);
      });

      expect(screen.queryByTestId('toast-item')).not.toBeInTheDocument();
    });
  });

  describe('dismissToast', () => {
    it('dismisses toast by clicking dismiss button', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));
      expect(screen.getByTestId('toast-item')).toBeInTheDocument();

      fireEvent.click(screen.getByTestId('toast-dismiss-button'));

      // Wait for exit animation (150ms)
      act(() => {
        vi.advanceTimersByTime(150);
      });

      expect(screen.queryByTestId('toast-item')).not.toBeInTheDocument();
    });
  });

  describe('dismissAll', () => {
    it('dismisses all toasts', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));
      fireEvent.click(screen.getByTestId('show-undo-toast'));

      expect(screen.getAllByTestId('toast-item')).toHaveLength(2);

      fireEvent.click(screen.getByTestId('dismiss-all'));

      expect(screen.queryByTestId('toast-container')).not.toBeInTheDocument();
    });
  });

  describe('undo functionality', () => {
    it('calls onUndo callback when undo is clicked', () => {
      const onUndo = vi.fn();

      function TestWithUndo() {
        const { showToast } = useToast();
        return (
          <button
            onClick={() => showToast({ message: 'Deleted item', onUndo })}
            data-testid="show-toast"
          >
            Show
          </button>
        );
      }

      render(
        <ToastProvider>
          <TestWithUndo />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-toast'));
      fireEvent.click(screen.getByTestId('toast-undo-button'));

      expect(onUndo).toHaveBeenCalledOnce();
    });

    it('dismisses toast after clicking undo', () => {
      const onUndo = vi.fn();

      function TestWithUndo() {
        const { showToast } = useToast();
        return (
          <button
            onClick={() => showToast({ message: 'Deleted item', onUndo })}
            data-testid="show-toast"
          >
            Show
          </button>
        );
      }

      render(
        <ToastProvider>
          <TestWithUndo />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-toast'));
      fireEvent.click(screen.getByTestId('toast-undo-button'));

      // Wait for exit animation (150ms)
      act(() => {
        vi.advanceTimersByTime(150);
      });

      expect(screen.queryByTestId('toast-item')).not.toBeInTheDocument();
    });
  });

  describe('accessibility', () => {
    it('has role="alert" for toast items', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    it('has aria-live="polite" for toast items', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.getByTestId('toast-item')).toHaveAttribute('aria-live', 'polite');
    });

    it('dismiss button has aria-label', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.getByTestId('toast-dismiss-button')).toHaveAttribute('aria-label', 'Dismiss');
    });

    it('container has aria-label', () => {
      render(<TestWrapper />);

      fireEvent.click(screen.getByTestId('show-basic-toast'));

      expect(screen.getByTestId('toast-container')).toHaveAttribute('aria-label', 'Notifications');
    });
  });
});

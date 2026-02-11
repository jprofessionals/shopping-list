import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, createTestStore } from '../../test/testUtils';
import AutocompleteInput, { type ItemSuggestion } from './AutocompleteInput';

const mockSuggestions: ItemSuggestion[] = [
  { name: 'Potatoes', typicalQuantity: 1, typicalUnit: 'kg', useCount: 15 },
  { name: 'Potato Chips', typicalQuantity: 2, typicalUnit: 'bags', useCount: 5 },
  { name: 'Potato Salad', typicalQuantity: 1, typicalUnit: null, useCount: 3 },
];

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
      items: [],
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
  });

describe('AutocompleteInput', () => {
  let mockFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockFetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ suggestions: mockSuggestions }),
      })
    );
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('renders input with placeholder', () => {
    render(
      <AutocompleteInput
        value=""
        onChange={() => {}}
        onSuggestionSelect={() => {}}
        placeholder="Search items"
      />,
      { store: getDefaultStore() }
    );

    expect(screen.getByPlaceholderText('Search items')).toBeInTheDocument();
  });

  it('calls onChange when typing', async () => {
    const onChange = vi.fn();
    render(<AutocompleteInput value="" onChange={onChange} onSuggestionSelect={() => {}} />, {
      store: getDefaultStore(),
    });

    const input = screen.getByRole('combobox');
    fireEvent.change(input, { target: { value: 'pot' } });

    expect(onChange).toHaveBeenCalledWith('pot');
  });

  it('fetches suggestions after debounce delay', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    // Rerender with value to simulate typing
    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    // Should not fetch immediately
    expect(mockFetch).not.toHaveBeenCalled();

    // Advance timers past debounce delay
    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/items/suggestions?q=pot&limit=10',
        expect.anything()
      );
    });
  });

  it('displays suggestions dropdown', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    // Use getAllByRole to get options and check their content
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(3);
    expect(options[0]).toHaveTextContent('Potatoes');
    expect(options[1]).toHaveTextContent('Potato Chips');
    expect(options[2]).toHaveTextContent('Potato Salad');
  });

  it('shows typical quantity and unit in suggestions', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByText('(1 kg)')).toBeInTheDocument();
      expect(screen.getByText('(2 bags)')).toBeInTheDocument();
    });
  });

  it('shows use count for frequently used items', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByText('Used 15 times')).toBeInTheDocument();
      expect(screen.getByText('Used 5 times')).toBeInTheDocument();
      expect(screen.getByText('Used 3 times')).toBeInTheDocument();
    });
  });

  it('navigates suggestions with arrow keys', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    const input = screen.getByRole('combobox');
    const options = screen.getAllByRole('option');

    // Press ArrowDown
    fireEvent.keyDown(input, { key: 'ArrowDown' });

    expect(options[0]).toHaveAttribute('aria-selected', 'true');

    // Press ArrowDown again
    fireEvent.keyDown(input, { key: 'ArrowDown' });

    expect(options[1]).toHaveAttribute('aria-selected', 'true');
    expect(options[0]).toHaveAttribute('aria-selected', 'false');

    // Press ArrowUp
    fireEvent.keyDown(input, { key: 'ArrowUp' });

    expect(options[0]).toHaveAttribute('aria-selected', 'true');
    expect(options[1]).toHaveAttribute('aria-selected', 'false');
  });

  it('wraps around when navigating past last item', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    const input = screen.getByRole('combobox');
    const options = screen.getAllByRole('option');

    // Navigate to the last item
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });

    // Press ArrowDown again to wrap to first item
    fireEvent.keyDown(input, { key: 'ArrowDown' });

    expect(options[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('selects suggestion on Enter key', async () => {
    const onSuggestionSelect = vi.fn();
    const onChange = vi.fn();

    const { rerender } = render(
      <AutocompleteInput value="" onChange={onChange} onSuggestionSelect={onSuggestionSelect} />,
      { store: getDefaultStore() }
    );

    rerender(
      <AutocompleteInput value="pot" onChange={onChange} onSuggestionSelect={onSuggestionSelect} />
    );

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    const input = screen.getByRole('combobox');

    // Select first item
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(onSuggestionSelect).toHaveBeenCalledWith(mockSuggestions[0]);
    expect(onChange).toHaveBeenCalledWith('Potatoes');
  });

  it('closes dropdown on Escape key', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    const input = screen.getByRole('combobox');
    fireEvent.keyDown(input, { key: 'Escape' });

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('selects suggestion on click', async () => {
    const onSuggestionSelect = vi.fn();
    const onChange = vi.fn();

    const { rerender } = render(
      <AutocompleteInput value="" onChange={onChange} onSuggestionSelect={onSuggestionSelect} />,
      { store: getDefaultStore() }
    );

    rerender(
      <AutocompleteInput value="pot" onChange={onChange} onSuggestionSelect={onSuggestionSelect} />
    );

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    // Click on the second option (Potato Chips)
    const options = screen.getAllByRole('option');
    fireEvent.click(options[1]);

    expect(onSuggestionSelect).toHaveBeenCalledWith(mockSuggestions[1]);
    expect(onChange).toHaveBeenCalledWith('Potato Chips');
  });

  it('closes dropdown on click outside', async () => {
    const { rerender } = render(
      <div>
        <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />
        <button>Outside Button</button>
      </div>,
      { store: getDefaultStore() }
    );

    rerender(
      <div>
        <AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />
        <button>Outside Button</button>
      </div>
    );

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    fireEvent.mouseDown(screen.getByText('Outside Button'));

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('does not fetch suggestions for short queries', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="p" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('handles API errors gracefully', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'));

    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    // Should not crash and should not show dropdown
    await waitFor(() => {
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });
  });

  it('is disabled when disabled prop is true', () => {
    render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} disabled />,
      { store: getDefaultStore() }
    );

    expect(screen.getByRole('combobox')).toBeDisabled();
  });

  it('highlights matching text in suggestions', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    // Check for highlighted text (has font-semibold class)
    const highlightedSpans = document.querySelectorAll('.font-semibold.text-indigo-600');
    expect(highlightedSpans.length).toBeGreaterThan(0);
  });

  it('has proper ARIA attributes', async () => {
    const { rerender } = render(
      <AutocompleteInput value="" onChange={() => {}} onSuggestionSelect={() => {}} />,
      { store: getDefaultStore() }
    );

    const input = screen.getByRole('combobox');
    expect(input).toHaveAttribute('aria-expanded', 'false');
    expect(input).toHaveAttribute('aria-haspopup', 'listbox');

    rerender(<AutocompleteInput value="pot" onChange={() => {}} onSuggestionSelect={() => {}} />);

    await act(async () => {
      vi.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(input).toHaveAttribute('aria-expanded', 'true');
    });

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(input).toHaveAttribute('aria-activedescendant', 'suggestion-0');
  });

  describe('Smart Parsing Preview', () => {
    it('does not show preview when smartParsingEnabled is false', () => {
      render(
        <AutocompleteInput
          value="2kg potatoes"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={false}
        />,
        { store: getDefaultStore() }
      );

      expect(screen.queryByTestId('parsing-preview')).not.toBeInTheDocument();
    });

    it('shows preview when smartParsingEnabled is true', () => {
      render(
        <AutocompleteInput
          value="2kg potatoes"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      const preview = screen.getByTestId('parsing-preview');
      expect(preview).toBeInTheDocument();
      expect(preview).toHaveTextContent('Preview:');
      expect(preview).toHaveTextContent('Potatoes - 2 kg');
    });

    it('shows preview with quantity only (no unit)', () => {
      render(
        <AutocompleteInput
          value="3 apples"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      const preview = screen.getByTestId('parsing-preview');
      expect(preview).toHaveTextContent('Apples - 3');
    });

    it('shows preview with name only (no quantity or unit)', () => {
      render(
        <AutocompleteInput
          value="bread"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      const preview = screen.getByTestId('parsing-preview');
      expect(preview).toHaveTextContent('Bread');
    });

    it('shows preview for quantity at end pattern', () => {
      render(
        <AutocompleteInput
          value="milk 2l"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      const preview = screen.getByTestId('parsing-preview');
      expect(preview).toHaveTextContent('Milk - 2 l');
    });

    it('does not show preview for empty input', () => {
      render(
        <AutocompleteInput
          value=""
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      expect(screen.queryByTestId('parsing-preview')).not.toBeInTheDocument();
    });

    it('does not show preview for whitespace-only input', () => {
      render(
        <AutocompleteInput
          value="   "
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      expect(screen.queryByTestId('parsing-preview')).not.toBeInTheDocument();
    });

    it('calls onParsedChange when parsing result changes', () => {
      const onParsedChange = vi.fn();

      render(
        <AutocompleteInput
          value="2kg potatoes"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
          onParsedChange={onParsedChange}
        />,
        { store: getDefaultStore() }
      );

      expect(onParsedChange).toHaveBeenCalledWith({
        name: 'Potatoes',
        quantity: 2,
        unit: 'kg',
      });
    });

    it('updates preview when value changes', () => {
      const { rerender } = render(
        <AutocompleteInput
          value="2kg potatoes"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      expect(screen.getByTestId('parsing-preview')).toHaveTextContent('Potatoes - 2 kg');

      rerender(
        <AutocompleteInput
          value="3 apples"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />
      );

      expect(screen.getByTestId('parsing-preview')).toHaveTextContent('Apples - 3');
    });

    it('has aria-live attribute for accessibility', () => {
      render(
        <AutocompleteInput
          value="2kg potatoes"
          onChange={() => {}}
          onSuggestionSelect={() => {}}
          smartParsingEnabled={true}
        />,
        { store: getDefaultStore() }
      );

      const preview = screen.getByTestId('parsing-preview');
      expect(preview).toHaveAttribute('aria-live', 'polite');
    });
  });
});

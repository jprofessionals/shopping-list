import { useState, useEffect, useRef, useCallback, useMemo, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounce } from '../../hooks/useDebounce';
import { useAppSelector } from '../../store/hooks';
import { parseItemInput, formatParsedItem, type ParsedItem } from '../../utils/parseItemInput';

export interface ItemSuggestion {
  name: string;
  typicalQuantity: number;
  typicalUnit: string | null;
  useCount: number;
}

interface AutocompleteInputProps {
  value: string;
  onChange: (value: string) => void;
  onSuggestionSelect: (suggestion: ItemSuggestion) => void;
  onParsedChange?: (parsed: ParsedItem) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  smartParsingEnabled?: boolean;
}

export default function AutocompleteInput({
  value,
  onChange,
  onSuggestionSelect,
  onParsedChange,
  placeholder = 'Item name',
  className = '',
  disabled = false,
  smartParsingEnabled = false,
}: AutocompleteInputProps) {
  const { t } = useTranslation();
  const [suggestions, setSuggestions] = useState<ItemSuggestion[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [isLoading, setIsLoading] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const token = useAppSelector((state) => state.auth.token);
  const debouncedValue = useDebounce(value, 300);

  // Parse the input value when smart parsing is enabled
  const parsedItem = useMemo(() => {
    if (!smartParsingEnabled || !value.trim()) {
      return null;
    }
    return parseItemInput(value);
  }, [value, smartParsingEnabled]);

  // Notify parent when parsed result changes
  useEffect(() => {
    if (parsedItem && onParsedChange) {
      onParsedChange(parsedItem);
    }
  }, [parsedItem, onParsedChange]);

  // Fetch suggestions when debounced value changes
  useEffect(() => {
    const fetchSuggestions = async () => {
      if (!debouncedValue.trim() || debouncedValue.length < 2 || !token) {
        setSuggestions([]);
        setIsOpen(false);
        return;
      }

      setIsLoading(true);
      try {
        const response = await fetch(
          `http://localhost:8080/api/items/suggestions?q=${encodeURIComponent(debouncedValue)}&limit=10`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );

        if (response.ok) {
          const data = await response.json();
          setSuggestions(data.suggestions || []);
          setIsOpen(data.suggestions?.length > 0);
          setHighlightedIndex(-1);
        }
      } catch (error) {
        console.error('Failed to fetch suggestions:', error);
        setSuggestions([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchSuggestions();
  }, [debouncedValue, token]);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Scroll highlighted item into view
  useEffect(() => {
    if (highlightedIndex >= 0 && listRef.current) {
      const items = listRef.current.querySelectorAll('li');
      const item = items[highlightedIndex];
      if (item && typeof item.scrollIntoView === 'function') {
        item.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [highlightedIndex]);

  const handleSelect = useCallback(
    (suggestion: ItemSuggestion) => {
      onSuggestionSelect(suggestion);
      onChange(suggestion.name);
      setIsOpen(false);
      setHighlightedIndex(-1);
      inputRef.current?.focus();
    },
    [onChange, onSuggestionSelect]
  );

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!isOpen || suggestions.length === 0) {
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setHighlightedIndex((prev) => (prev < suggestions.length - 1 ? prev + 1 : 0));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : suggestions.length - 1));
        break;
      case 'Enter':
        if (highlightedIndex >= 0) {
          e.preventDefault();
          handleSelect(suggestions[highlightedIndex]);
        }
        break;
      case 'Escape':
        e.preventDefault();
        setIsOpen(false);
        setHighlightedIndex(-1);
        break;
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
    if (e.target.value.length >= 2) {
      setIsOpen(true);
    }
  };

  const handleFocus = () => {
    if (suggestions.length > 0 && value.length >= 2) {
      setIsOpen(true);
    }
  };

  const formatSuggestion = (suggestion: ItemSuggestion) => {
    const parts = [];
    if (suggestion.typicalQuantity) {
      parts.push(suggestion.typicalQuantity.toString());
    }
    if (suggestion.typicalUnit) {
      parts.push(suggestion.typicalUnit);
    }
    return parts.length > 0 ? `(${parts.join(' ')})` : '';
  };

  const highlightMatch = (text: string, query: string) => {
    if (!query) return text;

    const lowerText = text.toLowerCase();
    const lowerQuery = query.toLowerCase();
    const index = lowerText.indexOf(lowerQuery);

    if (index === -1) return text;

    return (
      <>
        {text.slice(0, index)}
        <span className="font-semibold text-indigo-600">
          {text.slice(index, index + query.length)}
        </span>
        {text.slice(index + query.length)}
      </>
    );
  };

  return (
    <div ref={containerRef} className="relative flex-1">
      <input
        ref={inputRef}
        type="text"
        value={value}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onFocus={handleFocus}
        placeholder={placeholder}
        disabled={disabled}
        className={`w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:cursor-not-allowed disabled:bg-gray-100 dark:bg-gray-700 dark:border-gray-600 dark:text-white ${className}`}
        autoComplete="off"
        role="combobox"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-controls="suggestions-listbox"
        aria-activedescendant={highlightedIndex >= 0 ? `suggestion-${highlightedIndex}` : undefined}
      />

      {isLoading && (
        <div className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2">
          <svg
            className="h-4 w-4 animate-spin text-gray-400"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
        </div>
      )}

      {isOpen && suggestions.length > 0 && (
        <ul
          ref={listRef}
          id="suggestions-listbox"
          role="listbox"
          className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 text-sm shadow-lg ring-1 ring-black ring-opacity-5 dark:bg-gray-800 dark:ring-gray-700"
        >
          {suggestions.map((suggestion, index) => (
            <li
              key={`${suggestion.name}-${index}`}
              id={`suggestion-${index}`}
              role="option"
              aria-selected={highlightedIndex === index}
              className={`cursor-pointer px-3 py-2 ${
                highlightedIndex === index
                  ? 'bg-indigo-50 text-indigo-900 dark:bg-indigo-900/30 dark:text-indigo-200'
                  : 'text-gray-900 dark:text-gray-100'
              } hover:bg-indigo-50 dark:hover:bg-indigo-900/30`}
              onClick={() => handleSelect(suggestion)}
              onMouseEnter={() => setHighlightedIndex(index)}
            >
              <div className="flex items-center justify-between">
                <span>{highlightMatch(suggestion.name, value)}</span>
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  {formatSuggestion(suggestion)}
                </span>
              </div>
              {suggestion.useCount > 1 && (
                <div className="text-xs text-gray-400 dark:text-gray-500">
                  Used {suggestion.useCount} times
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* Smart parsing preview */}
      {smartParsingEnabled && parsedItem && parsedItem.name && (
        <div
          className="mt-1 text-xs text-gray-500 dark:text-gray-400"
          data-testid="parsing-preview"
          aria-live="polite"
        >
          <span className="font-medium text-gray-600 dark:text-gray-300">
            {t('common.preview')}
          </span>{' '}
          {formatParsedItem(parsedItem)}
        </div>
      )}
    </div>
  );
}

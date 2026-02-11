import { useState, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { type ListItem } from '../../store/listsSlice';

interface ListItemRowProps {
  item: ListItem;
  onToggleCheck: () => void;
  onDelete: () => void;
  onQuantityChange?: (newQuantity: number) => void;
  disabled?: boolean;
  isFocused?: boolean;
}

export default function ListItemRow({
  item,
  onToggleCheck,
  onDelete,
  onQuantityChange,
  disabled = false,
  isFocused = false,
}: ListItemRowProps) {
  const { t } = useTranslation();
  const [isStepperOpen, setIsStepperOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const isTouchDevice = useRef(false);

  const canOpenStepper = !disabled && !!onQuantityChange;

  const handleMouseEnter = () => {
    if (isTouchDevice.current || !canOpenStepper) return;
    setIsStepperOpen(true);
  };

  const handleMouseLeave = () => {
    if (isTouchDevice.current) return;
    setIsStepperOpen(false);
  };

  const handleTouchStart = () => {
    isTouchDevice.current = true;
  };

  const handleRowClick = (e: React.MouseEvent<HTMLLIElement>) => {
    if (!isTouchDevice.current || !canOpenStepper) return;
    // Don't toggle if user clicked checkbox, delete button, or stepper buttons
    const target = e.target as HTMLElement;
    if (
      target.closest(
        'input[type="checkbox"], [aria-label*="delete"], [data-testid="quantity-decrement"], [data-testid="quantity-increment"]'
      )
    ) {
      return;
    }
    setIsStepperOpen((prev) => !prev);
  };

  const handleQuantityClick = () => {
    if (disabled || !onQuantityChange) return;
    setIsStepperOpen(!isStepperOpen);
  };

  const handleIncrement = useCallback(async () => {
    if (!onQuantityChange || isUpdating) return;
    setIsUpdating(true);
    try {
      await onQuantityChange(item.quantity + 1);
    } finally {
      setIsUpdating(false);
    }
  }, [onQuantityChange, item.quantity, isUpdating]);

  const handleDecrement = useCallback(async () => {
    if (!onQuantityChange || isUpdating || item.quantity <= 1) return;
    setIsUpdating(true);
    try {
      await onQuantityChange(item.quantity - 1);
    } finally {
      setIsUpdating(false);
    }
  }, [onQuantityChange, item.quantity, isUpdating]);

  const canDecrement = item.quantity > 1;

  return (
    <li
      className={`flex items-center justify-between py-3 transition-colors ${
        isFocused ? 'bg-indigo-50 -mx-2 px-2 rounded dark:bg-indigo-900/30' : ''
      }`}
      data-testid={isFocused ? 'focused-item' : undefined}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onTouchStart={handleTouchStart}
      onClick={handleRowClick}
    >
      <div className="flex items-center gap-3">
        <input
          type="checkbox"
          checked={item.isChecked}
          onChange={onToggleCheck}
          disabled={disabled}
          className="h-5 w-5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
        />
        <span
          className={
            item.isChecked
              ? 'line-through text-gray-500 dark:text-gray-400'
              : 'text-gray-900 dark:text-white'
          }
        >
          {item.name}
        </span>

        {/* Recurring item badge */}
        {item.recurringItemId && (
          <svg
            className="h-4 w-4 flex-shrink-0 text-indigo-400 dark:text-indigo-300"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-label={t('recurring.title')}
            data-testid="recurring-badge"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
        )}

        {/* Quantity display with stepper */}
        <div className="relative flex items-center">
          <button
            type="button"
            onClick={handleQuantityClick}
            disabled={disabled || !onQuantityChange}
            className={`text-sm transition-colors duration-150 ${
              isStepperOpen
                ? 'font-medium text-indigo-600'
                : 'text-gray-500 hover:text-indigo-600 dark:text-gray-400'
            } ${disabled || !onQuantityChange ? 'cursor-default' : 'cursor-pointer'}`}
            aria-label={t('listItem.editQuantity')}
            aria-expanded={isStepperOpen}
            data-testid="quantity-display"
          >
            {item.quantity} {item.unit}
          </button>

          {/* Stepper controls */}
          <div
            className={`ml-2 flex items-center gap-1 overflow-hidden transition-all duration-200 ease-in-out ${
              isStepperOpen ? 'max-w-24 opacity-100' : 'max-w-0 opacity-0'
            }`}
            data-testid="quantity-stepper"
          >
            <button
              type="button"
              onClick={handleDecrement}
              disabled={!canDecrement || isUpdating}
              className={`flex h-7 w-7 items-center justify-center rounded-full border transition-colors duration-150 ${
                canDecrement && !isUpdating
                  ? 'border-gray-300 bg-white text-gray-700 hover:bg-gray-100 hover:border-gray-400 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600'
                  : 'border-gray-200 bg-gray-50 text-gray-300 cursor-not-allowed dark:border-gray-700 dark:bg-gray-800 dark:text-gray-500'
              }`}
              aria-label={t('listItem.decreaseQuantity')}
              data-testid="quantity-decrement"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 12H4" />
              </svg>
            </button>
            <button
              type="button"
              onClick={handleIncrement}
              disabled={isUpdating}
              className={`flex h-7 w-7 items-center justify-center rounded-full border transition-colors duration-150 ${
                !isUpdating
                  ? 'border-gray-300 bg-white text-gray-700 hover:bg-gray-100 hover:border-gray-400 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600'
                  : 'border-gray-200 bg-gray-50 text-gray-300 cursor-not-allowed dark:border-gray-700 dark:bg-gray-800 dark:text-gray-500'
              }`}
              aria-label={t('listItem.increaseQuantity')}
              data-testid="quantity-increment"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 4v16m8-8H4"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>
      {!disabled && (
        <button
          onClick={onDelete}
          className="text-red-600 hover:text-red-800"
          aria-label={t('listItem.deleteItem')}
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
            />
          </svg>
        </button>
      )}
    </li>
  );
}

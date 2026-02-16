import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import {
  addItem,
  addItems,
  toggleItemCheck,
  updateItem,
  removeItem,
  removeItems,
  setItems,
  type ListItem,
} from '../../store/listsSlice';
import { EmptyState, ErrorAlert, useToast } from '../common';
import ListItemRow from './ListItemRow';
import AutocompleteInput, { type ItemSuggestion } from './AutocompleteInput';
import { parseItemInput } from '../../utils/parseItemInput';
import { apiFetch, API_BASE } from '../../services/api';
import CommentFeed from '../comments/CommentFeed';

const UNDO_DURATION = 30000; // 30 seconds for undo
const PULL_THRESHOLD = 80; // pixels to trigger refresh

export type SharePermission = 'READ' | 'CHECK' | 'WRITE';

function sharedFetch(shareToken: string, path: string, options?: RequestInit) {
  return fetch(`${API_BASE}/shared/${shareToken}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
}

interface ShoppingListViewProps {
  listId: string;
  onBack: () => void;
  onShareClick?: () => void;
  onPinToggle?: () => void;
  onDeleteClick?: () => void;
  // Props for shared-link access:
  shareToken?: string;
  permission?: SharePermission;
  sharedListName?: string;
  sharedItems?: ListItem[];
  onSharedItemsChange?: (items: ListItem[]) => void;
}

export default function ShoppingListView({
  listId,
  onBack,
  onShareClick,
  onPinToggle,
  onDeleteClick,
  shareToken,
  permission: permissionProp,
  sharedListName,
  sharedItems,
  onSharedItemsChange,
}: ShoppingListViewProps) {
  const { t } = useTranslation();
  const [itemName, setItemName] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [unit, setUnit] = useState('');
  const [isClearingChecked, setIsClearingChecked] = useState(false);
  const [focusedItemIndex, setFocusedItemIndex] = useState(-1);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [smartParsingEnabled, setSmartParsingEnabled] = useState(false);

  // Default permission to WRITE when not in shared mode
  const permission = permissionProp ?? 'WRITE';
  const isShared = !!shareToken;
  const canWrite = permission === 'WRITE';

  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);
  const list = useAppSelector((state) => state.lists.items.find((l) => l.id === listId));
  const reduxItems = useAppSelector((state) => state.lists.currentListItems);
  const items = useMemo(
    () => (isShared ? sharedItems || [] : reduxItems),
    [isShared, sharedItems, reduxItems]
  );
  const listName = isShared ? sharedListName : list?.name;
  const { showToast } = useToast();
  const deletedItemsRef = useRef<ListItem[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const touchStartY = useRef(0);

  // Fetch smart parsing preference (only for authenticated mode)
  useEffect(() => {
    if (isShared || !token) return;
    apiFetch('/preferences')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data?.smartParsingEnabled != null) {
          setSmartParsingEnabled(data.smartParsingEnabled);
        }
      })
      .catch(() => {});
  }, [isShared, token]);

  const uncheckedItems = useMemo(() => items.filter((item) => !item.isChecked), [items]);
  const checkedItems = useMemo(() => items.filter((item) => item.isChecked), [items]);
  const allItems = useMemo(
    () => [...uncheckedItems, ...checkedItems],
    [uncheckedItems, checkedItems]
  );

  // Pull-to-refresh handlers (disabled for shared mode)
  const handleRefresh = useCallback(async () => {
    if (isShared || !token || isRefreshing) return;
    setIsRefreshing(true);
    try {
      const response = await apiFetch(`/lists/${listId}/items`);
      if (response.ok) {
        const refreshedItems: ListItem[] = await response.json();
        dispatch(setItems(refreshedItems));
      }
    } catch (err) {
      console.error('Failed to refresh:', err);
    } finally {
      setIsRefreshing(false);
      setPullDistance(0);
    }
  }, [isShared, token, listId, dispatch, isRefreshing]);

  const handleTouchStart = useCallback(
    (e: React.TouchEvent) => {
      if (isShared) return;
      if (containerRef.current?.scrollTop === 0) {
        touchStartY.current = e.touches[0].clientY;
      }
    },
    [isShared]
  );

  const handleTouchMove = useCallback(
    (e: React.TouchEvent) => {
      if (isShared) return;
      if (touchStartY.current === 0) return;
      const currentY = e.touches[0].clientY;
      const distance = Math.max(0, currentY - touchStartY.current);
      setPullDistance(Math.min(distance, PULL_THRESHOLD * 1.5));
    },
    [isShared]
  );

  const handleTouchEnd = useCallback(() => {
    if (isShared) return;
    if (pullDistance >= PULL_THRESHOLD) {
      handleRefresh();
    } else {
      setPullDistance(0);
    }
    touchStartY.current = 0;
  }, [isShared, pullDistance, handleRefresh]);

  const handleSuggestionSelect = useCallback((suggestion: ItemSuggestion) => {
    setItemName(suggestion.name);
    if (suggestion.typicalQuantity) {
      setQuantity(suggestion.typicalQuantity.toString());
    }
    if (suggestion.typicalUnit) {
      setUnit(suggestion.typicalUnit);
    }
  }, []);

  const handleAddItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!itemName.trim()) return;

    let finalName = itemName.trim();
    let finalQuantity = parseInt(quantity) || 1;
    let finalUnit: string | null = unit.trim() || null;

    if (smartParsingEnabled) {
      const parsed = parseItemInput(itemName);
      finalName = parsed.name;
      if (parsed.quantity != null) finalQuantity = parsed.quantity;
      if (parsed.unit != null) finalUnit = parsed.unit;
    }

    const body = JSON.stringify({
      name: finalName,
      quantity: finalQuantity,
      unit: finalUnit,
    });

    if (isShared && shareToken) {
      try {
        const response = await sharedFetch(shareToken, '/items', {
          method: 'POST',
          body,
        });
        if (!response.ok) throw new Error('Failed to add item');
        const newItem: ListItem = await response.json();
        onSharedItemsChange?.([...(sharedItems || []), newItem]);
        setItemName('');
        setQuantity('1');
        setUnit('');
      } catch (err) {
        console.error('Failed to add item:', err);
      }
      return;
    }

    if (!token) return;
    try {
      const response = await apiFetch(`/lists/${listId}/items`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
      });

      if (!response.ok) throw new Error('Failed to add item');

      const newItem: ListItem = await response.json();
      dispatch(addItem(newItem));
      setItemName('');
      setQuantity('1');
      setUnit('');
    } catch (err) {
      console.error('Failed to add item:', err);
    }
  };

  const handleToggleCheck = useCallback(
    async (item: ListItem) => {
      if (isShared && shareToken) {
        try {
          const response = await sharedFetch(shareToken, `/items/${item.id}/check`, {
            method: 'POST',
            body: JSON.stringify({ isChecked: !item.isChecked }),
          });
          if (!response.ok) throw new Error('Failed to toggle item');
          const data = await response.json();
          onSharedItemsChange?.(
            (sharedItems || []).map((i) =>
              i.id === item.id
                ? { ...i, isChecked: data.isChecked, checkedByName: data.checkedByName }
                : i
            )
          );
        } catch (err) {
          console.error('Failed to toggle item:', err);
        }
        return;
      }

      if (!token) return;

      try {
        const response = await apiFetch(`/lists/${listId}/items/${item.id}/check`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ isChecked: !item.isChecked }),
        });

        if (!response.ok) throw new Error('Failed to toggle item');

        const data = await response.json();
        dispatch(
          toggleItemCheck({
            id: item.id,
            isChecked: data.isChecked,
            checkedByName: data.checkedByName,
          })
        );
      } catch (err) {
        console.error('Failed to toggle item:', err);
      }
    },
    [isShared, shareToken, sharedItems, onSharedItemsChange, token, listId, dispatch]
  );

  const handleRemoveItem = useCallback(
    async (itemId: string) => {
      if (isShared && shareToken) {
        try {
          const response = await sharedFetch(shareToken, `/items/${itemId}`, {
            method: 'DELETE',
          });
          if (!response.ok) throw new Error('Failed to delete item');
          onSharedItemsChange?.((sharedItems || []).filter((i) => i.id !== itemId));
        } catch (err) {
          console.error('Failed to delete item:', err);
        }
        return;
      }

      if (!token) return;

      try {
        const response = await apiFetch(`/lists/${listId}/items/${itemId}`, {
          method: 'DELETE',
        });

        if (!response.ok) throw new Error('Failed to delete item');

        dispatch(removeItem(itemId));
      } catch (err) {
        console.error('Failed to delete item:', err);
      }
    },
    [isShared, shareToken, sharedItems, onSharedItemsChange, token, listId, dispatch]
  );

  const handleQuantityChange = useCallback(
    async (item: ListItem, newQuantity: number) => {
      if (isShared && shareToken) {
        try {
          const response = await sharedFetch(shareToken, `/items/${item.id}`, {
            method: 'PATCH',
            body: JSON.stringify({
              name: item.name,
              quantity: newQuantity,
              unit: item.unit,
            }),
          });
          if (!response.ok) throw new Error('Failed to update item quantity');
          const updatedItem: ListItem = await response.json();
          onSharedItemsChange?.(
            (sharedItems || []).map((i) =>
              i.id === item.id ? { ...i, quantity: updatedItem.quantity } : i
            )
          );
        } catch (err) {
          console.error('Failed to update item quantity:', err);
        }
        return;
      }

      if (!token) return;

      try {
        const response = await apiFetch(`/lists/${listId}/items/${item.id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: item.name,
            quantity: newQuantity,
            unit: item.unit,
          }),
        });

        if (!response.ok) throw new Error('Failed to update item quantity');

        const updatedItem: ListItem = await response.json();
        dispatch(updateItem({ id: item.id, quantity: updatedItem.quantity }));
      } catch (err) {
        console.error('Failed to update item quantity:', err);
      }
    },
    [isShared, shareToken, sharedItems, onSharedItemsChange, token, listId, dispatch]
  );

  const handleUndoClearChecked = useCallback(async () => {
    if (isShared && shareToken) {
      if (deletedItemsRef.current.length === 0) return;
      try {
        const itemsToRestore = deletedItemsRef.current.map((item) => ({
          name: item.name,
          quantity: item.quantity,
          unit: item.unit,
        }));
        const response = await sharedFetch(shareToken, '/items/bulk', {
          method: 'POST',
          body: JSON.stringify({ items: itemsToRestore }),
        });
        if (!response.ok) throw new Error('Failed to restore items');
        const restoredItems: ListItem[] = await response.json();
        onSharedItemsChange?.([...(sharedItems || []), ...restoredItems]);
        deletedItemsRef.current = [];
      } catch (err) {
        console.error('Failed to restore items:', err);
      }
      return;
    }

    if (!token || deletedItemsRef.current.length === 0) return;

    try {
      const itemsToRestore = deletedItemsRef.current.map((item) => ({
        name: item.name,
        quantity: item.quantity,
        unit: item.unit,
      }));

      const response = await apiFetch(`/lists/${listId}/items/bulk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items: itemsToRestore }),
      });

      if (!response.ok) throw new Error('Failed to restore items');

      const restoredItems: ListItem[] = await response.json();
      dispatch(addItems(restoredItems));
      deletedItemsRef.current = [];
    } catch (err) {
      console.error('Failed to restore items:', err);
    }
  }, [isShared, shareToken, sharedItems, onSharedItemsChange, token, listId, dispatch]);

  const handleClearChecked = async () => {
    if (checkedItems.length === 0) return;

    if (isShared && shareToken) {
      setIsClearingChecked(true);
      try {
        const response = await sharedFetch(shareToken, '/items/checked', {
          method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to clear checked items');
        const data: { deletedItemIds: string[] } = await response.json();

        deletedItemsRef.current = checkedItems.filter((item) =>
          data.deletedItemIds.includes(item.id)
        );

        onSharedItemsChange?.(
          (sharedItems || []).filter((item) => !data.deletedItemIds.includes(item.id))
        );

        const count = data.deletedItemIds.length;
        showToast({
          message: t('shoppingListView.clearedItems', { count }),
          onUndo: handleUndoClearChecked,
          duration: UNDO_DURATION,
        });
      } catch (err) {
        console.error('Failed to clear checked items:', err);
      } finally {
        setIsClearingChecked(false);
      }
      return;
    }

    if (!token) return;

    setIsClearingChecked(true);
    try {
      const response = await apiFetch(`/lists/${listId}/items/checked`, {
        method: 'DELETE',
      });

      if (!response.ok) throw new Error('Failed to clear checked items');

      const data: { deletedItemIds: string[] } = await response.json();

      // Store deleted items for potential undo
      deletedItemsRef.current = checkedItems.filter((item) =>
        data.deletedItemIds.includes(item.id)
      );

      // Remove items from Redux store
      dispatch(removeItems(data.deletedItemIds));

      // Show toast with undo option
      const count = data.deletedItemIds.length;
      showToast({
        message: t('shoppingListView.clearedItems', { count }),
        onUndo: handleUndoClearChecked,
        duration: UNDO_DURATION,
      });
    } catch (err) {
      console.error('Failed to clear checked items:', err);
    } finally {
      setIsClearingChecked(false);
    }
  };

  // Keyboard shortcuts
  useEffect(() => {
    // READ permission in shared mode: no keyboard shortcuts
    if (isShared && permission === 'READ') return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if typing in an input
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        e.target instanceof HTMLSelectElement
      ) {
        return;
      }

      // For CHECK permission, only allow space (toggle) and arrow navigation
      if (isShared && permission === 'CHECK') {
        switch (e.key) {
          case 'ArrowDown':
            e.preventDefault();
            setFocusedItemIndex((prev) => Math.min(prev + 1, allItems.length - 1));
            break;
          case 'ArrowUp':
            e.preventDefault();
            setFocusedItemIndex((prev) => Math.max(prev - 1, -1));
            break;
          case ' ':
            e.preventDefault();
            if (focusedItemIndex >= 0 && focusedItemIndex < allItems.length) {
              handleToggleCheck(allItems[focusedItemIndex]);
            }
            break;
        }
        return;
      }

      switch (e.key) {
        case '/':
        case 'n':
          e.preventDefault();
          inputRef.current?.focus();
          break;
        case 'ArrowDown':
          e.preventDefault();
          setFocusedItemIndex((prev) => Math.min(prev + 1, allItems.length - 1));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setFocusedItemIndex((prev) => Math.max(prev - 1, -1));
          break;
        case ' ':
          e.preventDefault();
          if (focusedItemIndex >= 0 && focusedItemIndex < allItems.length) {
            handleToggleCheck(allItems[focusedItemIndex]);
          }
          break;
        case 'Delete':
        case 'Backspace':
          if (focusedItemIndex >= 0 && focusedItemIndex < allItems.length) {
            e.preventDefault();
            handleRemoveItem(allItems[focusedItemIndex].id);
            setFocusedItemIndex((prev) => Math.min(prev, allItems.length - 2));
          }
          break;
        case '+':
        case '=':
          if (focusedItemIndex >= 0 && focusedItemIndex < allItems.length) {
            e.preventDefault();
            const item = allItems[focusedItemIndex];
            handleQuantityChange(item, item.quantity + 1);
          }
          break;
        case '-':
          if (focusedItemIndex >= 0 && focusedItemIndex < allItems.length) {
            e.preventDefault();
            const item = allItems[focusedItemIndex];
            if (item.quantity > 1) {
              handleQuantityChange(item, item.quantity - 1);
            }
          }
          break;
        case '?':
          e.preventDefault();
          showToast({
            message: t('shoppingListView.shortcutsHelp'),
            duration: 5000,
          });
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    isShared,
    permission,
    focusedItemIndex,
    allItems,
    showToast,
    handleToggleCheck,
    handleRemoveItem,
    handleQuantityChange,
    t,
  ]);

  // In authenticated mode, require list from Redux
  if (!isShared && !list) {
    return (
      <ErrorAlert message={t('shoppingListView.listNotFound')}>
        <Link to="/lists" className="mt-2 text-sm text-red-600 underline" onClick={onBack}>
          Go back
        </Link>
      </ErrorAlert>
    );
  }

  // Determine ListItemRow props based on permission
  const getItemRowProps = (item: ListItem) => {
    const base = {
      onToggleCheck: () => handleToggleCheck(item),
    };

    if (permission === 'READ') {
      return {
        ...base,
        disabled: true,
        onDelete: () => {},
        onQuantityChange: undefined,
      };
    }

    if (permission === 'CHECK' && isShared) {
      return {
        ...base,
        disabled: false,
        onDelete: () => {},
        onQuantityChange: undefined,
      };
    }

    // WRITE or authenticated mode
    return {
      ...base,
      disabled: false,
      onDelete: () => handleRemoveItem(item.id),
      onQuantityChange: (newQty: number) => handleQuantityChange(item, newQty),
    };
  };

  return (
    <div
      ref={containerRef}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      className="relative"
    >
      {/* Pull-to-refresh indicator (authenticated mode only) */}
      {!isShared && pullDistance > 0 && (
        <div
          className="absolute left-0 right-0 flex items-center justify-center text-gray-500 transition-transform"
          style={{ transform: `translateY(${pullDistance - 40}px)` }}
          data-testid="pull-refresh-indicator"
        >
          {isRefreshing ? (
            <div className="h-5 w-5 animate-spin rounded-full border-2 border-gray-300 border-t-indigo-600" />
          ) : pullDistance >= PULL_THRESHOLD ? (
            <span className="text-sm">{t('shoppingListView.releaseToRefresh')}</span>
          ) : (
            <span className="text-sm">{t('shoppingListView.pullToRefresh')}</span>
          )}
        </div>
      )}

      {!isShared && (
        <div className="mb-6">
          <Link
            to="/lists"
            onClick={onBack}
            className="flex items-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          >
            <span className="mr-1">&larr;</span> {t('common.back')}
          </Link>
        </div>
      )}

      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">{listName}</h2>
        <div className="flex items-center gap-2">
          {!isShared && onPinToggle && (
            <button
              onClick={onPinToggle}
              className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                list?.isPinned
                  ? 'bg-indigo-100 text-indigo-700 hover:bg-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-300'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-900 dark:text-gray-300 dark:hover:bg-gray-700'
              }`}
              aria-label={list?.isPinned ? t('common.unpin') : t('common.pin')}
              data-testid="pin-toggle"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="currentColor"
                className="h-5 w-5"
              >
                <path d="M14 4v5c0 1.12.37 2.16 1 3H9c.65-.86 1-1.9 1-3V4h4m3-2H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3V4h1c.55 0 1-.45 1-1s-.45-1-1-1z" />
              </svg>
            </button>
          )}
          {!isShared && list?.isOwner && onDeleteClick && (
            <button
              onClick={onDeleteClick}
              className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-red-500"
              aria-label={t('shoppingListView.deleteList')}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="currentColor"
                className="h-5 w-5"
              >
                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
              </svg>
            </button>
          )}
          {!isShared && list?.isOwner && onShareClick && (
            <button
              onClick={onShareClick}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
            >
              {t('common.share')}
            </button>
          )}
        </div>
      </div>

      {/* Add Item Form (WRITE permission only) */}
      {(!isShared || canWrite) && (
        <form onSubmit={handleAddItem} className="mb-6 flex gap-2">
          {!isShared ? (
            <AutocompleteInput
              value={itemName}
              onChange={setItemName}
              onSuggestionSelect={handleSuggestionSelect}
              placeholder={t('shoppingListView.itemName')}
              smartParsingEnabled={smartParsingEnabled}
            />
          ) : (
            <input
              ref={inputRef}
              type="text"
              value={itemName}
              onChange={(e) => setItemName(e.target.value)}
              placeholder={t('shoppingListView.itemName')}
              className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
            />
          )}
          <input
            type="number"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            placeholder={t('shoppingListView.qty')}
            min="1"
            className="w-20 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
          />
          <input
            type="text"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            placeholder={t('shoppingListView.unit')}
            className="w-24 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
          />
          <button
            type="submit"
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
          >
            {t('common.add')}
          </button>
        </form>
      )}

      {/* Unchecked Items */}
      {uncheckedItems.length > 0 && (
        <div className="mb-6 rounded-lg bg-white p-4 shadow dark:bg-gray-800 dark:shadow-gray-900/20">
          <ul className="divide-y divide-gray-200 dark:divide-gray-700">
            {uncheckedItems.map((item, index) => (
              <ListItemRow
                key={item.id}
                item={item}
                {...getItemRowProps(item)}
                isFocused={focusedItemIndex === index}
              />
            ))}
          </ul>
        </div>
      )}

      {/* Checked Items Section */}
      {checkedItems.length > 0 && (
        <div className="rounded-lg bg-gray-50 p-4 dark:bg-gray-800/50">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-medium text-gray-700 dark:text-gray-200">
              {t('shoppingListView.checkedItems')}
            </h3>
            {(!isShared || canWrite) && (
              <button
                onClick={handleClearChecked}
                disabled={isClearingChecked}
                className="text-sm font-medium text-red-600 hover:text-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                data-testid="clear-checked-button"
              >
                {isClearingChecked
                  ? t('shoppingListView.clearing')
                  : t('shoppingListView.clearChecked')}
              </button>
            )}
          </div>
          <ul className="divide-y divide-gray-200 dark:divide-gray-700">
            {checkedItems.map((item, index) => (
              <ListItemRow
                key={item.id}
                item={item}
                {...getItemRowProps(item)}
                isFocused={focusedItemIndex === uncheckedItems.length + index}
              />
            ))}
          </ul>
        </div>
      )}

      {items.length === 0 && (
        <EmptyState
          title={t('shoppingListView.noItemsYet')}
          description={t('shoppingListView.addFirstItem')}
        />
      )}

      {!isShared && (
        <div className="mt-6">
          <CommentFeed targetType="LIST" targetId={listId} />
        </div>
      )}
    </div>
  );
}
